package cn.iocoder.yudao.gateway.filter.statistics;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "yudao.gateway.behavior-tracking")
public class BehaviorTrackingGatewayProperties {
    private boolean enabled;
    private List<AllowedSite> allowedSites = new ArrayList<>();

    @Data
    public static class AllowedSite {
        private String host;
        private String origin;
        private Long tenantId;
    }
}
