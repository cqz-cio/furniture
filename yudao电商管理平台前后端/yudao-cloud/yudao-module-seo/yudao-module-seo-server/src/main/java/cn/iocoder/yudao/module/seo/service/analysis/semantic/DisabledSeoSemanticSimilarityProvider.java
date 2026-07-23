package cn.iocoder.yudao.module.seo.service.analysis.semantic;

import cn.iocoder.yudao.module.seo.service.analysis.model.SeoAnalysisContext;
import cn.iocoder.yudao.module.seo.service.analysis.model.SeoContentSnapshot;
import cn.iocoder.yudao.module.seo.service.analysis.model.SeoProviderScore;

public class DisabledSeoSemanticSimilarityProvider implements SeoSemanticSimilarityProvider {

    @Override
    public SeoProviderScore calculate(String keyword, SeoAnalysisContext context, SeoContentSnapshot snapshot) {
        return SeoProviderScore.unavailable(null, getUnavailableReason(),
                java.util.Map.of("provider", "DISABLED"));
    }

    @Override
    public String getModelVersion() {
        return null;
    }

    @Override
    public String getUnavailableReason() {
        return "语义模型尚未配置，当前结果按可用的确定性分项归一化计算";
    }

}
