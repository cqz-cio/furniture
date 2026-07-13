package cn.iocoder.yudao.module.statistics.service.dashboard;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

public class HmacConsentEvidenceVerifier implements ConsentEvidenceVerifier {
    private final ConsentEvidenceKeyProvider keyProvider;
    private final long maximumLifetimeSeconds;

    public HmacConsentEvidenceVerifier(ConsentEvidenceKeyProvider keyProvider, long maximumLifetimeSeconds) {
        this.keyProvider = keyProvider;
        this.maximumLifetimeSeconds = maximumLifetimeSeconds;
    }

    @Override
    public boolean verify(Long tenantId, String evidence, Instant now) {
        if (tenantId == null || evidence == null || evidence.length() > 512) return false;
        String[] parts = evidence.split("\\.", -1);
        if (parts.length != 5 || !"v1".equals(parts[0]) || !parts[3].matches("[A-Za-z0-9_-]{8,128}")) return false;
        try {
            long issued = Long.parseLong(parts[1]), expires = Long.parseLong(parts[2]);
            long current = now.getEpochSecond();
            if (issued > current + 300 || expires < current || expires <= issued || expires - issued > maximumLifetimeSeconds) return false;
            String payload = tenantId + "|v1|" + issued + "|" + expires + "|" + parts[3];
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyProvider.key(tenantId), "HmacSHA256"));
            byte[] expected = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            byte[] actual = Base64.getUrlDecoder().decode(parts[4]);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException exception) {
            return false;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to verify analytics consent evidence", exception);
        }
    }
}
