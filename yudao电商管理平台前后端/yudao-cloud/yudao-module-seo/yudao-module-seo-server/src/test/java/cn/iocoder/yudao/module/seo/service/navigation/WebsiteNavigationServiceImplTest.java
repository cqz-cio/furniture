package cn.iocoder.yudao.module.seo.service.navigation;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.product.api.category.ProductCategoryApi;
import cn.iocoder.yudao.module.product.api.category.dto.ProductCategoryNavigationRespDTO;
import cn.iocoder.yudao.module.seo.controller.admin.navigation.vo.WebsiteNavigationPreviewTicketReqVO;
import cn.iocoder.yudao.module.seo.controller.app.navigation.vo.AppWebsiteNavigationRespVO;
import cn.iocoder.yudao.module.seo.dal.dataobject.config.SeoSiteConfigDO;
import cn.iocoder.yudao.module.seo.dal.dataobject.navigation.WebsiteNavigationItemDO;
import cn.iocoder.yudao.module.seo.dal.dataobject.navigation.WebsiteNavigationRevisionDO;
import cn.iocoder.yudao.module.seo.dal.mysql.navigation.WebsiteNavigationItemMapper;
import cn.iocoder.yudao.module.seo.dal.mysql.navigation.WebsiteNavigationRevisionMapper;
import cn.iocoder.yudao.module.seo.dal.redis.navigation.WebsiteNavigationPreviewGrant;
import cn.iocoder.yudao.module.seo.dal.redis.navigation.WebsiteNavigationPreviewRedisDAO;
import cn.iocoder.yudao.module.seo.service.config.SeoSiteConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebsiteNavigationServiceImplTest {

    private static final Long TENANT_ID = 162L;

    private WebsiteNavigationServiceImpl service;
    private WebsiteNavigationRevisionMapper revisionMapper;
    private WebsiteNavigationItemMapper itemMapper;
    private ProductCategoryApi productCategoryApi;
    private SeoSiteConfigService siteConfigService;
    private WebsiteNavigationPreviewRedisDAO previewRedisDAO;

    @BeforeEach
    void setUp() {
        revisionMapper = mock(WebsiteNavigationRevisionMapper.class);
        itemMapper = mock(WebsiteNavigationItemMapper.class);
        productCategoryApi = mock(ProductCategoryApi.class);
        siteConfigService = mock(SeoSiteConfigService.class);
        previewRedisDAO = mock(WebsiteNavigationPreviewRedisDAO.class);
        service = new WebsiteNavigationServiceImpl();
        ReflectionTestUtils.setField(service, "revisionMapper", revisionMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "productCategoryApi", productCategoryApi);
        ReflectionTestUtils.setField(service, "siteConfigService", siteConfigService);
        ReflectionTestUtils.setField(service, "previewRedisDAO", previewRedisDAO);

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
    void getPublished_shouldResolveLiveCategoryNameAndStableRoute() {
        WebsiteNavigationRevisionDO revision = revision(8L, "PUBLISHED", 3);
        when(revisionMapper.selectActive(1L, "en", "PUBLISHED")).thenReturn(revision);
        when(itemMapper.selectListByRevisionId(8L)).thenReturn(List.of(
                pageItem(8L, "PAGE_HOME", "HOME", "Home", 10),
                pageItem(8L, "PAGE_PRODUCTS", "PRODUCTS", "Collections", 20),
                categoryItem(8L, 25L, "Old category name", 10)));
        when(productCategoryApi.getNavigationCategoryList()).thenReturn(CommonResult.success(List.of(
                category(25L, "Dining Chairs", 8L))));

        AppWebsiteNavigationRespVO response = service.getPublished(1L, " en ");

        assertThat(response.getRevisionId()).isEqualTo(8L);
        assertThat(response.getItems()).extracting("label").containsExactly("Home", "Collections");
        assertThat(response.getItems().get(1).getChildren()).hasSize(1);
        assertThat(response.getItems().get(1).getChildren().get(0).getLabel()).isEqualTo("Dining Chairs");
        assertThat(response.getItems().get(1).getChildren().get(0).getHref())
                .isEqualTo("/products/category/25");
        assertThat(response.getItems().get(1).getChildren().get(0).getPublishedProductCount()).isEqualTo(8L);
    }

    @Test
    void getPublished_shouldHideCategoryThatProductCenterNoLongerExposes() {
        WebsiteNavigationRevisionDO revision = revision(9L, "PUBLISHED", 4);
        when(revisionMapper.selectActive(1L, "en", "PUBLISHED")).thenReturn(revision);
        when(itemMapper.selectListByRevisionId(9L)).thenReturn(List.of(
                pageItem(9L, "PAGE_PRODUCTS", "PRODUCTS", "Products", 10),
                categoryItem(9L, 99L, "Removed category", 10)));
        when(productCategoryApi.getNavigationCategoryList()).thenReturn(CommonResult.success(List.of()));

        AppWebsiteNavigationRespVO response = service.getPublished(1L, "en");

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getChildren()).isEmpty();
    }

    @Test
    void createPreviewTicket_shouldUseFragmentAndBindStoredGrant() {
        WebsiteNavigationRevisionDO draft = revision(10L, "DRAFT", 5);
        when(revisionMapper.selectByIdForTenant(10L)).thenReturn(draft);
        when(siteConfigService.getRequiredSiteConfig(1L)).thenReturn(new SeoSiteConfigDO()
                .setSiteId(1L)
                .setSiteUrl("https://www.vanz.example"));
        WebsiteNavigationPreviewTicketReqVO request = new WebsiteNavigationPreviewTicketReqVO();
        request.setRevisionId(10L);
        request.setVersion(5);

        var response = service.createPreviewTicket(request);

        assertThat(response.getPreviewUrl())
                .startsWith("https://www.vanz.example/preview/navigation#ticket=pv_")
                .doesNotContain("?token=");
        assertThat(response.getExpiresInSeconds()).isEqualTo(600);
        verify(previewRedisDAO).setTicket(anyString(), eq(new WebsiteNavigationPreviewGrant(
                TENANT_ID, 1L, "en", 10L, 5, "https://www.vanz.example")), eq(Duration.ofMinutes(10)));
    }

    @Test
    void exchangePreviewTicket_shouldConsumeTicketAndIssueReadOnlySession() {
        WebsiteNavigationPreviewGrant grant = new WebsiteNavigationPreviewGrant(
                TENANT_ID, 1L, "en", 11L, 6, "https://www.vanz.example");
        when(previewRedisDAO.consumeTicket("pv_once")).thenReturn(grant);
        when(revisionMapper.selectByIdForTenant(11L)).thenReturn(revision(11L, "DRAFT", 6));

        var response = service.exchangePreviewTicket("pv_once", "https://www.vanz.example");

        assertThat(response.getSession()).startsWith("ps_");
        assertThat(response.getExpiresInSeconds()).isEqualTo(1800);
        verify(previewRedisDAO).setSession(eq(response.getSession()), eq(grant), eq(Duration.ofMinutes(30)));
    }

    private static WebsiteNavigationRevisionDO revision(Long id, String status, Integer version) {
        return new WebsiteNavigationRevisionDO()
                .setId(id)
                .setSiteId(1L)
                .setLocale("en")
                .setRevisionNo(1)
                .setStatus(status)
                .setVersion(version);
    }

    private static WebsiteNavigationItemDO pageItem(Long revisionId, String itemKey, String pageKey,
                                                     String label, Integer sort) {
        return new WebsiteNavigationItemDO()
                .setRevisionId(revisionId)
                .setItemKey(itemKey)
                .setItemType("PAGE")
                .setPageKey(pageKey)
                .setLabel(label)
                .setSort(sort)
                .setVisible(true);
    }

    private static WebsiteNavigationItemDO categoryItem(Long revisionId, Long categoryId,
                                                         String label, Integer sort) {
        return new WebsiteNavigationItemDO()
                .setRevisionId(revisionId)
                .setItemKey("CATEGORY_" + categoryId)
                .setItemType("CATEGORY")
                .setCategoryId(categoryId)
                .setLabel(label)
                .setSort(sort)
                .setVisible(true);
    }

    private static ProductCategoryNavigationRespDTO category(Long id, String name, Long count) {
        ProductCategoryNavigationRespDTO category = new ProductCategoryNavigationRespDTO();
        category.setId(id);
        category.setParentId(1L);
        category.setName(name);
        category.setSort(10);
        category.setStatus(0);
        category.setPublishedProductCount(count);
        return category;
    }

}
