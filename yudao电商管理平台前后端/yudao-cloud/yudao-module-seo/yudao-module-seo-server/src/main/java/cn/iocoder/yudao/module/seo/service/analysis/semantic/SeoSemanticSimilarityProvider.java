package cn.iocoder.yudao.module.seo.service.analysis.semantic;

import cn.iocoder.yudao.module.seo.service.analysis.model.SeoContentSnapshot;

import java.util.OptionalInt;

public interface SeoSemanticSimilarityProvider {

    OptionalInt calculatePercent(String keyword, SeoContentSnapshot snapshot);

    String getModelVersion();

    String getUnavailableReason();

}
