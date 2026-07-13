package cn.iocoder.yudao.module.statistics.service.dashboard;

/** Resolves secret material scoped to one tenant and key version. */
@FunctionalInterface
public interface TenantBehaviorHmacKeyProvider {
    byte[] keyForTenantVersion(Long tenantId, int version);
}
