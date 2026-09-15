package cn.iocoder.yudao.module.seo.service.page;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.seo.service.media.*;
import cn.iocoder.yudao.module.seo.controller.admin.media.*;
import cn.iocoder.yudao.module.seo.controller.admin.media.vo.*;
import cn.iocoder.yudao.module.seo.controller.admin.page.vo.*;
import cn.iocoder.yudao.module.seo.dal.dataobject.config.SeoSiteConfigDO;
import cn.iocoder.yudao.module.seo.dal.mysql.config.SeoSiteConfigMapper;
import cn.iocoder.yudao.module.seo.dal.mysql.media.WebsiteMediaMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.mock.web.MockMultipartFile;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.*;

@Import({WebsiteMediaService.class, WebsiteMediaController.class, WebsitePageService.class,
    ValidationAutoConfiguration.class, WebsitePageServiceTest.Config.class})
@TestPropertySource(properties="yudao.website-media.public-base-url=https://erp.example")
class WebsiteMediaServiceTest extends BaseDbUnitTest {
    @Resource WebsiteMediaService service;
    @Resource WebsiteMediaController controller;
    @Resource WebsitePageService pages;
    @Resource SeoSiteConfigMapper sites;
    @Resource WebsiteMediaMapper mapper;
    @Resource WebsitePageServiceTest.Permissions permissions;
    @Resource com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor tenantLine;
    @MockBean FileApi files;
    @BeforeEach void setup() {
        permissions.allowed.clear();
        for (long id : new long[]{500,501}) {
            TenantContextHolder.setTenantId(id);
            cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.setLoginUser(
                new cn.iocoder.yudao.framework.security.core.LoginUser().setId(10L).setTenantId(id).setUserType(1),
                new org.springframework.mock.web.MockHttpServletRequest());
            sites.insert(new SeoSiteConfigDO().setSiteId(1L).setSiteName("Site").setSiteUrl("https://site.example").setNavigationTemplate("TRIPEER_CORPORATE"));
        }
        TenantContextHolder.setTenantId(500L);
        when(files.createFile(any(byte[].class), anyString(), anyString(), anyString())).thenAnswer(call ->
            "http://127.0.0.1:48080/admin-api/infra/file/1/get/" + call.getArgument(2) + "/20260915/" + call.getArgument(1));
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    private MockMultipartFile png() throws Exception {
        var image = new BufferedImage(20, 15, BufferedImage.TYPE_INT_RGB);
        var bytes = new ByteArrayOutputStream(); ImageIO.write(image, "png", bytes);
        return new MockMultipartFile("file", "公司背景.png", "text/plain", bytes.toByteArray());
    }
    private void error(Runnable action, int code) {
        assertThatThrownBy(action::run).isInstanceOf(ServiceException.class).extracting("code").isEqualTo(code);
    }
    @Test void uploadUsesRealTypeUniqueTenantPathAndPublicDomain() throws Exception {
        var one=service.upload(png(), "港口"); var two=service.upload(png(), "港口");
        assertThat(one.getMimeType()).isEqualTo("image/png");
        assertThat(one.getWidth()).isEqualTo(20); assertThat(one.getHeight()).isEqualTo(15);
        assertThat(one.getUrl()).startsWith("https://erp.example/admin-api/infra/file/1/get/website-media/500/");
        assertThat(one.getUrl()).isNotEqualTo(two.getUrl());
        assertThat(one.getTenantId()).isEqualTo(500L);
    }
    @Test void otherTenantCannotListEditArchiveOrReferenceAssets() throws Exception {
        var asset=service.upload(png(), "image");
        TenantContextHolder.setTenantId(501L);
        assertThat(service.page(new WebsiteMediaPageReqVO()).getTotal()).isZero();
        error(() -> service.get(asset.getId()), MEDIA_NOT_EXISTS.getCode());
        error(() -> service.archive(asset.getId(), true), MEDIA_NOT_EXISTS.getCode());
        error(() -> service.update(new WebsiteMediaUpdateReqVO().setId(asset.getId()).setName("changed").setAlt("")), MEDIA_NOT_EXISTS.getCode());
        var image=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("url", asset.getUrl()).put("alt", "x");
        error(() -> service.validateImage(image), MEDIA_REFERENCE_INVALID.getCode());
        TenantContextHolder.setTenantId(500L); assertThat(service.get(asset.getId()).getArchived()).isFalse();
    }
    @Test void archiveHidesSelectionAndRestorePreservesPublishedLink() throws Exception {
        var asset=service.upload(png(), "image"); String url=asset.getUrl();
        service.archive(asset.getId(), true);
        assertThat(service.page(new WebsiteMediaPageReqVO()).getTotal()).isZero();
        var request=new WebsiteMediaPageReqVO(); request.setArchived(true);
        assertThat(service.page(request).getTotal()).isEqualTo(1);
        assertThat(service.get(asset.getId()).getUrl()).isEqualTo(url);
        service.archive(asset.getId(), false);
        assertThat(service.page(new WebsiteMediaPageReqVO()).getTotal()).isEqualTo(1);
        verify(files,times(1)).createFile(any(byte[].class),anyString(),anyString(),anyString());
        verifyNoMoreInteractions(files);
    }
    @Test void permissionsAreEnforcedByController() throws Exception {
        var upload=png();
        assertThatThrownBy(() -> controller.page(new WebsiteMediaPageReqVO())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        permissions.allowed.add("seo:media:query");
        assertThat(controller.page(new WebsiteMediaPageReqVO()).getData().getTotal()).isZero();
        assertThatThrownBy(() -> controller.upload(upload, "")).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        permissions.allowed.add("seo:media:upload");
        var row=controller.upload(upload, "").getData();
        assertThatThrownBy(() -> controller.archive(row.getId(), true)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void malformedSpoofedSvgAndOversizedUploadsDoNotReachStorage() {
        for (var file : new MockMultipartFile[]{
            new MockMultipartFile("file","x.png","image/png","<script>x</script>".getBytes()),
            new MockMultipartFile("file","x.svg","image/svg+xml","<svg/>".getBytes()),
            new MockMultipartFile("file","x.pdf","application/pdf","%PDF-1.7 incomplete".getBytes()),
            new MockMultipartFile("file","x.png","image/png",new byte[WebsiteMediaContent.MAX_BYTES+1])})
            error(() -> service.upload(file, ""), MEDIA_FILE_INVALID.getCode());
        verifyNoInteractions(files);
    }
    @Test void missingSiteFailsBeforeFileWrite() throws Exception {
        TenantContextHolder.setTenantId(999L); var file=png();
        error(() -> service.upload(file, ""), PAGE_SITE_UNAVAILABLE.getCode()); verifyNoInteractions(files);
    }
    @Test void pdfIsDownloadableButCannotBeUsedAsHomepageImage() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var document = new org.apache.pdfbox.pdmodel.PDDocument()) {
            document.addPage(new org.apache.pdfbox.pdmodel.PDPage()); document.save(bytes);
        }
        var file = new MockMultipartFile("file", "company.pdf", "application/pdf", bytes.toByteArray());
        var asset = service.upload(file, "");
        assertThat(asset.getKind()).isEqualTo("document");
        assertThat(asset.getMimeType()).isEqualTo("application/pdf");
        var image = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("url", asset.getUrl()).put("alt", "x");
        error(() -> service.validateImage(image), MEDIA_REFERENCE_INVALID.getCode());
        error(() -> service.upload(new MockMultipartFile("file", "fake.pdf", "application/pdf", "%PDF-1.7\nnot a PDF\n%%EOF".getBytes()), ""), MEDIA_FILE_INVALID.getCode());
    }
    @Test void publicMetadataCanBeEditedAndFiltered() throws Exception {
        var asset=service.upload(png(), "before");
        service.update(new WebsiteMediaUpdateReqVO().setId(asset.getId()).setName("新版背景").setAlt("港口图片"));
        var request=new WebsiteMediaPageReqVO(); request.setName("新版"); request.setKind("image");
        assertThat(service.page(request).getList()).singleElement().satisfies(row -> {
            assertThat(row.getName()).isEqualTo("新版背景"); assertThat(row.getAlt()).isEqualTo("港口图片");
            assertThat(row.getUrl()).isEqualTo(asset.getUrl());
        });
    }
    @Test void selectedImagePublishesWithoutChangingSchemaAndArchivedImageStillRenders() throws Exception {
        var asset=service.upload(png(), "港口");
        var key=new WebsitePageKeyReqVO().setSiteId(1L).setPageKey("home").setLocale("zh-CN");
        var initialized=pages.initialize(key);
        var content=initialized.getContent().deepCopy();
        ((ObjectNode) content.path("modules").path("hero").path("image")).put("url",asset.getUrl());
        var save=new WebsitePageSaveReqVO();save.setSiteId(1L);save.setPageKey("home");save.setLocale("zh-CN");save.setExpectedVersion(1);save.setContent(content);
        pages.saveDraft(save);
        var publish=new WebsitePageVersionReqVO();publish.setSiteId(1L);publish.setPageKey("home");publish.setLocale("zh-CN");publish.setExpectedVersion(2);
        pages.publish(publish); service.archive(asset.getId(),true);
        assertThat(pages.getPublished(key).getContent().path("modules").path("hero").path("image").path("url").asText()).isEqualTo(asset.getUrl());
        TenantContextHolder.setTenantId(501L); pages.initialize(key);save.setExpectedVersion(1);
        error(() -> pages.saveDraft(save),MEDIA_REFERENCE_INVALID.getCode());
    }
}
