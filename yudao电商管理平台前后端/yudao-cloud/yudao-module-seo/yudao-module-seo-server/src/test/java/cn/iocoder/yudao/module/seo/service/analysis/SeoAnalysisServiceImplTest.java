package cn.iocoder.yudao.module.seo.service.analysis;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoAnalysisCompareRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoAnalysisRespVO;
import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoAnalysisRunReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoContentSnapshotReqVO;
import cn.iocoder.yudao.module.seo.controller.admin.analysis.vo.SeoKeywordAnalysisRespVO;
import cn.iocoder.yudao.module.seo.dal.mysql.analysis.SeoAnalysisMapper;
import cn.iocoder.yudao.module.seo.service.analysis.dictionary.SeoIndustryDictionary;
import cn.iocoder.yudao.module.seo.service.analysis.engine.DefaultSeoKeywordAnalysisEngine;
import cn.iocoder.yudao.module.seo.service.analysis.engine.SeoKeywordScorer;
import cn.iocoder.yudao.module.seo.service.analysis.engine.SeoRuleSuggestionService;
import cn.iocoder.yudao.module.seo.service.analysis.lexical.SeoTextNormalizer;
import cn.iocoder.yudao.module.seo.service.analysis.semantic.DisabledSeoSemanticSimilarityProvider;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.ANALYSIS_IDEMPOTENCY_CONFLICT;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.ANALYSIS_KEYWORD_DUPLICATE;
import static cn.iocoder.yudao.module.seo.enums.ErrorCodeConstants.ANALYSIS_NOT_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import({SeoAnalysisServiceImpl.class, SeoContentSnapshotFactory.class,
        DefaultSeoKeywordAnalysisEngine.class, SeoKeywordScorer.class, SeoRuleSuggestionService.class,
        SeoTextNormalizer.class, SeoIndustryDictionary.class, DisabledSeoSemanticSimilarityProvider.class,
        ValidationAutoConfiguration.class, SeoAnalysisServiceImplTest.TenantInterceptorTestConfiguration.class})
class SeoAnalysisServiceImplTest extends BaseDbUnitTest {

    private static final Long TENANT_ONE = 1L;
    private static final Long TENANT_TWO = 2L;

    @Resource
    private SeoAnalysisService analysisService;
    @Resource
    private SeoAnalysisMapper analysisMapper;

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
    void runAnalysis_shouldPersistIndependentKeywordResultsAndEvidence() {
        SeoAnalysisRunReqVO request = manualRequest("analysis-1", relevantContent());

        Long id = analysisService.runAnalysis(request);

        SeoAnalysisRespVO result = analysisService.getAnalysis(id);
        assertThat(result.getAnalysisStatus()).isEqualTo("PARTIAL");
        assertThat(result.getOverallRelevancePercent()).isBetween(70, 100);
        assertThat(result.getKeywords()).extracting(SeoKeywordAnalysisRespVO::getKeyword)
                .containsExactly("实木餐桌", "北欧家具", "真皮沙发");
        assertThat(result.getKeywords()).allSatisfy(keyword -> {
            assertThat(keyword.getRelevancePercent()).isBetween(0, 100);
            assertThat(keyword.getSemanticPercent()).isNull();
            assertThat(keyword.getSuggestionCount()).isNotNull();
        });
        SeoKeywordAnalysisRespVO focus = analysisService.getKeyword(id, result.getKeywords().get(0).getId());
        assertThat(focus.getItems()).isNotEmpty();
        assertThat(focus.getItems()).anySatisfy(item -> {
            if ("KW_SEMANTIC_PROVIDER".equals(item.getRuleCode())) {
                assertThat(item.getStatus()).isEqualTo("NOT_COMPLETED");
                assertThat(item.getRecommendation()).contains("不伪造 0%");
            }
        });
        assertThat(analysisMapper.selectById(id).getTenantId()).isEqualTo(TENANT_ONE);
    }

    @Test
    void runAnalysis_shouldBeIdempotentAndRejectKeyReuseForChangedInput() {
        SeoAnalysisRunReqVO first = manualRequest("same-key", relevantContent());

        Long firstId = analysisService.runAnalysis(first);
        Long repeatedId = analysisService.runAnalysis(manualRequest("same-key", relevantContent()));

        assertThat(repeatedId).isEqualTo(firstId);
        SeoContentSnapshotReqVO changed = relevantContent();
        changed.setBody("完全不同的正文");
        assertServiceError(() -> analysisService.runAnalysis(manualRequest("same-key", changed)),
                ANALYSIS_IDEMPOTENCY_CONFLICT.getCode());
    }

    @Test
    void runAnalysis_shouldRejectNormalizedDuplicateKeywords() {
        SeoAnalysisRunReqVO request = manualRequest("duplicate-keywords", relevantContent());
        request.setRelatedKeyphrases(List.of("ｓｏｌｉｄ　ｗｏｏｄ", "Solid Wood"));
        request.setFocusKeyphrase("solid wood");

        assertServiceError(() -> analysisService.runAnalysis(request), ANALYSIS_KEYWORD_DUPLICATE.getCode());
    }

    @Test
    void runAnalysis_shouldLinkHistoryAndCompareEveryKeyword() {
        Long firstId = analysisService.runAnalysis(manualRequest("history-1", weakContent()));
        Long secondId = analysisService.runAnalysis(manualRequest("history-2", relevantContent()));

        SeoAnalysisRespVO second = analysisService.getAnalysis(secondId);
        SeoAnalysisCompareRespVO comparison = analysisService.compareAnalysis(secondId, null);

        assertThat(second.getPreviousAnalysisId()).isEqualTo(firstId);
        assertThat(comparison.getPreviousAnalysisId()).isEqualTo(firstId);
        assertThat(comparison.getCurrentAnalysisId()).isEqualTo(secondId);
        assertThat(comparison.getKeywords()).hasSize(3);
        assertThat(comparison.getKeywords())
                .filteredOn(keyword -> keyword.getNormalizedKeyword().equals("实木餐桌"))
                .singleElement()
                .satisfies(keyword -> {
                    assertThat(keyword.getDeltaPercent()).isPositive();
                    assertThat(keyword.getChangeType()).isEqualTo("IMPROVED");
                    assertThat(keyword.getResolvedRuleCodes()).isNotEmpty();
                });
    }

    @Test
    void analysisQueries_shouldNotCrossTenantBoundary() {
        TenantContextHolder.setTenantId(TENANT_TWO);
        Long tenantTwoId = analysisService.runAnalysis(manualRequest("tenant-two", relevantContent()));

        TenantContextHolder.setTenantId(TENANT_ONE);

        assertServiceError(() -> analysisService.getAnalysis(tenantTwoId), ANALYSIS_NOT_EXISTS.getCode());
        assertThat(analysisMapper.selectByIdForTenant(tenantTwoId)).isNull();
    }

    private static SeoAnalysisRunReqVO manualRequest(String idempotencyKey, SeoContentSnapshotReqVO content) {
        SeoAnalysisRunReqVO request = new SeoAnalysisRunReqVO();
        request.setSiteId(10L);
        request.setEntityType("PRODUCT");
        request.setEntityId(100L);
        request.setLocale("zh-cn");
        request.setFocusKeyphrase("实木餐桌");
        request.setRelatedKeyphrases(List.of("北欧家具", "真皮沙发"));
        request.setSourceType("MANUAL");
        request.setIdempotencyKey(idempotencyKey);
        request.setContent(content);
        return request;
    }

    private static SeoContentSnapshotReqVO relevantContent() {
        SeoContentSnapshotReqVO content = new SeoContentSnapshotReqVO();
        content.setSeoTitle("Oakved 北欧实木餐桌 1.8m");
        content.setH1("北欧实木餐桌");
        content.setIntroduction("这款实木餐桌采用橡木制作，适合家庭餐厅。");
        content.setMetaDescription("了解 Oakved 实木餐桌的材质、尺寸和配送服务。");
        content.setSlug("solid-wood-dining-table");
        content.setHeadings(List.of("橡木材质", "实木餐桌的保养方法"));
        content.setParagraphs(List.of("桌面选用实木，保留自然木纹。", "提供入户配送和安装说明。"));
        content.setBody("实木餐桌适合现代和北欧风格餐厅，日常使用后请用软布保养。");
        content.setAttributes(Map.of("材质", "橡木实木", "尺寸", "1800 x 900 mm", "风格", "北欧"));
        content.setImageAlts(List.of("北欧实木餐桌正面图"));
        return content;
    }

    private static SeoContentSnapshotReqVO weakContent() {
        SeoContentSnapshotReqVO content = new SeoContentSnapshotReqVO();
        content.setSeoTitle("Oakved 家具新品");
        content.setH1("新品介绍");
        content.setBody("这是一段尚未补充商品事实的简短介绍。");
        return content;
    }

    private static void assertServiceError(ThrowingRunnable action, int code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(code);
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
