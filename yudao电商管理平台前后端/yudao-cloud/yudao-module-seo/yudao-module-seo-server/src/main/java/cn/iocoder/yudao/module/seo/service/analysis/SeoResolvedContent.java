package cn.iocoder.yudao.module.seo.service.analysis;

import cn.iocoder.yudao.module.seo.service.analysis.model.SeoContentSnapshot;

import java.util.List;

public record SeoResolvedContent(SeoContentSnapshot snapshot, Long metadataId,
                                 String focusKeyphrase, List<String> relatedKeyphrases) {
}
