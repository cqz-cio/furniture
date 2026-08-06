package cn.iocoder.yudao.module.statistics.service.dashboard;

import java.time.Instant;

public class HmacConsentEvidenceVerifier implements ConsentEvidenceVerifier {
    private final HmacConsentEvidenceCodec codec;

    public HmacConsentEvidenceVerifier(ConsentEvidenceKeyProvider keyProvider, long maximumLifetimeSeconds) {
        this.codec = new HmacConsentEvidenceCodec(keyProvider, maximumLifetimeSeconds);
    }

    @Override
    public boolean verify(Long tenantId, String evidence, Instant now) {
        return codec.verifyAndDecode(tenantId, evidence, now) != null;
    }
}
