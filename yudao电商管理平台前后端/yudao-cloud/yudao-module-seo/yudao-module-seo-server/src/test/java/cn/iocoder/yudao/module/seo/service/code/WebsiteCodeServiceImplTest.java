package cn.iocoder.yudao.module.seo.service.code;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.seo.controller.admin.code.vo.*;
import cn.iocoder.yudao.module.seo.dal.dataobject.config.SeoSiteConfigDO;
import cn.iocoder.yudao.module.seo.dal.mysql.config.SeoSiteConfigMapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import jakarta.annotation.Resource;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.*;
import static org.assertj.core.api.Assertions.*;

@Import({WebsiteCodeServiceImpl.class, ValidationAutoConfiguration.class,
        WebsiteCodeServiceImplTest.TenantConfiguration.class})
class WebsiteCodeServiceImplTest extends BaseDbUnitTest {
    @Resource private WebsiteCodeService service;
    @Resource private TenantLineInnerInterceptor tenantLine;
    @Resource private SeoSiteConfigMapper siteMapper;

    @BeforeEach
    void prepare() {
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(100L).setTenantId(162L).setUserType(1),
                new MockHttpServletRequest());
        TenantContextHolder.setTenantId(162L);
        site(1L); site(2L);
        TenantContextHolder.setTenantId(121L);
        site(1L);
        TenantContextHolder.setTenantId(162L);
    }
    @AfterEach void clearTenant() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }
    private void site(Long id) {
        SeoSiteConfigDO site = new SeoSiteConfigDO().setSiteId(id).setSiteName("Site " + id)
                .setSiteUrl("https://site.example.com");
        siteMapper.insert(site);
    }
    private WebsiteCodeSaveReqVO draft(Long site, int version, String code) {
        WebsiteCodeSaveReqVO request = new WebsiteCodeSaveReqVO();
        request.setSiteId(site); request.setVersion(version);
        request.setContent(new WebsiteCodeContent());
        request.getContent().getHeader().setEnabled(true).setCode(code);
        return request;
    }
    private WebsiteCodeVersionReqVO version(Long site, int version) {
        return new WebsiteCodeVersionReqVO().setSiteId(site).setVersion(version);
    }
    @Test void readsAreEmptyAndDoNotCreateHistory() {
        assertThat(service.getDraft(1L).getVersion()).isZero();
        assertThat(service.getHistory(1L)).isEmpty();
        assertThat(service.getPublished(1L).getContent().getHeader().getCode()).isEmpty();
    }
    @Test void draftsStayPrivateAndPublishIsASnapshot() {
        service.saveDraft(draft(1L, 0, "<script>window.tag = 'A';</script>"));
        assertThat(service.getPublished(1L).getContent().getHeader().getCode()).isEmpty();
        WebsiteCodeRespVO published = service.publish(version(1L, 1));
        assertThat(published.getVersion()).isEqualTo(2);
        assertThat(published.getPublishedVersion()).isEqualTo(2);
        service.saveDraft(draft(1L, 2, "<script>window.tag = 'B';</script>"));
        assertThat(service.getPublished(1L).getContent().getHeader().getCode()).contains("'A'");
        assertThat(service.getPublished(1L).getVersion()).isEqualTo(2);
        assertThat(service.getDraft(1L).getContent().getHeader().getCode()).contains("'B'");
        assertThat(service.getHistory(1L)).extracting(WebsiteCodeHistoryRespVO::getAction).containsExactly("SAVE", "PUBLISH", "SAVE");
    }
    @Test void tenantsAndSitesAreIsolatedIncludingRestore() {
        service.saveDraft(draft(1L, 0, "tenant162/site1"));
        service.publish(version(1L, 1));
        Long historyId = service.getHistory(1L).get(0).getId();
        assertThat(service.getDraft(2L).getVersion()).isZero();
        WebsiteCodeRestoreReqVO restore = new WebsiteCodeRestoreReqVO();
        restore.setSiteId(2L); restore.setVersion(0); restore.setHistoryId(historyId);
        assertThatThrownBy(() -> service.restoreDraft(restore)).isInstanceOf(ServiceException.class)
                .extracting("code").isEqualTo(WEBSITE_CODE_HISTORY_NOT_EXISTS.getCode());
        TenantContextHolder.setTenantId(121L);
        assertThat(service.getHistory(1L)).isEmpty();
        assertThat(service.getPublished(1L).getContent().getHeader().getCode()).isEmpty();
        restore.setSiteId(1L);
        assertThatThrownBy(() -> service.restoreDraft(restore)).isInstanceOf(ServiceException.class);
        service.saveDraft(draft(1L, 0, "tenant121/site1"));
        service.publish(version(1L, 1));
        TenantContextHolder.setTenantId(162L);
        assertThat(service.getPublished(1L).getContent().getHeader().getCode()).isEqualTo("tenant162/site1");
    }
    @Test void restoreOnlyUpdatesDraftAndCanBePublishedSeparately() {
        service.saveDraft(draft(1L, 0, "first"));
        Long first = service.getHistory(1L).get(0).getId();
        service.saveDraft(draft(1L, 1, "second"));
        service.publish(version(1L, 2));
        WebsiteCodeRestoreReqVO restore = new WebsiteCodeRestoreReqVO();
        restore.setSiteId(1L); restore.setVersion(3); restore.setHistoryId(first);
        WebsiteCodeRespVO restored = service.restoreDraft(restore);
        assertThat(restored.getContent().getHeader().getCode()).isEqualTo("first");
        assertThat(restored.getPublishedVersion()).isEqualTo(3);
        assertThat(service.getPublished(1L).getContent().getHeader().getCode()).isEqualTo("second");
        service.publish(version(1L, 4));
        assertThat(service.getPublished(1L).getContent().getHeader().getCode()).isEqualTo("first");
    }
    @Test void staleSavesAndPublishesCannotOverwriteTheLatestVersion() {
        service.saveDraft(draft(1L, 0, "first"));
        assertThatThrownBy(() -> service.saveDraft(draft(1L, 0, "stale")))
                .isInstanceOf(ServiceException.class).extracting("code").isEqualTo(WEBSITE_CODE_VERSION_CONFLICT.getCode());
        assertThatThrownBy(() -> service.publish(version(1L, 0))).isInstanceOf(ServiceException.class);
        assertThat(service.getDraft(1L).getContent().getHeader().getCode()).isEqualTo("first");
        assertThat(service.getHistory(1L)).hasSize(1);
    }
    @Test void disabledCodeIsRetainedPrivatelyButNotServed() {
        WebsiteCodeSaveReqVO request = draft(1L, 0, "secret-draft-tag");
        request.getContent().getHeader().setEnabled(false);
        service.saveDraft(request);
        service.publish(version(1L, 1));
        assertThat(service.getPublished(1L).getContent().getHeader().getCode()).isEmpty();
        assertThat(service.getDraft(1L).getContent().getHeader().getCode()).isEqualTo("secret-draft-tag");
    }
    @Test void missingSiteAndInvalidPayloadsAreRejected() {
        assertThatThrownBy(() -> service.saveDraft(draft(99L, 0, ""))).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.saveDraft(draft(1L, 0, "x".repeat(100001)))).isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> service.publish(version(1L, 0))).isInstanceOf(ServiceException.class)
                .extracting("code").isEqualTo(WEBSITE_CODE_DRAFT_REQUIRED.getCode());
        assertThat(service.getHistory(1L)).isEmpty();
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TenantProperties.class)
    static class TenantConfiguration {
        // The full application already has an infra bean named configMapper.
        // Keep an incompatible bean with that name to catch @Resource's
        // name-first resolution, which a SEO-only test context used to miss.
        @Bean(name = "configMapper") Object unrelatedInfraConfigMapper() {
            return new Object();
        }

        @Bean TenantLineInnerInterceptor tenantLine(TenantProperties properties, MybatisPlusInterceptor interceptor) {
            TenantLineInnerInterceptor inner = new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(properties));
            MyBatisUtils.addInterceptor(interceptor, inner, 0);
            return inner;
        }
    }
}
