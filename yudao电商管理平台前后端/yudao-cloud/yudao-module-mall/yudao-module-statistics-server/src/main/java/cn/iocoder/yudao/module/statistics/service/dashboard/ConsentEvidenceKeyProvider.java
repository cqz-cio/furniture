package cn.iocoder.yudao.module.statistics.service.dashboard;

public interface ConsentEvidenceKeyProvider {
    byte[] key(Long tenantId);
}
