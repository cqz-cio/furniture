package cn.iocoder.yudao.module.seo.service.analysis.semantic;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.seo.config.SeoAnalysisProperties;
import cn.iocoder.yudao.module.seo.service.analysis.model.SeoAnalysisContext;
import cn.iocoder.yudao.module.seo.service.analysis.model.SeoContentSnapshot;
import cn.iocoder.yudao.module.seo.service.analysis.model.SeoProviderScore;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BGE-M3 semantic provider using the OpenAI-compatible embeddings contract.
 *
 * <p>The provider deliberately owns only transport and calibration. Model hosting can be
 * supplied later by vLLM, Xinference, Infinity, SiliconFlow, or another compatible service.</p>
 */
@Slf4j
public class BgeM3SeoSemanticSimilarityProvider implements SeoSemanticSimilarityProvider {

    private final SeoAnalysisProperties.Semantic properties;
    private final RestClient restClient;

    public BgeM3SeoSemanticSimilarityProvider(SeoAnalysisProperties.Semantic properties,
                                              RestClient.Builder restClientBuilder) {
        validate(properties);
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(toMillis(properties.getConnectTimeout()));
        requestFactory.setReadTimeout(toMillis(properties.getReadTimeout()));
        RestClient.Builder builder = restClientBuilder
                .baseUrl(stripTrailingSlash(properties.getBaseUrl()))
                .requestFactory(requestFactory);
        if (StrUtil.isNotBlank(properties.getApiKey())) {
            builder.defaultHeaders(headers -> headers.setBearerAuth(properties.getApiKey().trim()));
        }
        this.restClient = builder.build();
    }

    BgeM3SeoSemanticSimilarityProvider(SeoAnalysisProperties.Semantic properties,
                                       RestClient restClient) {
        validate(properties);
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public SeoProviderScore calculate(String keyword, SeoAnalysisContext context, SeoContentSnapshot snapshot) {
        if (snapshot == null || snapshot.isEmpty() || StrUtil.isBlank(keyword)) {
            return unavailable("关键词或待分析内容为空，无法计算语义相似度", Map.of());
        }
        String content = truncate(snapshot.visibleText(), properties.getMaxContentChars());
        try {
            EmbeddingResponse response = restClient.post()
                    .uri(normalizePath(properties.getEndpointPath()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", properties.getModel(),
                            "input", List.of(keyword.trim(), content),
                            "encoding_format", "float"))
                    .retrieve()
                    .body(EmbeddingResponse.class);
            List<EmbeddingData> data = response == null || response.getData() == null
                    ? List.of() : response.getData().stream()
                    .sorted(Comparator.comparingInt(EmbeddingData::getIndex))
                    .toList();
            if (data.size() < 2 || data.get(0).getEmbedding() == null || data.get(1).getEmbedding() == null) {
                return unavailable("BGE-M3 返回的向量数量不足", Map.of("embeddingCount", data.size()));
            }
            double cosine = cosine(data.get(0).getEmbedding(), data.get(1).getEmbedding());
            int percent = calibrate(cosine);
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("provider", "OPENAI_COMPATIBLE_EMBEDDINGS");
            evidence.put("model", getModelVersion());
            evidence.put("rawCosineSimilarity", decimal(cosine));
            evidence.put("calibrationMinimum", properties.getMinimumSimilarity());
            evidence.put("calibrationMaximum", properties.getMaximumSimilarity());
            evidence.put("contentCharacters", content.length());
            evidence.put("contentTruncated", content.length() < snapshot.visibleText().length());
            return SeoProviderScore.available(percent, getModelVersion(),
                    "已通过 BGE-M3 向量相似度并按当前阈值区间校准为百分比", evidence);
        } catch (RuntimeException ex) {
            log.warn("[calculate][model({}) BGE-M3 语义评分暂时不可用: {}]",
                    properties.getModel(), ex.getClass().getSimpleName());
            return unavailable("BGE-M3 服务暂时不可用，本次按其他可用分项归一化",
                    Map.of("errorType", ex.getClass().getSimpleName()));
        }
    }

    @Override
    public String getModelVersion() {
        return properties.getModel();
    }

    @Override
    public String getUnavailableReason() {
        return "BGE-M3 服务暂时不可用，本次按可用的确定性分项归一化计算";
    }

    private SeoProviderScore unavailable(String reason, Map<String, Object> evidence) {
        return SeoProviderScore.unavailable(getModelVersion(), reason, evidence);
    }

    private int calibrate(double cosine) {
        double normalized = (cosine - properties.getMinimumSimilarity())
                / (properties.getMaximumSimilarity() - properties.getMinimumSimilarity());
        return clamp((int) Math.round(normalized * 100D));
    }

    private static double cosine(List<Double> left, List<Double> right) {
        if (left.isEmpty() || left.size() != right.size()) {
            throw new IllegalArgumentException("embedding dimensions do not match");
        }
        double dot = 0D;
        double leftNorm = 0D;
        double rightNorm = 0D;
        for (int index = 0; index < left.size(); index++) {
            double leftValue = left.get(index);
            double rightValue = right.get(index);
            dot += leftValue * rightValue;
            leftNorm += leftValue * leftValue;
            rightNorm += rightValue * rightValue;
        }
        if (leftNorm == 0D || rightNorm == 0D) {
            return 0D;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    private static int toMillis(java.time.Duration duration) {
        long millis = duration == null ? 0L : duration.toMillis();
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, millis));
    }

    private static String truncate(String value, int maxCharacters) {
        int normalizedMaximum = Math.max(1, maxCharacters);
        return value.length() <= normalizedMaximum ? value : value.substring(0, normalizedMaximum);
    }

    private static String normalizePath(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    private static String stripTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static void validate(SeoAnalysisProperties.Semantic properties) {
        if (properties == null || StrUtil.isBlank(properties.getBaseUrl())
                || StrUtil.isBlank(properties.getModel())
                || StrUtil.isBlank(properties.getEndpointPath())) {
            throw new IllegalArgumentException("BGE-M3 baseUrl, model and endpointPath are required");
        }
        if (properties.getMaximumSimilarity() <= properties.getMinimumSimilarity()) {
            throw new IllegalArgumentException("semantic maximumSimilarity must be greater than minimumSimilarity");
        }
    }

    @Data
    public static class EmbeddingResponse {

        private List<EmbeddingData> data;

    }

    @Data
    public static class EmbeddingData {

        private int index;
        private List<Double> embedding;

    }

}
