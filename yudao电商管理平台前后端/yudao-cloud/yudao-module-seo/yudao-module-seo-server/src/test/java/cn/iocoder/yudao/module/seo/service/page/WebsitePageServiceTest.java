package cn.iocoder.yudao.module.seo.service.page;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.seo.controller.admin.page.vo.*;
import cn.iocoder.yudao.module.seo.dal.dataobject.config.SeoSiteConfigDO;
import cn.iocoder.yudao.module.seo.dal.mysql.config.SeoSiteConfigMapper;
import cn.iocoder.yudao.module.seo.dal.redis.page.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Import({WebsitePageService.class, ValidationAutoConfiguration.class, WebsitePageServiceTest.Config.class,
        cn.iocoder.yudao.module.seo.controller.admin.page.WebsitePageController.class})
class WebsitePageServiceTest extends BaseDbUnitTest {
    @org.springframework.boot.test.mock.mockito.MockBean private cn.iocoder.yudao.module.seo.service.media.WebsiteMediaService mediaService;
    @Resource private WebsitePageService service;
    @Resource private SeoSiteConfigMapper siteMapper;
    @Resource private com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor tenantLine;
    @Resource private WebsitePagePreviewRedisDAO previewDAO;
    @Resource private cn.iocoder.yudao.module.seo.controller.admin.page.WebsitePageController controller;
    @Resource private Permissions permissions;
    private final Map<String, WebsitePagePreviewGrant> tickets = new HashMap<>();
    private final Map<String, WebsitePagePreviewGrant> sessions = new HashMap<>();

    @TestConfiguration(proxyBeanMethods = false)
    @org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
    static class Config {
        @Bean(name = "ss") Permissions permissions() { return new Permissions(); }
        @Bean org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate() {
            return mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        }
        @Bean WebsitePagePreviewRedisDAO pagePreviewDAO() { return mock(WebsitePagePreviewRedisDAO.class); }
        @Bean cn.iocoder.yudao.framework.tenant.config.TenantProperties tenantProperties() {
            return new cn.iocoder.yudao.framework.tenant.config.TenantProperties();
        }
        @Bean com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor tenantLine(
                cn.iocoder.yudao.framework.tenant.config.TenantProperties properties,
                com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor interceptor) {
            var inner = new com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor(
                    new cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor(properties));
            cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils.addInterceptor(interceptor, inner, 0);
            return inner;
        }
    }

    @BeforeEach void setup() {
        permissions.allowed.clear();
        reset(previewDAO); tickets.clear(); sessions.clear();
        for (long tenant : List.of(500L, 501L)) {
            TenantContextHolder.setTenantId(tenant);
            SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(10L).setTenantId(tenant).setUserType(1),
                    new MockHttpServletRequest());
            var site = new SeoSiteConfigDO().setSiteId(1L).setSiteName("TRIPEER")
                    .setSiteUrl("https://site.example").setPreviewBaseUrl("http://localhost:5173")
                    .setNavigationTemplate("TRIPEER_CORPORATE");
            siteMapper.insert(site);
        }
        TenantContextHolder.setTenantId(500L);
        doAnswer(call -> { tickets.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(previewDAO).setTicket(anyString(), any());
        when(previewDAO.consumeTicket(anyString())).thenAnswer(call -> tickets.remove(call.getArgument(0)));
        doAnswer(call -> { sessions.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(previewDAO).setSession(anyString(), any());
        when(previewDAO.getSession(anyString())).thenAnswer(call -> sessions.get(call.getArgument(0)));
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }

    private WebsitePageKeyReqVO key(String locale) { return new WebsitePageKeyReqVO().setSiteId(1L).setPageKey("home").setLocale(locale); }
    private WebsitePageVersionReqVO version(int version) {
        var result = new WebsitePageVersionReqVO(); result.setSiteId(1L); result.setPageKey("home");
        result.setLocale("zh-CN"); result.setExpectedVersion(version); return result;
    }
    private WebsitePageSaveReqVO changed(int version, String title) {
        var result = new WebsitePageSaveReqVO(); result.setSiteId(1L); result.setPageKey("home");
        result.setLocale("zh-CN"); result.setExpectedVersion(version);
        var content = service.getDraft(key("zh-CN")).getContent().deepCopy();
        ((ObjectNode) content.path("modules").path("hero")).put("title", title);
        result.setContent(content); return result;
    }
    private String ticket() {
        String url = (String) service.createPreviewTicket(version(1)).get("previewUrl");
        return url.substring(url.indexOf("ticket=") + 7);
    }
    private void error(Runnable action, int code) {
        assertThatThrownBy(action::run).isInstanceOf(ServiceException.class).extracting("code").isEqualTo(code);
    }

    public static class Permissions {
        final Set<String> allowed = new HashSet<>();
        public boolean hasPermission(String value) { return allowed.contains(value); }
    }
    @Test void editingPermissionDoesNotGrantPublishOrPreviewAccess() {
        permissions.allowed.add("seo:page:update");
        controller.initialize(key("zh-CN"));
        assertThatThrownBy(() -> controller.publish(version(1)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> controller.preview(version(1)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        permissions.allowed.add("seo:page:publish");
        assertThat(controller.publish(version(1)).getData().getPublishedVersion()).isEqualTo(1);
    }
    @Test void readsDoNotInitializeAndInitializationNeverOverwritesEdits() {
        error(() -> service.getDraft(key("zh-CN")), PAGE_NOT_INITIALIZED.getCode());
        service.initialize(key("zh-CN"));
        service.saveDraft(changed(1, "edited"));
        assertThat(service.initialize(key("zh-CN")).getContent().path("modules").path("hero").path("title").asText()).isEqualTo("edited");
        assertThat(service.getDraft(key("zh-CN")).getVersion()).isEqualTo(2);
    }
    @Test void draftsStayPrivateAndPublishedSnapshotsDoNotFollowLaterEdits() {
        service.initialize(key("zh-CN"));
        error(() -> service.getPublished(key("zh-CN")), PAGE_NOT_PUBLISHED.getCode());
        String initial = service.publish(version(1)).getContent().toString();
        service.saveDraft(changed(1, "next draft"));
        assertThat(service.getPublished(key("zh-CN")).getContent().toString()).isEqualTo(initial);
        service.publish(version(2));
        assertThat(service.getPublished(key("zh-CN")).getContent().path("modules").path("hero").path("title").asText()).isEqualTo("next draft");
        assertThat(service.getHistory(key("zh-CN"))).hasSize(2);
        service.publish(version(2));
        assertThat(service.getHistory(key("zh-CN"))).hasSize(2);
    }
    @Test void staleSaveAndPublishAreRejected() {
        service.initialize(key("zh-CN"));
        service.saveDraft(changed(1, "latest"));
        error(() -> service.saveDraft(changed(1, "stale")), PAGE_VERSION_CONFLICT.getCode());
        error(() -> service.publish(version(1)), PAGE_VERSION_CONFLICT.getCode());
        assertThat(service.getDraft(key("zh-CN")).getVersion()).isEqualTo(2);
    }
    @Test void concurrentEditorsCannotBothSaveTheSameVersion() throws Exception {
        service.initialize(key("zh-CN"));
        var first = changed(1, "first");
        var second = changed(1, "second");
        var start = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<Boolean>>();
            for (var request : List.of(first, second)) futures.add(executor.submit(() -> {
                TenantContextHolder.setTenantId(500L);
                try {
                    if (!start.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                    service.saveDraft(request);
                    return true;
                } catch (ServiceException error) {
                    assertThat(error.getCode()).isEqualTo(PAGE_VERSION_CONFLICT.getCode());
                    return false;
                } finally { TenantContextHolder.clear(); }
            }));
            start.countDown();
            int successes = 0;
            for (var future : futures) if (future.get(10, java.util.concurrent.TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
            assertThat(service.getDraft(key("zh-CN")).getVersion()).isEqualTo(2);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        }
    }
    @Test void tenantsAndLanguagesAreIsolated() {
        service.initialize(key("zh-CN")); service.publish(version(1));
        error(() -> service.getDraft(key("en")), PAGE_NOT_INITIALIZED.getCode());
        service.initialize(key("en"));
        assertThat(service.getDraft(key("en")).getContent().toString()).doesNotContain("连接可靠供应");
        TenantContextHolder.setTenantId(501L);
        error(() -> service.getPublished(key("zh-CN")), PAGE_NOT_INITIALIZED.getCode());
        service.initialize(key("zh-CN"));
        error(() -> service.getPublished(key("zh-CN")), PAGE_NOT_PUBLISHED.getCode());
        TenantContextHolder.setTenantId(500L);
        assertThat(service.getHistory(key("zh-CN"))).hasSize(1);
    }
    @Test void unsupportedSitesCannotAccessPageContent() {
        var config = siteMapper.selectBySiteId(1L);
        config.setNavigationTemplate("VANZ_B2B"); siteMapper.updateById(config);
        error(() -> service.initialize(key("zh-CN")), PAGE_SITE_UNAVAILABLE.getCode());
    }
    @Test void unknownFieldsUnsafeImagesAndEmptyTitlesAreRejected() {
        service.initialize(key("zh-CN"));
        var request = changed(1, "");
        error(() -> service.saveDraft(request), PAGE_CONTENT_INVALID.getCode());
        request.setContent(changed(1, "title").getContent());
        ((ObjectNode) request.getContent().path("modules").path("hero")).put("script", "alert(1)");
        error(() -> service.saveDraft(request), PAGE_CONTENT_INVALID.getCode());
        for (String url : List.of("javascript:alert(1)", "//evil.example/image", "/assets/../private", "https://site.example/img?token=secret")) {
            request.setContent(changed(1, "title").getContent());
            ((ObjectNode) request.getContent().path("modules").path("hero").path("image")).put("url", url);
            error(() -> service.saveDraft(request), PAGE_CONTENT_INVALID.getCode());
        }
        assertThat(service.getDraft(key("zh-CN")).getVersion()).isEqualTo(1);
    }
    @Test void previewTicketIsSingleUseAndSnapshotSurvivesSubsequentEdits() {
        service.initialize(key("zh-CN"));
        String before = service.getDraft(key("zh-CN")).getContent().toString();
        String ticket = ticket();
        var grant = tickets.get(ticket);
        String serialized = cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(grant);
        tickets.put(ticket, cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(serialized, WebsitePagePreviewGrant.class));
        service.saveDraft(changed(1, "later"));
        String session = (String) service.exchangePreviewTicket(ticket, "http://localhost:5173").get("previewSession");
        error(() -> service.exchangePreviewTicket(ticket, "http://localhost:5173"), PAGE_PREVIEW_INVALID.getCode());
        assertThat(service.getPreview(session, "http://localhost:5173/").getContent().toString()).isEqualTo(before);
        error(() -> service.getPreview(session, "https://evil.example"), PAGE_PREVIEW_INVALID.getCode());
        TenantContextHolder.setTenantId(501L);
        error(() -> service.getPreview(session, "http://localhost:5173"), PAGE_PREVIEW_INVALID.getCode());
        TenantContextHolder.setTenantId(500L);
        sessions.clear();
        error(() -> service.getPreview(session, "http://localhost:5173"), PAGE_PREVIEW_INVALID.getCode());
    }
    @Test void previewNeedsConfiguredOriginAndRejectsMalformedCredentials() {
        service.initialize(key("zh-CN"));
        error(() -> service.exchangePreviewTicket("anything", null), PAGE_PREVIEW_INVALID.getCode());
        error(() -> service.exchangePreviewTicket(ticket(), null), PAGE_PREVIEW_INVALID.getCode());
        error(() -> service.getPreview("anything", "http://localhost:5173"), PAGE_PREVIEW_INVALID.getCode());
    }
}
