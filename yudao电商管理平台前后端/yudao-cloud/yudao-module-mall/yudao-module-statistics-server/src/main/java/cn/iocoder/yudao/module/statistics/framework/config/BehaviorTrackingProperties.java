package cn.iocoder.yudao.module.statistics.framework.config;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.*;
@Data @ConfigurationProperties(prefix="yudao.statistics.behavior")
public class BehaviorTrackingProperties {
    private boolean enabled;
    private boolean consentRequired = true;
    private List<Long> enabledTenantIds = Arrays.asList(121L, 162L);
    private int perIpPerMinute = 120;
    private int perVisitorPerMinute = 120;
    private int perTenantPerMinute = 6000;
    private int globalPerMinute = 30000;
    private int consentPerIpPerMinute = 30;
    private int consentEvidenceLifetimeDays = 180;
    private int consentRecordRetentionDays = 730;
    private String consentPolicyVersion = "2026-08-06";
    private Map<String, TenantHmac> hmacTenants = new HashMap<>();
    @Data public static class TenantHmac { private Integer activeVersion; private String activeKeyRef; private String activatesAt; private Integer previousVersion; private String previousKeyRef; private String consentEvidenceKeyRef; }
}
