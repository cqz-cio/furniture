package cn.iocoder.yudao.module.statistics.service.dashboard;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class HmacConsentEvidenceCodecTest {

    private static final byte[] KEY =
            "tenant-162-consent-test-key-32-bytes".getBytes(StandardCharsets.UTF_8);

    @Test
    void issuesTenantBoundEvidenceAndDecodesClaims() {
        HmacConsentEvidenceCodec codec = new HmacConsentEvidenceCodec(tenantId -> KEY, 180 * 86400L);
        Instant issuedAt = Instant.ofEpochSecond(1_800_000_000L);
        Instant expiresAt = issuedAt.plusSeconds(3600);

        String evidence = codec.issue(162L, "consent_nonce_123", issuedAt, expiresAt);
        ConsentEvidenceClaims claims = codec.verifyAndDecode(162L, evidence, issuedAt.plusSeconds(1));

        assertNotNull(claims);
        assertEquals("consent_nonce_123", claims.nonce());
        assertEquals(issuedAt, claims.issuedAt());
        assertEquals(expiresAt, claims.expiresAt());
        assertNull(codec.verifyAndDecode(121L, evidence, issuedAt.plusSeconds(1)));
        assertNull(codec.verifyAndDecode(162L, evidence + "tampered", issuedAt.plusSeconds(1)));
        assertNull(codec.verifyAndDecode(162L, evidence, expiresAt.plusSeconds(1)));
    }

    @Test
    void rejectsEvidenceLifetimeOutsideConfiguredBoundary() {
        HmacConsentEvidenceCodec codec = new HmacConsentEvidenceCodec(tenantId -> KEY, 60L);
        Instant issuedAt = Instant.ofEpochSecond(1_800_000_000L);
        assertThrows(IllegalArgumentException.class,
                () -> codec.issue(162L, "consent_nonce_123", issuedAt, issuedAt.plusSeconds(61)));
    }
}
