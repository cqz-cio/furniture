package cn.iocoder.yudao.module.seo.service.analysis.engine;

import cn.iocoder.yudao.module.seo.service.analysis.dictionary.SeoIndustryDictionary;
import cn.iocoder.yudao.module.seo.service.analysis.lexical.SeoTextNormalizer;
import cn.iocoder.yudao.module.seo.service.analysis.model.SeoContentSnapshot;
import cn.iocoder.yudao.module.seo.service.analysis.model.SeoKeywordEvaluation;
import cn.iocoder.yudao.module.seo.service.analysis.semantic.DisabledSeoSemanticSimilarityProvider;
import cn.iocoder.yudao.module.seo.service.analysis.semantic.SeoSemanticSimilarityProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultSeoKeywordAnalysisEngineTest {

    private DefaultSeoKeywordAnalysisEngine engine;

    @BeforeEach
    void setUp() {
        SeoTextNormalizer normalizer = new SeoTextNormalizer();
        engine = new DefaultSeoKeywordAnalysisEngine(normalizer, new SeoIndustryDictionary(normalizer),
                new DisabledSeoSemanticSimilarityProvider(), new SeoKeywordScorer(),
                new SeoRuleSuggestionService());
    }

    @Test
    void analyze_shouldReturnExplainablePartialPercentForEveryAvailableDimension() {
        SeoContentSnapshot snapshot = relevantSnapshot();

        SeoKeywordEvaluation result = engine.analyze("实木餐桌", "FOCUS", 0, snapshot);

        assertThat(result.getNormalizedKeyword()).isEqualTo("实木餐桌");
        assertThat(result.getSemanticPercent()).isNull();
        assertThat(result.getAnalysisStatus()).isEqualTo("PARTIAL");
        assertThat(result.getRelevancePercent()).isBetween(75, 100);
        assertThat(result.getKeyPositionPercent()).isGreaterThanOrEqualTo(80);
        assertThat(result.getLexicalMatchPercent()).isGreaterThanOrEqualTo(60);
        assertThat(result.getIntentCoveragePercent()).isEqualTo(100);
        assertThat(result.getMatchedLocations()).contains("SEO_TITLE", "H1", "INTRODUCTION")
                .doesNotContain("ATTRIBUTE");
        assertThat(result.getItems())
                .filteredOn(item -> item.getRuleCode().equals("KW_SEMANTIC_PROVIDER"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getStatus()).isEqualTo("NOT_COMPLETED");
                    assertThat(item.getRecommendation()).contains("不伪造 0%");
                });
    }

    @Test
    void analyze_shouldKeepDifferentKeywordsEvidenceIsolated() {
        SeoContentSnapshot snapshot = relevantSnapshot();

        SeoKeywordEvaluation unrelated = engine.analyze("真皮沙发", "RELATED", 1, snapshot);

        assertThat(unrelated.getExactMatchCount()).isZero();
        assertThat(unrelated.getMatchedLocations()).isEmpty();
        assertThat(unrelated.getRelevancePercent()).isLessThan(40);
        assertThat(unrelated.getItems()).anySatisfy(item -> {
            if (item.getRuleCode().equals("KW_TOPIC_UNRELATED")) {
                assertThat(item.getRecommendation()).contains("删除该关键词或转到更合适的页面")
                        .doesNotContain("强行插入");
            }
        });
        assertThat(unrelated.getItems())
                .filteredOn(item -> item.getStatus().equals("GOOD"))
                .noneSatisfy(item -> assertThat(item.getEvidence().toString()).contains("matchType=EXACT"));
    }

    @Test
    void analyze_shouldStayPartialWhileBm25RuleIsNotCompleted() {
        SeoTextNormalizer normalizer = new SeoTextNormalizer();
        SeoSemanticSimilarityProvider availableSemanticProvider = new SeoSemanticSimilarityProvider() {
            @Override
            public OptionalInt calculatePercent(String keyword, SeoContentSnapshot snapshot) {
                return OptionalInt.of(88);
            }

            @Override
            public String getModelVersion() {
                return "test-semantic-v1";
            }

            @Override
            public String getUnavailableReason() {
                return "";
            }
        };
        DefaultSeoKeywordAnalysisEngine engineWithSemantic = new DefaultSeoKeywordAnalysisEngine(
                normalizer, new SeoIndustryDictionary(normalizer), availableSemanticProvider,
                new SeoKeywordScorer(), new SeoRuleSuggestionService());

        SeoKeywordEvaluation result = engineWithSemantic.analyze("实木餐桌", "FOCUS", 0, relevantSnapshot());

        assertThat(result.getSemanticPercent()).isEqualTo(88);
        assertThat(result.getAnalysisStatus()).isEqualTo("PARTIAL");
        assertThat(result.getItems())
                .filteredOn(item -> item.getRuleCode().equals("KW_LEXICAL_BM25"))
                .singleElement()
                .satisfies(item -> assertThat(item.getStatus()).isEqualTo("NOT_COMPLETED"));
    }

    @Test
    void analyze_shouldPenalizeKeywordStuffing() {
        SeoContentSnapshot natural = relevantSnapshot();
        SeoContentSnapshot stuffed = relevantSnapshot();
        stuffed.setBody(("实木餐桌 ").repeat(20));

        SeoKeywordEvaluation naturalResult = engine.analyze("实木餐桌", "FOCUS", 0, natural);
        SeoKeywordEvaluation stuffedResult = engine.analyze("实木餐桌", "FOCUS", 0, stuffed);

        assertThat(stuffedResult.getDistributionPercent()).isLessThan(naturalResult.getDistributionPercent());
        assertThat(stuffedResult.getItems()).extracting("ruleCode").contains("KW_DISTRIBUTION_STUFFING");
    }

    @Test
    void analyze_shouldExcludeUnavailableKeyPositionsInsteadOfScoringThemAsZero() {
        SeoContentSnapshot snapshot = new SeoContentSnapshot();
        snapshot.setBody("这是一段介绍实木餐桌材质和用途的正文。");

        SeoKeywordEvaluation result = engine.analyze("实木餐桌", "FOCUS", 0, snapshot);

        assertThat(result.getKeyPositionPercent()).isNull();
        assertThat(result.getItems()).anySatisfy(item -> {
            if (item.getRuleCode().equals("KW_POSITION_NOT_APPLICABLE")) {
                assertThat(item.getStatus()).isEqualTo("NOT_APPLICABLE");
            }
        });
    }

    private static SeoContentSnapshot relevantSnapshot() {
        SeoContentSnapshot snapshot = new SeoContentSnapshot();
        snapshot.setSeoTitle("Oakved 北欧实木餐桌 1.8m");
        snapshot.setH1("北欧实木餐桌");
        snapshot.setIntroduction("这款实木餐桌采用橡木制作，适合家庭餐厅。");
        snapshot.setMetaDescription("了解 Oakved 实木餐桌的材质、尺寸和配送服务。");
        snapshot.setSlug("solid-wood-dining-table");
        snapshot.setHeadings(List.of("橡木材质", "实木餐桌的保养方法"));
        snapshot.setParagraphs(List.of("桌面选用实木，保留自然木纹。", "提供入户配送和安装说明。"));
        snapshot.setBody("实木餐桌适合现代和北欧风格餐厅，日常使用后请用软布保养。");
        snapshot.setAttributes(Map.of("材质", "橡木实木", "尺寸", "1800 x 900 mm", "风格", "北欧"));
        snapshot.setImageAlts(List.of("北欧实木餐桌正面图"));
        return snapshot;
    }

}
