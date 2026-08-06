package cn.iocoder.yudao.module.statistics.service.dashboard;

import cn.iocoder.yudao.module.statistics.dal.mysql.dashboard.ConsentEvidenceMapper;

import java.time.Instant;

public class PersistedConsentEvidenceVerifier implements ConsentEvidenceVerifier {
    private final HmacConsentEvidenceCodec codec;
    private final ConsentEvidenceMapper mapper;
    private final String policyVersion;

    public PersistedConsentEvidenceVerifier(HmacConsentEvidenceCodec codec,
                                            ConsentEvidenceMapper mapper,
                                            String policyVersion) {
        this.codec = codec;
        this.mapper = mapper;
        this.policyVersion = policyVersion;
    }

    @Override
    public boolean verify(Long tenantId, String evidence, Instant now) {
        ConsentEvidenceClaims claims = codec.verifyAndDecode(tenantId, evidence, now);
        return claims != null && mapper.isActiveAnalyticsEvidence(
                tenantId, claims.nonce(), policyVersion, now.getEpochSecond());
    }
}
