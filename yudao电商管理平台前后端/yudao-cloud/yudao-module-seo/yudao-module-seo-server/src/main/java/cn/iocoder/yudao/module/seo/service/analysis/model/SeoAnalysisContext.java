package cn.iocoder.yudao.module.seo.service.analysis.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SeoAnalysisContext {

    Long tenantId;
    Long siteId;
    String entityType;
    Long entityId;
    String locale;
    String sourceType;
    Long sourceId;

    public static SeoAnalysisContext empty() {
        return SeoAnalysisContext.builder().build();
    }

    public boolean isIndexable() {
        return tenantId != null && siteId != null && entityType != null && !entityType.isBlank()
                && entityId != null && locale != null && !locale.isBlank();
    }

    public String documentKey() {
        return entityType + ":" + entityId;
    }

}
