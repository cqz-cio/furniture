package cn.iocoder.yudao.module.seo.service.analysis.semantic;

import cn.iocoder.yudao.module.seo.service.analysis.model.SeoAnalysisContext;
import cn.iocoder.yudao.module.seo.service.analysis.model.SeoContentSnapshot;
import cn.iocoder.yudao.module.seo.service.analysis.model.SeoProviderScore;

public interface SeoSemanticSimilarityProvider {

    SeoProviderScore calculate(String keyword, SeoAnalysisContext context, SeoContentSnapshot snapshot);

    String getModelVersion();

    String getUnavailableReason();

}
