package cn.iocoder.yudao.module.statistics.service.dashboard;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

@Component
public class BehaviorIdentityHasher {

    private final TenantBehaviorHmacKeyProvider keyProvider;

    public BehaviorIdentityHasher(TenantBehaviorHmacKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    public String hash(Long tenantId, int version, String rawId) throws GeneralSecurityException {
        if (tenantId == null || rawId == null || rawId.trim().isEmpty()) {
            throw new IllegalArgumentException("tenant and raw identity are required");
        }
        byte[] keyBytes = keyProvider.keyForTenantVersion(tenantId, version);
        if (keyBytes == null || keyBytes.length < 16) {
            throw new IllegalStateException("HMAC key is unavailable");
        }
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
        byte[] digest = mac.doFinal((tenantId + ":" + rawId).getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hex.append(String.format("%02x", value & 0xff));
        }
        return hex.toString();
    }
}
