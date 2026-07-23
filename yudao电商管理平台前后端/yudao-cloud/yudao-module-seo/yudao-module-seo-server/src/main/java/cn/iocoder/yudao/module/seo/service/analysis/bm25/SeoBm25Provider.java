package cn.iocoder.yudao.module.seo.service.analysis.bm25;

import cn.iocoder.yudao.module.seo.service.analysis.model.SeoAnalysisContext;
import cn.iocoder.yudao.module.seo.service.analysis.model.SeoContentSnapshot;
import cn.iocoder.yudao.module.seo.service.analysis.model.SeoProviderScore;

public interface SeoBm25Provider {

    void index(SeoAnalysisContext context, SeoContentSnapshot snapshot);

    SeoProviderScore calculate(String keyword, SeoAnalysisContext context, SeoContentSnapshot snapshot);

    String getVersion();

}
