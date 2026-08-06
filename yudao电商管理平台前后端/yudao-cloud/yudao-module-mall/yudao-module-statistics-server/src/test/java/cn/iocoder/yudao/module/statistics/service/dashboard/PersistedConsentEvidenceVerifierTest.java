package cn.iocoder.yudao.module.statistics.service.dashboard;

import cn.iocoder.yudao.module.statistics.dal.mysql.dashboard.ConsentEvidenceMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class PersistedConsentEvidenceVerifierTest {

    @Test
    void requiresBothValidSignatureAndActiveDatabaseRecord() {
        byte[] key = "tenant-162-consent-test-key-32-bytes".getBytes(StandardCharsets.UTF_8);
        HmacConsentEvidenceCodec codec = new HmacConsentEvidenceCodec(tenantId -> key, 3600L);
        ConsentEvidenceMapper mapper = mock(ConsentEvidenceMapper.class);
        PersistedConsentEvidenceVerifier verifier = new PersistedConsentEvidenceVerifier(
                codec, mapper, "2026-08-06");
        Instant now = Instant.ofEpochSecond(1_800_000_000L);
        String evidence = codec.issue(162L, "consent_nonce_123", now.minusSeconds(1), now.plusSeconds(600));

        when(mapper.isActiveAnalyticsEvidence(
                162L, "consent_nonce_123", "2026-08-06", now.getEpochSecond()))
                .thenReturn(true, false);

        assertTrue(verifier.verify(162L, evidence, now));
        assertFalse(verifier.verify(162L, evidence, now));
        assertFalse(verifier.verify(162L, evidence + "x", now));
        verify(mapper, times(2))
                .isActiveAnalyticsEvidence(
                        162L, "consent_nonce_123", "2026-08-06", now.getEpochSecond());
        verifyNoMoreInteractions(mapper);
    }
}
