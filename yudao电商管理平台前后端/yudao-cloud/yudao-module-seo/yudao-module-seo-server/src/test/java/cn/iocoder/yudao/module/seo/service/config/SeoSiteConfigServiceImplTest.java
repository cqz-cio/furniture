package cn.iocoder.yudao.module.seo.service.config;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.seo.controller.admin.config.vo.SeoSiteConfigSaveReqVO;
import cn.iocoder.yudao.module.seo.dal.dataobject.config.SeoSiteConfigDO;
import cn.iocoder.yudao.module.seo.dal.mysql.config.SeoSiteConfigMapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import jakarta.annotation.Resource;
import jakarta.validation.ConstraintViolationException;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.SITE_CONFIG_NOT_EXISTS;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.SITE_CONFIG_URL_INVALID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import({SeoSiteConfigServiceImpl.class, ValidationAutoConfiguration.class,
        SeoSiteConfigServiceImplTest.TenantInterceptorTestConfiguration.class})
class SeoSiteConfigServiceImplTest extends BaseDbUnitTest {

    private static final Long TENANT_ONE = 1L;
    private static final Long TENANT_TWO = 2L;

    @Resource
    private SeoSiteConfigService siteConfigService;
    @Resource
    private SeoSiteConfigMapper siteConfigMapper;
    @Resource
    private DataSource dataSource;
    @Resource
    private TenantLineInnerInterceptor tenantLineInnerInterceptor;
    @Resource
    private MybatisPlusInterceptor mybatisPlusInterceptor;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ONE);
        LoginUser loginUser = new LoginUser().setId(100L).setTenantId(TENANT_ONE).setUserType(1);
        SecurityFrameworkUtils.setLoginUser(loginUser, new MockHttpServletRequest());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void saveSiteConfig_shouldInsertDefaultsAndNormalizeUrl() {
        SeoSiteConfigSaveReqVO reqVO = newRequest(10L, "  HTTPS://SHOP.Example.COM:443/  ");
        reqVO.setDefaultTitleSuffix(" ");
        reqVO.setDefaultDescription(null);
        reqVO.setDefaultRobots("\t");
        reqVO.setDefaultOgImage("");
        reqVO.setDefaultLocale(" ");

        siteConfigService.saveSiteConfig(reqVO);

        SeoSiteConfigDO saved = siteConfigMapper.selectBySiteId(10L);
        assertThat(saved.getSiteUrl()).isEqualTo("https://shop.example.com");
        assertThat(saved.getDefaultLocale()).isEqualTo("zh-CN");
        assertThat(saved.getDefaultRobots()).isEqualTo("index,follow");
        assertThat(saved.getDefaultTitleSuffix()).isEmpty();
        assertThat(saved.getDefaultDescription()).isEmpty();
        assertThat(saved.getDefaultOgImage()).isEmpty();
        assertThat(saved.getTenantId()).isEqualTo(TENANT_ONE);
    }

    @Test
    void saveSiteConfig_shouldUpdateExistingRowWithoutChangingId() {
        siteConfigService.saveSiteConfig(newRequest(20L, "https://old.example.com"));
        SeoSiteConfigDO before = siteConfigMapper.selectBySiteId(20L);

        SeoSiteConfigSaveReqVO update = newRequest(20L, "https://NEW.example.com:8443/base/./catalog/../");
        update.setSiteName("Updated Store");
        update.setDefaultLocale("en-US");
        siteConfigService.saveSiteConfig(update);

        SeoSiteConfigDO after = siteConfigMapper.selectBySiteId(20L);
        assertThat(after.getId()).isEqualTo(before.getId());
        assertThat(after.getSiteName()).isEqualTo("Updated Store");
        assertThat(after.getSiteUrl()).isEqualTo("https://new.example.com:8443/base");
        assertThat(after.getDefaultLocale()).isEqualTo("en-US");
        assertThat(siteConfigMapper.selectCount()).isOne();
    }

    @Test
    void saveSiteConfig_shouldNormalizeDefaultLocaleWithMetadataRules() {
        SeoSiteConfigSaveReqVO reqVO = newRequest(21L, "https://shop.example.com");
        reqVO.setDefaultLocale(" zh-hans-cn ");

        siteConfigService.saveSiteConfig(reqVO);

        assertThat(siteConfigMapper.selectBySiteId(21L).getDefaultLocale()).isEqualTo("zh-Hans-CN");
    }

    @ParameterizedTest
    @ValueSource(strings = {"zh_CN", "not a locale", "-en", "en-"})
    void saveSiteConfig_shouldRejectInvalidDefaultLocaleWithStableBusinessError(String locale) {
        SeoSiteConfigSaveReqVO reqVO = newRequest(22L, "https://shop.example.com");
        reqVO.setDefaultLocale(locale);

        assertThatThrownBy(() -> siteConfigService.saveSiteConfig(reqVO))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(1_070_000_000);
        assertThat(siteConfigMapper.selectCount()).isZero();
    }

    @Test
    void getSiteConfig_shouldReturnNullForAnotherTenant() {
        TenantContextHolder.setTenantId(TENANT_TWO);
        siteConfigService.saveSiteConfig(newRequest(30L, "https://tenant-two.example.com"));
        Long storedTenant = new JdbcTemplate(dataSource).queryForObject(
                "SELECT tenant_id FROM seo_site_config WHERE site_id = 30", Long.class);
        assertThat(storedTenant).isEqualTo(TENANT_TWO);

        TenantContextHolder.setTenantId(TENANT_ONE);

        assertThat(siteConfigService.getSiteConfig(30L)).isNull();
        assertThat(siteConfigMapper.selectBySiteId(30L)).isNull();
        assertThat(mybatisPlusInterceptor.getInterceptors()).contains(tenantLineInnerInterceptor);
    }

    @Test
    void selectBySiteIdForUpdate_shouldUseCurrentReadAndRemainTenantScoped() {
        TenantContextHolder.setTenantId(TENANT_TWO);
        siteConfigService.saveSiteConfig(newRequest(31L, "https://locking-read.example.com"));
        SeoSiteConfigDO tenantTwoConfig = siteConfigMapper.selectBySiteIdForUpdate(31L);
        assertThat(tenantTwoConfig).isNotNull();

        TenantContextHolder.setTenantId(TENANT_ONE);

        assertThat(siteConfigMapper.selectBySiteIdForUpdate(31L)).isNull();
    }

    @Test
    void getRequiredSiteConfig_shouldUseModuleNotFoundError() {
        assertThatThrownBy(() -> siteConfigService.getRequiredSiteConfig(404L))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(SITE_CONFIG_NOT_EXISTS.getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://shop.example.com",
            "/relative/path",
            "https:///missing-host",
            "https://user:secret@shop.example.com",
            "https://shop.example.com/base?preview=true",
            "https://shop.example.com/base#fragment",
            "https:\\shop.example.com\\base",
            "https://shop.example.com/%zz",
            "https://shop.example.com:bad"
    })
    void saveSiteConfig_shouldRejectNonHttpUrl(String siteUrl) {
        SeoSiteConfigSaveReqVO reqVO = newRequest(40L, siteUrl);

        assertThatThrownBy(() -> siteConfigService.saveSiteConfig(reqVO))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(SITE_CONFIG_URL_INVALID.getCode());
        assertThat(siteConfigMapper.selectCount()).isZero();
    }

    @Test
    void saveSiteConfig_shouldPreserveNormalizedDeploymentPathAndNonDefaultPort() {
        siteConfigService.saveSiteConfig(newRequest(50L,
                "  HTTP://Shop.Example.COM:8080/deploy/a/../store////  "));

        assertThat(siteConfigService.getSiteConfig(50L).getSiteUrl())
                .isEqualTo("http://shop.example.com:8080/deploy/store");
    }

    @Test
    void saveSiteConfig_shouldPreserveEscapedReservedCharactersInDeploymentPath() {
        siteConfigService.saveSiteConfig(newRequest(51L,
                "https://shop.example.com/deploy/c%2Fd/q%3Fv/hash%23value/"));

        assertThat(siteConfigService.getSiteConfig(51L).getSiteUrl())
                .isEqualTo("https://shop.example.com/deploy/c%2Fd/q%3Fv/hash%23value");
    }

    @Test
    void saveSiteConfig_shouldNormalizeIpv6HostWithoutDuplicatingBrackets() {
        siteConfigService.saveSiteConfig(newRequest(52L,
                "HTTPS://[2001:DB8::1]:443/deploy/"));

        assertThat(siteConfigService.getSiteConfig(52L).getSiteUrl())
                .isEqualTo("https://[2001:db8::1]/deploy");
    }

    @Test
    void saveSiteConfig_directCallerShouldRejectNullSiteId() {
        SeoSiteConfigSaveReqVO reqVO = newRequest(null, "https://shop.example.com");

        assertDirectServiceValidation(reqVO);
    }

    @Test
    void saveSiteConfig_directCallerShouldRejectBlankSiteName() {
        SeoSiteConfigSaveReqVO reqVO = newRequest(60L, "https://shop.example.com");
        reqVO.setSiteName(" ");

        assertDirectServiceValidation(reqVO);
    }

    @Test
    void saveSiteConfig_directCallerShouldRejectOversizedOptionalField() {
        SeoSiteConfigSaveReqVO reqVO = newRequest(61L, "https://shop.example.com");
        reqVO.setDefaultDescription("x".repeat(501));

        assertDirectServiceValidation(reqVO);
    }

    private void assertDirectServiceValidation(SeoSiteConfigSaveReqVO reqVO) {
        assertThat(AopUtils.isAopProxy(siteConfigService)).isTrue();
        assertThatThrownBy(() -> siteConfigService.saveSiteConfig(reqVO))
                .isInstanceOf(ConstraintViolationException.class);
    }

    private static SeoSiteConfigSaveReqVO newRequest(Long siteId, String siteUrl) {
        return new SeoSiteConfigSaveReqVO()
                .setSiteId(siteId)
                .setSiteName("Example Store")
                .setSiteUrl(siteUrl);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TenantProperties.class)
    static class TenantInterceptorTestConfiguration {

        @Bean
        TenantLineInnerInterceptor tenantLineInnerInterceptor(TenantProperties properties,
                                                               MybatisPlusInterceptor interceptor) {
            TenantLineInnerInterceptor inner =
                    new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(properties));
            MyBatisUtils.addInterceptor(interceptor, inner, 0);
            return inner;
        }

    }

}
