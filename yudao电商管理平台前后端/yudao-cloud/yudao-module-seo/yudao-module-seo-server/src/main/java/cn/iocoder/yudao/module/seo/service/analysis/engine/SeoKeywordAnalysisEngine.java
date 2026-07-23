package cn.iocoder.yudao.module.seo.service.analysis.engine;

import cn.iocoder.yudao.module.seo.service.analysis.model.SeoAnalysisContext;
import cn.iocoder.yudao.module.seo.service.analysis.model.SeoContentSnapshot;
import cn.iocoder.yudao.module.seo.service.analysis.model.SeoKeywordEvaluation;

public interface SeoKeywordAnalysisEngine {

    default void prepare(SeoAnalysisContext context, SeoContentSnapshot snapshot) {
        // Most engines do not require a preparation phase.
    }

    default SeoKeywordEvaluation analyze(String keyword, String keywordType, int sort,
                                         SeoContentSnapshot snapshot) {
        return analyze(keyword, keywordType, sort, snapshot, SeoAnalysisContext.empty());
    }

    SeoKeywordEvaluation analyze(String keyword, String keywordType, int sort,
                                 SeoContentSnapshot snapshot, SeoAnalysisContext context);

    String getEngineVersion();

    String getRuleProfileVersion();

}
