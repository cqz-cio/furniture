package cn.iocoder.yudao.module.seo.service.analysis.semantic;

import cn.iocoder.yudao.module.seo.config.SeoAnalysisProperties;
import cn.iocoder.yudao.module.seo.service.analysis.model.SeoAnalysisContext;
import cn.iocoder.yudao.module.seo.service.analysis.model.SeoContentSnapshot;
import cn.iocoder.yudao.module.seo.service.analysis.model.SeoProviderScore;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BgeM3SeoSemanticSimilarityProviderTest {

    @Test
    void calculate_shouldCalibrateCosineFromOpenAiCompatibleResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://bge.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SeoAnalysisProperties.Semantic properties = properties();
        BgeM3SeoSemanticSimilarityProvider provider = new BgeM3SeoSemanticSimilarityProvider(
                properties, builder.build());
        server.expect(once(), requestTo("http://bge.test/v1/embeddings"))
                .andRespond(withSuccess("""
                        {"data":[
                          {"index":0,"embedding":[1.0,0.0,0.0]},
                          {"index":1,"embedding":[0.8,0.6,0.0]}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        SeoProviderScore result = provider.calculate("实木餐桌", SeoAnalysisContext.empty(),
                snapshot("这是一款适合餐厅使用的橡木实木餐桌。"));

        assertThat(result.isAvailable()).isTrue();
        assertThat(result.getPercent()).isEqualTo(92);
        assertThat(result.getVersion()).isEqualTo("BAAI/bge-m3");
        assertThat(result.getEvidence()).containsEntry("provider", "OPENAI_COMPATIBLE_EMBEDDINGS");
        server.verify();
    }

    @Test
    void calculate_shouldReturnUnavailableInsteadOfThrowingOnProviderFailure() {
        RestClient restClient = RestClient.builder().baseUrl("http://127.0.0.1:1").build();
        BgeM3SeoSemanticSimilarityProvider provider = new BgeM3SeoSemanticSimilarityProvider(
                properties(), restClient);

        SeoProviderScore result = provider.calculate("实木餐桌", SeoAnalysisContext.empty(),
                snapshot("实木餐桌"));

        assertThat(result.isAvailable()).isFalse();
        assertThat(result.getReason()).contains("暂时不可用");
        assertThat(result.getEvidence()).containsKey("errorType");
    }

    private static SeoAnalysisProperties.Semantic properties() {
        SeoAnalysisProperties.Semantic properties = new SeoAnalysisProperties.Semantic();
        properties.setBaseUrl("http://bge.test");
        properties.setMinimumSimilarity(0.25D);
        properties.setMaximumSimilarity(0.85D);
        return properties;
    }

    private static SeoContentSnapshot snapshot(String body) {
        SeoContentSnapshot snapshot = new SeoContentSnapshot();
        snapshot.setBody(body);
        return snapshot;
    }

}
