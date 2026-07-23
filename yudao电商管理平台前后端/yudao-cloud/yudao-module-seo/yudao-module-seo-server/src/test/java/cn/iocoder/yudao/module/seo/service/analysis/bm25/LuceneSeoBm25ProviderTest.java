package cn.iocoder.yudao.module.seo.service.analysis.bm25;

import cn.iocoder.yudao.module.seo.config.SeoAnalysisProperties;
import cn.iocoder.yudao.module.seo.service.analysis.model.SeoAnalysisContext;
import cn.iocoder.yudao.module.seo.service.analysis.model.SeoContentSnapshot;
import cn.iocoder.yudao.module.seo.service.analysis.model.SeoProviderScore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LuceneSeoBm25ProviderTest {

    private final Path indexPath = Path.of("target", "test-bm25", UUID.randomUUID().toString())
            .toAbsolutePath();

    @AfterEach
    void cleanUp() throws IOException {
        FileSystemUtils.deleteRecursively(indexPath);
    }

    @Test
    void calculate_shouldWaitForMinimumCorpusThenReturnComparablePercent() {
        SeoAnalysisProperties.Bm25 properties = new SeoAnalysisProperties.Bm25();
        properties.setIndexPath(indexPath.toString());
        properties.setMinimumCorpusSize(2);

        try (LuceneSeoBm25Provider provider = new LuceneSeoBm25Provider(properties)) {
            SeoAnalysisContext currentContext = context(101L);
            SeoContentSnapshot current = snapshot("北欧实木餐桌，橡木桌面适合家庭餐厅。");
            provider.index(currentContext, current);

            SeoProviderScore insufficient = provider.calculate("实木餐桌", currentContext, current);
            assertThat(insufficient.isAvailable()).isFalse();
            assertThat(insufficient.getEvidence()).containsEntry("corpusSize", 1);

            SeoAnalysisContext otherContext = context(102L);
            provider.index(otherContext, snapshot("现代布艺沙发，适合客厅休息。"));
            SeoProviderScore result = provider.calculate("实木餐桌", currentContext, current);

            assertThat(result.isAvailable()).isTrue();
            assertThat(result.getPercent()).isBetween(1, 100);
            assertThat(result.getVersion()).isEqualTo(LuceneSeoBm25Provider.VERSION);
            assertThat(result.getEvidence()).containsEntry("corpusSize", 2);
        }
    }

    @Test
    void calculate_shouldKeepTenantIndexesIsolated() {
        SeoAnalysisProperties.Bm25 properties = new SeoAnalysisProperties.Bm25();
        properties.setIndexPath(indexPath.toString());
        properties.setMinimumCorpusSize(1);

        try (LuceneSeoBm25Provider provider = new LuceneSeoBm25Provider(properties)) {
            SeoAnalysisContext tenantOne = context(1L, 101L);
            provider.index(tenantOne, snapshot("北欧实木餐桌"));

            SeoAnalysisContext tenantTwo = context(2L, 101L);
            SeoProviderScore tenantTwoResult = provider.calculate(
                    "实木餐桌", tenantTwo, snapshot("北欧实木餐桌"));

            assertThat(tenantTwoResult.isAvailable()).isFalse();
            assertThat(tenantTwoResult.getEvidence()).containsEntry("corpusSize", 0);
        }
    }

    private static SeoAnalysisContext context(Long entityId) {
        return context(1L, entityId);
    }

    private static SeoAnalysisContext context(Long tenantId, Long entityId) {
        return SeoAnalysisContext.builder()
                .tenantId(tenantId)
                .siteId(1L)
                .entityType("PRODUCT")
                .entityId(entityId)
                .locale("zh-CN")
                .sourceType("ENTITY")
                .build();
    }

    private static SeoContentSnapshot snapshot(String body) {
        SeoContentSnapshot snapshot = new SeoContentSnapshot();
        snapshot.setBody(body);
        return snapshot;
    }

}
