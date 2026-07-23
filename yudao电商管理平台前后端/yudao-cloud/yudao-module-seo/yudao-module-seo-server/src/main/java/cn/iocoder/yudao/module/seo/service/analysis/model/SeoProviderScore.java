package cn.iocoder.yudao.module.seo.service.analysis.model;

import lombok.Builder;
import lombok.Value;

import java.util.LinkedHashMap;
import java.util.Map;

@Value
@Builder
public class SeoProviderScore {

    Integer percent;
    String version;
    String reason;
    @Builder.Default
    Map<String, Object> evidence = Map.of();

    public boolean isAvailable() {
        return percent != null;
    }

    public static SeoProviderScore available(int percent, String version, String reason,
                                             Map<String, Object> evidence) {
        return SeoProviderScore.builder()
                .percent(Math.max(0, Math.min(100, percent)))
                .version(version)
                .reason(reason)
                .evidence(evidence == null ? Map.of() : new LinkedHashMap<>(evidence))
                .build();
    }

    public static SeoProviderScore unavailable(String version, String reason,
                                               Map<String, Object> evidence) {
        return SeoProviderScore.builder()
                .version(version)
                .reason(reason)
                .evidence(evidence == null ? Map.of() : new LinkedHashMap<>(evidence))
                .build();
    }

}
