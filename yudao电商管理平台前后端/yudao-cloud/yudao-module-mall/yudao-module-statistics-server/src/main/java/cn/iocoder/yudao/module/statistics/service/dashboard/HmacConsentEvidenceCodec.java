package cn.iocoder.yudao.module.statistics.service.dashboard;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

public class HmacConsentEvidenceCodec {
    private static final String VERSION = "v1";
    private static final String NONCE_PATTERN = "[A-Za-z0-9_-]{8,128}";

    private final ConsentEvidenceKeyProvider keyProvider;
    private final long maximumLifetimeSeconds;

    public HmacConsentEvidenceCodec(ConsentEvidenceKeyProvider keyProvider,
                                    long maximumLifetimeSeconds) {
        this.keyProvider = keyProvider;
        this.maximumLifetimeSeconds = maximumLifetimeSeconds;
    }

    public String issue(Long tenantId, String nonce, Instant issuedAt, Instant expiresAt) {
        if (tenantId == null || nonce == null || !nonce.matches(NONCE_PATTERN)
                || issuedAt == null || expiresAt == null) {
            throw new IllegalArgumentException("invalid consent evidence claims");
        }
        long issued = issuedAt.getEpochSecond();
        long expires = expiresAt.getEpochSecond();
        if (expires <= issued || expires - issued > maximumLifetimeSeconds) {
            throw new IllegalArgumentException("invalid consent evidence lifetime");
        }
        String payload = payload(tenantId, issued, expires, nonce);
        return VERSION + "." + issued + "." + expires + "." + nonce + "." + sign(tenantId, payload);
    }

    public ConsentEvidenceClaims verifyAndDecode(Long tenantId, String evidence, Instant now) {
        if (tenantId == null || evidence == null || evidence.length() > 512 || now == null) return null;
        String[] parts = evidence.split("\\.", -1);
        if (parts.length != 5 || !VERSION.equals(parts[0]) || !parts[3].matches(NONCE_PATTERN)) return null;
        try {
            long issued = Long.parseLong(parts[1]);
            long expires = Long.parseLong(parts[2]);
            long current = now.getEpochSecond();
            if (issued > current + 300 || expires < current || expires <= issued
                    || expires - issued > maximumLifetimeSeconds) return null;
            String expected = sign(tenantId, payload(tenantId, issued, expires, parts[3]));
            if (!MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII),
                    parts[4].getBytes(StandardCharsets.US_ASCII))) return null;
            return new ConsentEvidenceClaims(parts[3], Instant.ofEpochSecond(issued), Instant.ofEpochSecond(expires));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String payload(Long tenantId, long issued, long expires, String nonce) {
        return tenantId + "|" + VERSION + "|" + issued + "|" + expires + "|" + nonce;
    }

    private String sign(Long tenantId, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyProvider.key(tenantId), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign analytics consent evidence", exception);
        }
    }
}
