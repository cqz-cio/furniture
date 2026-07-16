package cn.iocoder.yudao.module.seo.service.metadata;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.seo.controller.admin.metadata.vo.SeoMetadataSaveReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.metadata.vo.SeoMetadataPageReqVO;
import cn.iocoder.yudao.module.seo.dal.dataobject.metadata.SeoMetadataDO;
import cn.iocoder.yudao.module.seo.dal.mysql.metadata.SeoMetadataMapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import jakarta.annotation.Resource;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.sql.DataSource;
import java.util.List;

import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.ENTITY_TYPE_INVALID;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.METADATA_CANONICAL_URL_INVALID;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.METADATA_DUPLICATE;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.METADATA_IDENTITY_IMMUTABLE;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.METADATA_NOT_EXISTS;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.METADATA_VERSION_CONFLICT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import({SeoMetadataServiceImpl.class, ValidationAutoConfiguration.class,
        SeoMetadataServiceImplTest.TenantInterceptorTestConfiguration.class})
class SeoMetadataServiceImplTest extends BaseDbUnitTest {

    private static final Long TENANT_ONE = 1L;
    private static final Long TENANT_TWO = 2L;

    @Resource
    private SeoMetadataService metadataService;
    @Resource
    private SeoMetadataMapper metadataMapper;
    @Resource
    private DataSource dataSource;

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
    void createMetadata_shouldPersistDraftAndRelatedKeyphrases() {
        SeoMetadataSaveReqVO reqVO = newRequest(10L, "PRODUCT", 100L, " zh-cn ");
        reqVO.setRelatedKeyphrases(List.of("solid wood", "table, dining"));
        reqVO.setCanonicalUrl("https://shop.example.com/products/100?preview=true");

        Long id = metadataService.createMetadata(reqVO);

        assertThat(id).isNotNull();
        assertThat(new JdbcTemplate(dataSource).queryForMap(
                "SELECT id, tenant_id, deleted FROM seo_metadata WHERE entity_id = 100"))
                .containsEntry("id", id)
                .containsEntry("tenant_id", TENANT_ONE)
                .containsEntry("deleted", false);
        SeoMetadataDO saved = metadataMapper.selectById(id);
        assertThat(saved.getPublishStatus()).isEqualTo("DRAFT");
        assertThat(saved.getVersion()).isEqualTo(1);
        assertThat(saved.getLocale()).isEqualTo("zh-CN");
        assertThat(saved.getRelatedKeyphrases()).containsExactly("solid wood", "table, dining");
        assertThat(saved.getTenantId()).isEqualTo(TENANT_ONE);
    }

    @Test
    void createMetadata_shouldIgnoreClientControlledId() {
        long attackerControlledId = 987654321L;
        SeoMetadataSaveReqVO reqVO = newRequest(10L, "PRODUCT", 107L, "en-US")
                .setId(attackerControlledId);

        Long generatedId = metadataService.createMetadata(reqVO);

        assertThat(generatedId).isNotNull().isNotEqualTo(attackerControlledId);
        assertThat(new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM seo_metadata WHERE id = ?", Integer.class, attackerControlledId))
                .isZero();
        assertThat(metadataMapper.selectById(generatedId)).isNotNull();
    }

    @Test
    void createMetadata_shouldRejectDuplicateEntityLocale() {
        metadataService.createMetadata(newRequest(10L, "PRODUCT", 101L, "en-us"));

        assertServiceError(() -> metadataService.createMetadata(newRequest(10L, "PRODUCT", 101L, " en-US ")),
                METADATA_DUPLICATE.getCode());
    }

    @Test
    void saveMetadata_shouldRejectUnsupportedEntityType() {
        assertServiceError(() -> metadataService.createMetadata(newRequest(10L, "UNKNOWN", 102L, "en-US")),
                ENTITY_TYPE_INVALID.getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/relative/path",
            "ftp://shop.example.com/product",
            "https:///missing-host",
            "https://user:secret@shop.example.com/product",
            "https://shop.example.com/product#details",
            "https:\\shop.example.com\\product",
            "https://shop.example.com/%zz",
            "https://shop.example.com:bad"
    })
    void saveMetadata_shouldRejectInvalidCanonicalUrl(String canonicalUrl) {
        SeoMetadataSaveReqVO reqVO = newRequest(10L, "PRODUCT", 103L, "en-US");
        reqVO.setCanonicalUrl(canonicalUrl);

        assertServiceError(() -> metadataService.createMetadata(reqVO), METADATA_CANONICAL_URL_INVALID.getCode());
    }

    @Test
    void createMetadata_shouldAllowBlankCanonicalUrl() {
        SeoMetadataSaveReqVO reqVO = newRequest(10L, "PRODUCT", 104L, "en-US");
        reqVO.setCanonicalUrl(" ");

        Long id = metadataService.createMetadata(reqVO);

        assertThat(metadataMapper.selectById(id).getCanonicalUrl()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"zh_CN", "not a locale", "-en", "en-"})
    void createMetadata_shouldRejectInvalidLocale(String locale) {
        SeoMetadataSaveReqVO reqVO = newRequest(10L, "PRODUCT", 105L, locale);

        assertThatThrownBy(() -> metadataService.createMetadata(reqVO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createMetadata_directCallerShouldRejectNullSiteId() {
        SeoMetadataSaveReqVO reqVO = newRequest(null, "PRODUCT", 106L, "en-US");

        assertThat(AopUtils.isAopProxy(metadataService)).isTrue();
        assertThatThrownBy(() -> metadataService.createMetadata(reqVO))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void updateMetadata_shouldAtomicallyIncrementVersionAndEditableFields() {
        Long id = metadataService.createMetadata(newRequest(20L, "PRODUCT", 200L, "en-US"));
        SeoMetadataSaveReqVO update = newRequest(20L, "PRODUCT", 200L, "en-us")
                .setId(id)
                .setVersion(1)
                .setSeoTitle("Updated live title")
                .setRelatedKeyphrases(List.of("updated", "comma, preserved"));

        metadataService.updateMetadata(update);

        SeoMetadataDO saved = metadataMapper.selectById(id);
        assertThat(saved.getSeoTitle()).isEqualTo("Updated live title");
        assertThat(saved.getRelatedKeyphrases()).containsExactly("updated", "comma, preserved");
        assertThat(saved.getVersion()).isEqualTo(2);
        assertThat(saved.getUpdater()).isEqualTo("100");
        assertThat(saved.getUpdateTime()).isNotNull();
    }

    @Test
    void updateMetadata_shouldRejectStaleVersion() {
        Long id = metadataService.createMetadata(newRequest(20L, "PRODUCT", 201L, "en-US"));
        SeoMetadataSaveReqVO first = newRequest(20L, "PRODUCT", 201L, "en-US").setId(id).setVersion(1);
        metadataService.updateMetadata(first);

        SeoMetadataSaveReqVO stale = newRequest(20L, "PRODUCT", 201L, "en-US").setId(id).setVersion(1);

        assertServiceError(() -> metadataService.updateMetadata(stale), METADATA_VERSION_CONFLICT.getCode());
        assertThat(metadataMapper.selectById(id).getVersion()).isEqualTo(2);
    }

    @Test
    void updateMetadata_shouldRejectIdentitySwitch() {
        Long id = metadataService.createMetadata(newRequest(20L, "PRODUCT", 202L, "en-US"));
        SeoMetadataSaveReqVO update = newRequest(21L, "PRODUCT", 202L, "en-US").setId(id).setVersion(1);

        assertServiceError(() -> metadataService.updateMetadata(update), METADATA_IDENTITY_IMMUTABLE.getCode());
        assertThat(metadataMapper.selectById(id).getVersion()).isEqualTo(1);
    }

    @Test
    void updateMetadata_shouldRequireIdAndVersion() {
        SeoMetadataSaveReqVO missingId = newRequest(20L, "PRODUCT", 203L, "en-US").setVersion(1);
        SeoMetadataSaveReqVO missingVersion = newRequest(20L, "PRODUCT", 203L, "en-US").setId(99L);

        assertThatThrownBy(() -> metadataService.updateMetadata(missingId)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> metadataService.updateMetadata(missingVersion)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateMetadata_shouldDistinguishMissingFromVersionConflict() {
        SeoMetadataSaveReqVO missing = newRequest(20L, "PRODUCT", 204L, "en-US")
                .setId(99999L).setVersion(1);

        assertServiceError(() -> metadataService.updateMetadata(missing), METADATA_NOT_EXISTS.getCode());
    }

    @Test
    void publishMetadata_shouldAtomicallySetStatusVersionAndPublishedTime() {
        Long id = metadataService.createMetadata(newRequest(30L, "ARTICLE", 300L, "zh-CN"));

        metadataService.publishMetadata(id, 1);

        SeoMetadataDO saved = metadataMapper.selectById(id);
        assertThat(saved.getPublishStatus()).isEqualTo("PUBLISHED");
        assertThat(saved.getVersion()).isEqualTo(2);
        assertThat(saved.getPublishedTime()).isNotNull();
        assertThat(saved.getUpdater()).isEqualTo("100");
    }

    @Test
    void publishMetadata_shouldRejectStaleVersion() {
        Long id = metadataService.createMetadata(newRequest(30L, "ARTICLE", 301L, "zh-CN"));
        metadataService.publishMetadata(id, 1);

        assertServiceError(() -> metadataService.publishMetadata(id, 1), METADATA_VERSION_CONFLICT.getCode());
        assertThat(metadataMapper.selectById(id).getVersion()).isEqualTo(2);
    }

    @Test
    void publishMetadata_shouldDistinguishMissingFromVersionConflict() {
        assertServiceError(() -> metadataService.publishMetadata(99999L, 1), METADATA_NOT_EXISTS.getCode());
    }

    @Test
    void deleteMetadata_shouldLogicallyDeleteAndAllowRepeatedRecreation() {
        Long firstId = metadataService.createMetadata(newRequest(40L, "PAGE", 400L, "en-US"));

        metadataService.deleteMetadata(firstId);
        assertServiceError(() -> metadataService.deleteMetadata(firstId), METADATA_NOT_EXISTS.getCode());
        Long secondId = metadataService.createMetadata(newRequest(40L, "PAGE", 400L, "en-us"));
        metadataService.deleteMetadata(secondId);
        Long thirdId = metadataService.createMetadata(newRequest(40L, "PAGE", 400L, "en-US"));

        assertThat(firstId).isNotEqualTo(secondId).isNotEqualTo(thirdId);
        assertThat(metadataService.getMetadata(thirdId)).isNotNull();
        assertThat(new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM seo_metadata WHERE site_id = 40 AND deleted = TRUE", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void metadataOperations_shouldNotCrossTenantBoundary() {
        TenantContextHolder.setTenantId(TENANT_TWO);
        Long tenantTwoId = metadataService.createMetadata(newRequest(50L, "PRODUCT", 500L, "en-US"));
        metadataService.publishMetadata(tenantTwoId, 1);

        TenantContextHolder.setTenantId(TENANT_ONE);
        SeoMetadataSaveReqVO update = newRequest(50L, "PRODUCT", 500L, "en-US")
                .setId(tenantTwoId).setVersion(1).setSeoTitle("cross tenant");

        assertServiceError(() -> metadataService.getMetadata(tenantTwoId), METADATA_NOT_EXISTS.getCode());
        assertServiceError(() -> metadataService.updateMetadata(update), METADATA_NOT_EXISTS.getCode());
        assertServiceError(() -> metadataService.publishMetadata(tenantTwoId, 1), METADATA_NOT_EXISTS.getCode());
        assertServiceError(() -> metadataService.deleteMetadata(tenantTwoId), METADATA_NOT_EXISTS.getCode());
        assertThat(metadataService.getPublishedMetadata(50L, "PRODUCT", 500L, "en-US")).isNull();
        assertThat(new JdbcTemplate(dataSource).queryForMap(
                "SELECT tenant_id, version, publish_status, deleted FROM seo_metadata WHERE id = ?", tenantTwoId))
                .containsEntry("tenant_id", TENANT_TWO)
                .containsEntry("version", 2)
                .containsEntry("publish_status", "PUBLISHED")
                .containsEntry("deleted", false);
    }

    @Test
    void getPublishedMetadata_shouldIgnoreDraftAndRequireExactNormalizedLocale() {
        Long publishedId = metadataService.createMetadata(newRequest(60L, "CATEGORY", 600L, "en-US"));
        metadataService.publishMetadata(publishedId, 1);
        metadataService.createMetadata(newRequest(60L, "CATEGORY", 600L, "zh-CN"));

        SeoMetadataDO resolved = metadataService.getPublishedMetadata(60L, "CATEGORY", 600L, " en-us ");

        assertThat(resolved).isNotNull();
        assertThat(resolved.getId()).isEqualTo(publishedId);
        assertThat(metadataService.getPublishedMetadata(60L, "CATEGORY", 600L, "en-GB")).isNull();
        assertThat(metadataService.getPublishedMetadata(60L, "CATEGORY", 600L, "zh-CN")).isNull();
    }

    @Test
    void getMetadataPage_shouldNormalizeLocaleAndApplyKeyword() {
        metadataService.createMetadata(newRequest(70L, "PRODUCT", 701L, "zh-CN")
                .setSeoTitle("Dining table"));
        metadataService.createMetadata(newRequest(70L, "PRODUCT", 702L, "en-US")
                .setSeoTitle("Dining table English"));
        metadataService.createMetadata(newRequest(70L, "PRODUCT", 703L, "zh-CN")
                .setSeoTitle("Sofa"));
        SeoMetadataPageReqVO reqVO = new SeoMetadataPageReqVO()
                .setSiteId(70L)
                .setLocale(" zh-cn ")
                .setKeyword("Dining");

        PageResult<SeoMetadataDO> page = metadataService.getMetadataPage(reqVO);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getList()).extracting(SeoMetadataDO::getEntityId).containsExactly(701L);
    }

    @Test
    void getMetadataPage_shouldReturnOnlyCurrentTenantRecords() {
        metadataService.createMetadata(newRequest(71L, "PRODUCT", 7101L, "en-US")
                .setSeoTitle("Shared tenant keyword"));
        TenantContextHolder.setTenantId(TENANT_TWO);
        metadataService.createMetadata(newRequest(71L, "PRODUCT", 7201L, "en-US")
                .setSeoTitle("Shared tenant keyword"));
        TenantContextHolder.setTenantId(TENANT_ONE);
        SeoMetadataPageReqVO reqVO = new SeoMetadataPageReqVO()
                .setSiteId(71L)
                .setLocale("en-US")
                .setKeyword("Shared tenant keyword");

        PageResult<SeoMetadataDO> page = metadataService.getMetadataPage(reqVO);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getList()).extracting(SeoMetadataDO::getEntityId).containsExactly(7101L);
        assertThat(page.getList()).extracting(SeoMetadataDO::getTenantId).containsOnly(TENANT_ONE);
    }

    private static void assertServiceError(ThrowingRunnable action, int code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(code);
    }

    private static SeoMetadataSaveReqVO newRequest(Long siteId, String entityType, Long entityId, String locale) {
        return new SeoMetadataSaveReqVO()
                .setSiteId(siteId)
                .setEntityType(entityType)
                .setEntityId(entityId)
                .setLocale(locale)
                .setSeoTitle("Example title")
                .setMetaDescription("Example description")
                .setFocusKeyphrase("example")
                .setRobotsIndex(true)
                .setRobotsFollow(true)
                .setOgTitle("Example OG title")
                .setOgDescription("Example OG description")
                .setOgImage("https://cdn.example.com/og.jpg")
                .setSchemaType("Product");
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
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
