package cn.iocoder.yudao.module.seo.service.analysis.engine;

import cn.iocoder.yudao.module.seo.service.analysis.model.SeoContentSnapshot;
import cn.iocoder.yudao.module.seo.service.analysis.model.SeoKeywordEvaluation;

public interface SeoKeywordAnalysisEngine {

    SeoKeywordEvaluation analyze(String keyword, String keywordType, int sort, SeoContentSnapshot snapshot);

    String getEngineVersion();

    String getRuleProfileVersion();

}
