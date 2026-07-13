package cn.iocoder.yudao.module.statistics.service.dashboard;

import java.time.Instant;

public interface ConsentEvidenceVerifier {
    boolean verify(Long tenantId, String evidence, Instant now);
}
