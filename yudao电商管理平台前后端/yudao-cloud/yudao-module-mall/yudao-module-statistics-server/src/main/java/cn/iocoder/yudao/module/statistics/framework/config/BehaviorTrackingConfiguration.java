package cn.iocoder.yudao.module.statistics.framework.config;

import cn.iocoder.yudao.module.statistics.service.dashboard.TenantBehaviorHmacKeyProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Configuration
@EnableConfigurationProperties(BehaviorTrackingProperties.class)
public class BehaviorTrackingConfiguration {
    @Bean
    public TenantBehaviorHmacKeyProvider tenantBehaviorHmacKeyProvider(BehaviorTrackingProperties properties,
                                                                       Environment environment) {
        return (tenantId, version) -> {
            BehaviorTrackingProperties.TenantHmac tenant = properties.getHmacTenants().get(String.valueOf(tenantId));
            if (tenant == null || !versionEquals(version, tenant)) throw new IllegalStateException("HMAC version is not configured");
            String reference = version == tenant.getActiveVersion() ? tenant.getActiveKeyRef() : tenant.getPreviousKeyRef();
            if (reference == null || reference.trim().isEmpty()) throw new IllegalStateException("HMAC key reference is missing");
            String secret = environment.getProperty(reference);
            if (secret == null || secret.length() < 16) throw new IllegalStateException("HMAC key reference cannot be resolved");
            try {
                return MessageDigest.getInstance("SHA-256").digest((tenantId + ":" + version + ":" + secret)
                        .getBytes(StandardCharsets.UTF_8));
            } catch (Exception ex) { throw new IllegalStateException("Unable to derive HMAC key", ex); }
        };
    }
    private boolean versionEquals(int version, BehaviorTrackingProperties.TenantHmac tenant) {
        return tenant.getActiveVersion() != null && tenant.getActiveVersion() == version
                || tenant.getPreviousVersion() != null && tenant.getPreviousVersion() == version;
    }
}
