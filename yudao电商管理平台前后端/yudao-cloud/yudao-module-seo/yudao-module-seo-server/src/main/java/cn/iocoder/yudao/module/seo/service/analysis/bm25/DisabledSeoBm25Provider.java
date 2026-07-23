package cn.iocoder.yudao.module.seo.service.analysis.bm25;

import cn.iocoder.yudao.module.seo.service.analysis.model.SeoAnalysisContext;
import cn.iocoder.yudao.module.seo.service.analysis.model.SeoContentSnapshot;
import cn.iocoder.yudao.module.seo.service.analysis.model.SeoProviderScore;

import java.util.Map;

public class DisabledSeoBm25Provider implements SeoBm25Provider {

    @Override
    public void index(SeoAnalysisContext context, SeoContentSnapshot snapshot) {
        // Disabled providers intentionally do not create local index files.
    }

    @Override
    public SeoProviderScore calculate(String keyword, SeoAnalysisContext context,
                                      SeoContentSnapshot snapshot) {
        return SeoProviderScore.unavailable(null,
                "Lucene/BM25 尚未启用，当前词法分数按精确词和行业变体的可用权重归一化",
                Map.of("provider", "DISABLED"));
    }

    @Override
    public String getVersion() {
        return null;
    }

}
