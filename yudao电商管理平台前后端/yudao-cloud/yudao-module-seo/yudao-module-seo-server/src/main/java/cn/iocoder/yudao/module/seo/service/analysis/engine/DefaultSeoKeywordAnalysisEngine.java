package cn.iocoder.yudao.module.seo.service.analysis.engine;

import cn.iocoder.yudao.module.seo.enums.SeoAnalysisStatusEnum;
import cn.iocoder.yudao.module.seo.enums.SeoKeywordGradeEnum;
import cn.iocoder.yudao.module.seo.service.analysis.dictionary.SeoIndustryDictionary;
import cn.iocoder.yudao.module.seo.service.analysis.lexical.SeoTextNormalizer;
import cn.iocoder.yudao.module.seo.service.analysis.model.SeoContentSnapshot;
import cn.iocoder.yudao.module.seo.service.analysis.model.SeoKeywordEvaluation;
import cn.iocoder.yudao.module.seo.service.analysis.model.SeoKeywordRuleResult;
import cn.iocoder.yudao.module.seo.service.analysis.semantic.SeoSemanticSimilarityProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DefaultSeoKeywordAnalysisEngine implements SeoKeywordAnalysisEngine {

    public static final String ENGINE_VERSION = "seo-keyword-engine-v1";
    public static final String RULE_PROFILE_VERSION = "keyword-rules-v1";

    private static final Map<String, Integer> POSITION_WEIGHTS = createPositionWeights();

    private final SeoTextNormalizer normalizer;
    private final SeoIndustryDictionary dictionary;
    private final SeoSemanticSimilarityProvider semanticProvider;
    private final SeoKeywordScorer scorer;
    private final SeoRuleSuggestionService suggestionService;

    @Override
    public SeoKeywordEvaluation analyze(String keyword, String keywordType, int sort, SeoContentSnapshot snapshot) {
        String normalizedKeyword = normalizer.normalize(keyword);
        String visibleText = snapshot.visibleText();
        Set<String> variants = dictionary.variants(normalizedKeyword);
        int exactMatchCount = normalizer.countPhrase(visibleText, normalizedKeyword);
        int variantMatchCount = variants.stream().mapToInt(variant -> normalizer.countPhrase(visibleText, variant)).sum();
        List<String> matchedLocations = matchedLocations(snapshot, normalizedKeyword, variants);
        List<SeoKeywordRuleResult> items = new ArrayList<>();

        Integer keyPositionPercent = scoreKeyPositions(keyword, normalizedKeyword, variants, snapshot, items);
        int lexicalMatchPercent = scoreLexical(keyword, normalizedKeyword, variants, visibleText,
                exactMatchCount, variantMatchCount, items);
        OptionalInt semantic = semanticProvider.calculatePercent(keyword, snapshot);
        Integer semanticPercent = semantic.isPresent() ? clamp(semantic.getAsInt()) : null;
        addSemanticEvidence(semanticPercent, items);
        int distributionPercent = scoreDistribution(keyword, normalizedKeyword, variants, snapshot,
                exactMatchCount, items);
        int intentCoveragePercent = scoreIntent(keyword, normalizedKeyword, visibleText, items);

        Map<String, Integer> dimensions = scorer.dimensions(keyPositionPercent, lexicalMatchPercent,
                semanticPercent, distributionPercent, intentCoveragePercent);
        int relevancePercent = scorer.weightedPercent(dimensions);
        int confidencePercent = Math.max(35, scorer.availableWeight(dimensions) - 10); // BM25 is not enabled yet.
        String status = items.stream().anyMatch(item -> "NOT_COMPLETED".equals(item.getStatus()))
                ? SeoAnalysisStatusEnum.PARTIAL.getCode() : SeoAnalysisStatusEnum.SUCCEEDED.getCode();

        if (relevancePercent < 25 && exactMatchCount == 0 && variantMatchCount == 0) {
            items.add(rule("KW_TOPIC_UNRELATED", "INTENT", "HIGH", "ISSUE", null, null,
                    "BODY", Map.of("exactMatchCount", 0, "variantMatchCount", 0),
                    "页面可见内容中未找到与该关键词相关的词组、已知变体或意图属性",
                    suggestionService.unrelatedRecommendation(keyword), null, 900));
        }

        items.sort(Comparator.comparingInt(SeoKeywordRuleResult::getSort));
        return SeoKeywordEvaluation.builder()
                .keyword(keyword.trim())
                .normalizedKeyword(normalizedKeyword)
                .keywordType(keywordType)
                .sort(sort)
                .keyPositionPercent(keyPositionPercent)
                .lexicalMatchPercent(lexicalMatchPercent)
                .semanticPercent(semanticPercent)
                .distributionPercent(distributionPercent)
                .intentCoveragePercent(intentCoveragePercent)
                .relevancePercent(relevancePercent)
                .confidencePercent(confidencePercent)
                .grade(SeoKeywordGradeEnum.fromPercent(relevancePercent).getCode())
                .analysisStatus(status)
                .exactMatchCount(exactMatchCount)
                .variantMatchCount(variantMatchCount)
                .matchedLocations(matchedLocations)
                .dictionaryVersion(dictionary.getVersion())
                .semanticModelVersion(semanticProvider.getModelVersion())
                .items(List.copyOf(items))
                .build();
    }

    @Override
    public String getEngineVersion() {
        return ENGINE_VERSION;
    }

    @Override
    public String getRuleProfileVersion() {
        return RULE_PROFILE_VERSION;
    }

    private Integer scoreKeyPositions(String keyword, String normalizedKeyword, Set<String> variants,
                                      SeoContentSnapshot snapshot, List<SeoKeywordRuleResult> items) {
        Map<String, List<String>> locations = snapshot.locationTexts();
        int availableWeight = POSITION_WEIGHTS.entrySet().stream()
                .filter(position -> {
                    List<String> values = locations.get(position.getKey());
                    return values != null && !values.isEmpty();
                })
                .mapToInt(Map.Entry::getValue)
                .sum();
        if (availableWeight == 0) {
            items.add(rule("KW_POSITION_NOT_APPLICABLE", "KEY_POSITION", "INFO", "NOT_APPLICABLE",
                    null, null, null, Map.of(), "当前快照没有可用的标题、简介、Slug 或图片 ALT 位置",
                    "补充结构化商品内容后重新分析", null, 100));
            return null;
        }
        BigDecimal earnedWeight = BigDecimal.ZERO;
        int sort = 100;
        for (Map.Entry<String, Integer> position : POSITION_WEIGHTS.entrySet()) {
            List<String> values = locations.get(position.getKey());
            if (values == null || values.isEmpty()) {
                continue;
            }
            String joined = String.join(" ", values);
            boolean exact = normalizer.containsPhrase(joined, normalizedKeyword);
            String matchedVariant = variants.stream()
                    .filter(variant -> normalizer.containsPhrase(joined, variant))
                    .findFirst().orElse(null);
            BigDecimal locationScore = exact ? BigDecimal.valueOf(position.getValue())
                    : matchedVariant != null ? BigDecimal.valueOf(position.getValue()).multiply(BigDecimal.valueOf(0.7))
                    : BigDecimal.ZERO;
            earnedWeight = earnedWeight.add(locationScore);
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("text", snippet(joined));
            evidence.put("matchType", exact ? "EXACT" : matchedVariant != null ? "VARIANT" : "NONE");
            if (matchedVariant != null) {
                evidence.put("matchedVariant", matchedVariant);
            }
            boolean matched = locationScore.signum() > 0;
            BigDecimal recoverable = matched ? null : BigDecimal.valueOf(position.getValue())
                    .multiply(BigDecimal.valueOf(25))
                    .divide(BigDecimal.valueOf(availableWeight), 2, RoundingMode.HALF_UP);
            items.add(rule("KW_POSITION_" + position.getKey(), "KEY_POSITION",
                    matched ? "INFO" : position.getValue() >= 15 ? "HIGH" : "MEDIUM",
                    matched ? "GOOD" : "ISSUE", locationScore, BigDecimal.valueOf(position.getValue()),
                    position.getKey(), evidence,
                    matched ? suggestionService.locationLabel(position.getKey()) + "已命中该关键词"
                            : suggestionService.missingPositionReason(position.getKey(), keyword),
                    matched ? "" : suggestionService.missingPositionRecommendation(position.getKey(), keyword),
                    recoverable, sort++));
        }
        return earnedWeight.multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(availableWeight), 0, RoundingMode.HALF_UP).intValue();
    }

    private int scoreLexical(String keyword, String normalizedKeyword, Set<String> variants, String visibleText,
                             int exactMatchCount, int variantMatchCount, List<SeoKeywordRuleResult> items) {
        List<String> keywordTokens = normalizer.tokens(normalizedKeyword);
        List<String> contentTokens = normalizer.tokens(visibleText);
        long coveredTokens = keywordTokens.stream().filter(contentTokens::contains).count();
        int tokenCoverage = keywordTokens.isEmpty() ? 0
                : round(coveredTokens * 100.0 / keywordTokens.size());
        int exactPoints = exactMatchCount > 0 ? 40 : tokenCoverage == 100 ? 20 : tokenCoverage >= 50 ? 10 : 0;
        int availablePoints = 40;
        int variantPoints = 0;
        if (!variants.isEmpty()) {
            availablePoints += 25;
            variantPoints = Math.min(25, variantMatchCount * 13);
        }

        items.add(rule("KW_LEXICAL_EXACT_PHRASE", "LEXICAL", exactPoints == 40 ? "INFO" : "HIGH",
                exactPoints == 40 ? "GOOD" : "ISSUE", BigDecimal.valueOf(exactPoints), BigDecimal.valueOf(40),
                "BODY", Map.of("exactMatchCount", exactMatchCount, "tokenCoveragePercent", tokenCoverage),
                exactPoints == 40 ? "可见内容中已命中完整词组"
                        : "可见内容中未稳定命中完整词组“" + keyword + "”",
                exactPoints == 40 ? "" : "在最能说明商品主题的一处自然使用完整词组，不要为提高词频重复堆叠",
                exactPoints == 40 ? null : BigDecimal.valueOf(8), 300));

        if (!variants.isEmpty()) {
            items.add(rule("KW_LEXICAL_KNOWN_VARIANTS", "LEXICAL", variantMatchCount > 0 ? "INFO" : "LOW",
                    variantMatchCount > 0 ? "GOOD" : "ISSUE", BigDecimal.valueOf(variantPoints),
                    BigDecimal.valueOf(25), "BODY",
                    Map.of("knownVariants", variants, "variantMatchCount", variantMatchCount),
                    variantMatchCount > 0 ? "内容命中了受版本控制的家具行业变体词"
                            : "内容未命中已知的同义词或行业变体",
                    variantMatchCount > 0 ? "" : "仅在符合实际商品表述时，选择一个用户常用的变体词补充说明",
                    null, 310));
        }

        items.add(rule("KW_LEXICAL_BM25", "LEXICAL", "INFO", "NOT_COMPLETED", null,
                BigDecimal.valueOf(35), null, Map.of("provider", "DISABLED"),
                "BM25 站内语料索引尚未启用，本分项已按精确词和变体词的可用权重归一化",
                "启用按租户、站点和语言隔离的 Lucene/BM25 索引后可提高词法评分可信度",
                null, 320));
        return round((exactPoints + variantPoints) * 100.0 / availablePoints);
    }

    private void addSemanticEvidence(Integer semanticPercent, List<SeoKeywordRuleResult> items) {
        if (semanticPercent == null) {
            items.add(rule("KW_SEMANTIC_PROVIDER", "SEMANTIC", "INFO", "NOT_COMPLETED", null,
                    BigDecimal.valueOf(100), null, Map.of("provider", "DISABLED"),
                    semanticProvider.getUnavailableReason(),
                    "配置经评测集校准的 BGE-M3 提供者后重新分析；未配置时不伪造 0%",
                    null, 400));
            return;
        }
        items.add(rule("KW_SEMANTIC_PROVIDER", "SEMANTIC", "INFO", "GOOD",
                BigDecimal.valueOf(semanticPercent), BigDecimal.valueOf(100), null,
                Map.of("modelVersion", semanticProvider.getModelVersion()),
                "语义相似度已由当前校准模型完成", "", null, 400));
    }

    private int scoreDistribution(String keyword, String normalizedKeyword, Set<String> variants,
                                  SeoContentSnapshot snapshot, int exactMatchCount,
                                  List<SeoKeywordRuleResult> items) {
        Map<String, List<String>> locations = snapshot.locationTexts();
        int hitRegions = 0;
        for (List<String> values : locations.values()) {
            String text = String.join(" ", values);
            if (containsKeywordOrVariant(text, normalizedKeyword, variants)) {
                hitRegions++;
            }
        }
        int targetRegions = Math.min(3, Math.max(1, locations.size()));
        int score = exactMatchCount == 0 && hitRegions == 0 ? 0
                : normalizer.compact(snapshot.visibleText()).length() < 80
                ? (hitRegions >= 2 ? 100 : 85)
                : Math.min(100, round(hitRegions * 100.0 / targetRegions));
        String bodyText = bodyText(snapshot);
        int bodyExactMatchCount = normalizer.countPhrase(bodyText, normalizedKeyword);
        int bodyLength = Math.max(1, normalizer.compact(bodyText).length());
        double density = bodyExactMatchCount * Math.max(1, normalizer.compact(normalizedKeyword).length()) * 100.0
                / bodyLength;
        int penalty = bodyExactMatchCount >= 4 && density > 5.0 ? density > 9.0 ? 60 : 35 : 0;
        score = clamp(score - penalty);

        items.add(rule("KW_DISTRIBUTION_REGIONS", "DISTRIBUTION", hitRegions >= 2 ? "INFO" : "MEDIUM",
                hitRegions > 0 ? "GOOD" : "ISSUE", BigDecimal.valueOf(score + penalty), BigDecimal.valueOf(100),
                "BODY", Map.of("matchedRegionCount", hitRegions, "availableRegionCount", locations.size()),
                hitRegions > 0 ? "关键词或变体分布在 " + hitRegions + " 个可见内容区域"
                        : "关键词未出现在任何可见内容区域",
                hitRegions > 0 ? "" : "先确认该关键词确实符合商品，再在标题或简介中补充一次事实性描述",
                null, 500));
        if (penalty > 0) {
            items.add(rule("KW_DISTRIBUTION_STUFFING", "DISTRIBUTION", "HIGH", "ISSUE",
                    BigDecimal.valueOf(100 - penalty), BigDecimal.valueOf(100), "BODY",
                    Map.of("bodyExactMatchCount", bodyExactMatchCount,
                            "totalExactMatchCount", exactMatchCount,
                            "estimatedDensityPercent", BigDecimal.valueOf(density).setScale(2, RoundingMode.HALF_UP)),
                    "关键词在正文区域的重复次数与正文长度不匹配，存在堆砌风险",
                    "删除重复句式，只保留能帮助用户理解材质、尺寸、用途或保养信息的必要表达",
                    null, 510));
        }
        return score;
    }

    private String bodyText(SeoContentSnapshot snapshot) {
        List<String> parts = new ArrayList<>();
        if (snapshot.getBody() != null && !snapshot.getBody().isBlank()) {
            parts.add(snapshot.getBody());
        }
        if (snapshot.getParagraphs() != null) {
            snapshot.getParagraphs().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .forEach(parts::add);
        }
        return String.join("\n", parts);
    }

    private int scoreIntent(String keyword, String normalizedKeyword, String visibleText,
                            List<SeoKeywordRuleResult> items) {
        Map<String, Set<String>> expectedFacets = dictionary.expectedFacets(normalizedKeyword);
        if (expectedFacets.isEmpty()) {
            List<String> tokens = normalizer.tokens(normalizedKeyword);
            List<String> contentTokens = normalizer.tokens(visibleText);
            long matched = tokens.stream().filter(contentTokens::contains).count();
            int percent = tokens.isEmpty() ? 0 : round(matched * 100.0 / tokens.size());
            items.add(rule("KW_INTENT_GENERIC_TOPIC", "INTENT", percent >= 60 ? "INFO" : "MEDIUM",
                    percent >= 60 ? "GOOD" : "ISSUE", BigDecimal.valueOf(percent), BigDecimal.valueOf(100),
                    "BODY", Map.of("matchedTokenCount", matched, "expectedTokenCount", tokens.size()),
                    percent >= 60 ? "内容已覆盖该关键词的主要词项"
                            : "内容对该关键词的主题覆盖不足",
                    percent >= 60 ? "" : "补充能够直接回答该关键词用户问题的商品事实；如果无法补充，请更换关键词",
                    null, 600));
            return percent;
        }

        int covered = 0;
        int sort = 610;
        for (Map.Entry<String, Set<String>> facet : expectedFacets.entrySet()) {
            String matchedTerm = facet.getValue().stream()
                    .filter(term -> normalizer.containsPhrase(visibleText, term))
                    .findFirst().orElse(null);
            boolean present = matchedTerm != null;
            if (present) {
                covered++;
            }
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("facet", facet.getKey());
            evidence.put("expectedTerms", facet.getValue());
            if (matchedTerm != null) {
                evidence.put("matchedTerm", matchedTerm);
            }
            items.add(rule("KW_INTENT_" + facet.getKey(), "INTENT", present ? "INFO" : "MEDIUM",
                    present ? "GOOD" : "ISSUE", BigDecimal.valueOf(present ? 1 : 0), BigDecimal.ONE,
                    "BODY", evidence,
                    present ? "内容已覆盖关键词中的 " + facet.getKey() + " 意图"
                            : "关键词包含 " + facet.getKey() + " 意图，但可见内容没有对应证据",
                    present ? "" : "仅当商品真实具备该属性时，在商品属性或描述中明确说明；否则删除该关键词",
                    null, sort++));
        }
        return round(covered * 100.0 / expectedFacets.size());
    }

    private List<String> matchedLocations(SeoContentSnapshot snapshot, String keyword, Set<String> variants) {
        Set<String> result = new LinkedHashSet<>();
        snapshot.locationTexts().forEach((location, values) -> {
            if (values.stream().anyMatch(value -> containsKeywordOrVariant(value, keyword, variants))) {
                result.add(location);
            }
        });
        return List.copyOf(result);
    }

    private boolean containsKeywordOrVariant(String text, String keyword, Set<String> variants) {
        return normalizer.containsPhrase(text, keyword)
                || variants.stream().anyMatch(variant -> normalizer.containsPhrase(text, variant));
    }

    private SeoKeywordRuleResult rule(String ruleCode, String dimension, String severity, String status,
                                      BigDecimal score, BigDecimal maxScore, String location,
                                      Map<String, Object> evidence, String reason, String recommendation,
                                      BigDecimal recoverableScore, int sort) {
        return SeoKeywordRuleResult.builder()
                .ruleCode(ruleCode)
                .dimension(dimension)
                .severity(severity)
                .status(status)
                .score(score)
                .maxScore(maxScore)
                .contentLocation(location)
                .evidence(evidence)
                .reason(reason)
                .recommendation(recommendation)
                .recoverableScore(recoverableScore)
                .sort(sort)
                .build();
    }

    private String snippet(String value) {
        String clean = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return clean.length() <= 160 ? clean : clean.substring(0, 157) + "...";
    }

    private static int round(double value) {
        return BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static Map<String, Integer> createPositionWeights() {
        Map<String, Integer> weights = new LinkedHashMap<>();
        weights.put("SEO_TITLE", 25);
        weights.put("H1", 20);
        weights.put("INTRODUCTION", 15);
        weights.put("META_DESCRIPTION", 15);
        weights.put("HEADING", 10);
        weights.put("SLUG", 10);
        weights.put("IMAGE_ALT", 5);
        return weights;
    }

}
