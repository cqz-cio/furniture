package cn.iocoder.yudao.module.seo.service.blog;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.seo.controller.admin.blog.vo.WebsiteBlogArticleSaveReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.blog.vo.WebsiteBlogSectionRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.blog.vo.WebsiteBlogSectionSaveReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.blog.vo.WebsiteBlogVersionReqVO;
import cn.iocoder.yudao.module.seo.controller.app.blog.vo.AppWebsiteBlogArticleRespVO;
import cn.iocoder.yudao.module.seo.dal.dataobject.blog.WebsiteBlogArticleDO;
import cn.iocoder.yudao.module.seo.dal.dataobject.blog.WebsiteBlogPublishRecordDO;
import cn.iocoder.yudao.module.seo.dal.dataobject.config.SeoSiteConfigDO;
import cn.iocoder.yudao.module.seo.dal.mysql.blog.WebsiteBlogArticleMapper;
import cn.iocoder.yudao.module.seo.dal.mysql.blog.WebsiteBlogPublishRecordMapper;
import cn.iocoder.yudao.module.seo.dal.redis.blog.WebsiteBlogPreviewGrant;
import cn.iocoder.yudao.module.seo.dal.redis.blog.WebsiteBlogPreviewRedisDAO;
import cn.iocoder.yudao.module.seo.service.config.SeoSiteConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebsiteBlogServiceImplTest {

    private static final Long TENANT_ID = 162L;

    private WebsiteBlogServiceImpl service;
    private WebsiteBlogArticleMapper articleMapper;
    private WebsiteBlogPublishRecordMapper publishRecordMapper;
    private WebsiteBlogPreviewRedisDAO previewRedisDAO;
    private SeoSiteConfigService siteConfigService;

    @BeforeEach
    void setUp() {
        articleMapper = mock(WebsiteBlogArticleMapper.class);
        publishRecordMapper = mock(WebsiteBlogPublishRecordMapper.class);
        previewRedisDAO = mock(WebsiteBlogPreviewRedisDAO.class);
        siteConfigService = mock(SeoSiteConfigService.class);
        service = new WebsiteBlogServiceImpl();
        ReflectionTestUtils.setField(service, "websiteBlogArticleMapper", articleMapper);
        ReflectionTestUtils.setField(service, "publishRecordMapper", publishRecordMapper);
        ReflectionTestUtils.setField(service, "previewRedisDAO", previewRedisDAO);
        ReflectionTestUtils.setField(service, "siteConfigService", siteConfigService);

        TenantContextHolder.setTenantId(TENANT_ID);
        LoginUser loginUser = new LoginUser().setId(100L).setTenantId(TENANT_ID).setUserType(1);
        SecurityFrameworkUtils.setLoginUser(loginUser, new MockHttpServletRequest());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void createArticle_shouldPersistStructuredDraftWithTenant() {
        WebsiteBlogArticleSaveReqVO request = saveRequest();
        when(siteConfigService.getRequiredSiteConfig(1L)).thenReturn(new SeoSiteConfigDO().setSiteId(1L));
        doAnswer(invocation -> {
            WebsiteBlogArticleDO article = invocation.getArgument(0);
            article.setId(88L);
            return 1;
        }).when(articleMapper).insert(any(WebsiteBlogArticleDO.class));

        Long id = service.createArticle(request);

        assertThat(id).isEqualTo(88L);
        ArgumentCaptor<WebsiteBlogArticleDO> captor = ArgumentCaptor.forClass(WebsiteBlogArticleDO.class);
        verify(articleMapper).insert(captor.capture());
        WebsiteBlogArticleDO inserted = captor.getValue();
        assertThat(inserted.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(inserted.getStatus()).isEqualTo("DRAFT");
        assertThat(inserted.getVersion()).isEqualTo(1);
        assertThat(JsonUtils.parseArray(inserted.getTitleLinesJson(), String.class))
                .containsExactly("5 Quick Steps", "Bedroom Space");
        assertThat(JsonUtils.parseArray(inserted.getSectionsJson(), WebsiteBlogSectionRespVO.class))
                .singleElement()
                .satisfies(section -> {
                    assertThat(section.getNumber()).isEqualTo("01");
                    assertThat(section.getParagraphs()).containsExactly("Plan the room first.");
                });
    }

    @Test
    void publishArticle_shouldFreezePublicSnapshotAndWriteHistory() {
        WebsiteBlogArticleDO article = article(9L, 4);
        when(articleMapper.selectByIdForTenant(9L)).thenReturn(article);
        when(articleMapper.publishAtomic(eq(9L), eq(4), any(LocalDateTime.class),
                eq(article.getSlug()), anyString(), eq(TENANT_ID), eq("100"))).thenReturn(1);
        WebsiteBlogVersionReqVO request = new WebsiteBlogVersionReqVO();
        request.setId(9L);
        request.setVersion(4);

        service.publishArticle(request);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(articleMapper).publishAtomic(eq(9L), eq(4), any(LocalDateTime.class),
                eq(article.getSlug()), payloadCaptor.capture(), eq(TENANT_ID), eq("100"));
        AppWebsiteBlogArticleRespVO payload = JsonUtils.parseObject(
                payloadCaptor.getValue(), AppWebsiteBlogArticleRespVO.class);
        assertThat(payload.getPath()).isEqualTo("/5-quick-steps-to-double-your-bedroom-space/");
        assertThat(payload.getCoverImage().getUrl()).isEqualTo("/assets/bedroom.webp");
        assertThat(payload.getPublishedAt()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*");
        assertThat(payload.getSections()).singleElement()
                .extracting("number", "title")
                .containsExactly("01", "Plan before buying");

        ArgumentCaptor<WebsiteBlogPublishRecordDO> recordCaptor =
                ArgumentCaptor.forClass(WebsiteBlogPublishRecordDO.class);
        verify(publishRecordMapper).insert(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getPublishedVersion()).isEqualTo(5);
        assertThat(recordCaptor.getValue().getTenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    void getPublishedPage_shouldOnlySerializeStoredSnapshot() {
        WebsiteBlogArticleDO article = article(12L, 6);
        AppWebsiteBlogArticleRespVO snapshot = new AppWebsiteBlogArticleRespVO();
        snapshot.setId(12L);
        snapshot.setSlug("published-slug");
        snapshot.setTitle("Published title");
        article.setPublishedPayloadJson(JsonUtils.toJsonString(snapshot));
        article.setTitle("Unpublished edited title");
        when(articleMapper.selectPublishedPage(eq(1L), eq("en"), any(PageParam.class),
                any(LocalDateTime.class))).thenReturn(new PageResult<>(List.of(article), 1L));

        var response = service.getPublishedPage(1L, " EN ", 1, 24);

        assertThat(response.getItems()).singleElement()
                .extracting("title", "slug")
                .containsExactly("Published title", "published-slug");
        assertThat(response.getTotal()).isEqualTo(1L);
    }

    @Test
    void createPreviewTicket_shouldBindArticleVersionAndUseFragment() {
        WebsiteBlogArticleDO article = article(15L, 7);
        when(articleMapper.selectByIdForTenant(15L)).thenReturn(article);
        when(siteConfigService.getRequiredSiteConfig(1L)).thenReturn(new SeoSiteConfigDO()
                .setSiteId(1L)
                .setSiteUrl("https://www.vanz.example"));
        WebsiteBlogVersionReqVO request = new WebsiteBlogVersionReqVO();
        request.setId(15L);
        request.setVersion(7);

        var response = service.createPreviewTicket(request);

        assertThat(response.getPreviewUrl())
                .startsWith("https://www.vanz.example/preview/blog#ticket=bpv_")
                .endsWith("&tenantId=162");
        assertThat(response.getExpiresInSeconds()).isEqualTo(600);
        verify(previewRedisDAO).setTicket(anyString(), eq(new WebsiteBlogPreviewGrant(
                TENANT_ID, 1L, "en", 15L, 7, "https://www.vanz.example")),
                eq(Duration.ofMinutes(10)));
    }

    @Test
    void publishArticle_shouldRejectEmptyBody() {
        WebsiteBlogArticleDO article = article(18L, 2).setSectionsJson("[]");
        when(articleMapper.selectByIdForTenant(18L)).thenReturn(article);
        WebsiteBlogVersionReqVO request = new WebsiteBlogVersionReqVO();
        request.setId(18L);
        request.setVersion(2);

        assertThatThrownBy(() -> service.publishArticle(request))
                .hasMessageContaining("企业日志");
    }

    private static WebsiteBlogArticleSaveReqVO saveRequest() {
        WebsiteBlogSectionSaveReqVO section = new WebsiteBlogSectionSaveReqVO();
        section.setId("plan-before-buying");
        section.setTitle("Plan before buying");
        section.setParagraphs(List.of("Plan the room first."));
        WebsiteBlogArticleSaveReqVO request = new WebsiteBlogArticleSaveReqVO();
        request.setSiteId(1L);
        request.setLocale("en");
        request.setSlug("5-quick-steps-to-double-your-bedroom-space");
        request.setLegacyPath("/5-quick-steps-to-double-your-bedroom-space/");
        request.setTitle("5 Quick Steps to Double Your Bedroom Space");
        request.setTitleLines(List.of("5 Quick Steps", "Bedroom Space"));
        request.setCategory("Bedroom planning");
        request.setLabel("Small-space guide");
        request.setSummary("A practical guide to a more open bedroom.");
        request.setCoverImageUrl("/assets/bedroom.webp");
        request.setCoverImageAlt("A warm bedroom");
        request.setHeroImageUrl("");
        request.setSections(List.of(section));
        request.setVisible(true);
        request.setSortOrder(100);
        request.setSeoTitle("");
        request.setSeoDescription("");
        return request;
    }

    private static WebsiteBlogArticleDO article(Long id, Integer version) {
        WebsiteBlogSectionRespVO section = new WebsiteBlogSectionRespVO();
        section.setId("plan-before-buying");
        section.setNumber("01");
        section.setTitle("Plan before buying");
        section.setParagraphs(List.of("Plan the room first."));
        return new WebsiteBlogArticleDO()
                .setId(id)
                .setSiteId(1L)
                .setLocale("en")
                .setSlug("5-quick-steps-to-double-your-bedroom-space")
                .setLegacyPath("/5-quick-steps-to-double-your-bedroom-space/")
                .setTitle("5 Quick Steps to Double Your Bedroom Space")
                .setTitleLinesJson(JsonUtils.toJsonString(List.of("5 Quick Steps", "Bedroom Space")))
                .setCategory("Bedroom planning")
                .setLabel("Small-space guide")
                .setSummary("A practical guide to a more open bedroom.")
                .setCoverImageUrl("/assets/bedroom.webp")
                .setCoverImageAlt("A warm bedroom")
                .setHeroImageUrl("")
                .setSectionsJson(JsonUtils.toJsonString(List.of(section)))
                .setStatus("DRAFT")
                .setVisible(true)
                .setSortOrder(100)
                .setSeoTitle("Bedroom guide — VANZ Journal")
                .setSeoDescription("A practical guide to a more open bedroom.")
                .setVersion(version);
    }

}
