package cn.iocoder.yudao.module.statistics.service.dashboard;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HmacConsentEvidenceVerifierTest {
    private static final byte[] KEY = "test-consent-key-with-32-bytes!!".getBytes(StandardCharsets.UTF_8);

    @Test void verifiesTenantBoundUnexpiredEvidenceAndRejectsTampering() throws Exception {
        Instant now = Instant.ofEpochSecond(1_800_000_000L);
        HmacConsentEvidenceVerifier verifier = new HmacConsentEvidenceVerifier(tenantId -> KEY, 180 * 86400L);
        String evidence = token(121L, now.minusSeconds(60).getEpochSecond(), now.plusSeconds(3600).getEpochSecond(), "cmp-abc1");
        assertTrue(verifier.verify(121L, evidence, now));
        assertFalse(verifier.verify(122L, evidence, now));
        assertFalse(verifier.verify(121L, evidence + "x", now));
        assertFalse(verifier.verify(121L, token(121L, now.minusSeconds(7200).getEpochSecond(), now.minusSeconds(1).getEpochSecond(), "cmp-old"), now));
    }

    private String token(Long tenantId, long issued, long expires, String nonce) throws Exception {
        String payload = tenantId + "|v1|" + issued + "|" + expires + "|" + nonce;
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(KEY, "HmacSHA256"));
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        return "v1." + issued + "." + expires + "." + nonce + "." + signature;
    }
}
