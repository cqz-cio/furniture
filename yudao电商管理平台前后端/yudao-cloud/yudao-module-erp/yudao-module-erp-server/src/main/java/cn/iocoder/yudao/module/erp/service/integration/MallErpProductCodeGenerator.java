package cn.iocoder.yudao.module.erp.service.integration;

import cn.iocoder.yudao.module.system.api.tenant.TenantApi;
import cn.iocoder.yudao.module.system.api.tenant.dto.TenantRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MallErpProductCodeGenerator {

    private final TenantApi tenantApi;

    public String generate(Long tenantId, Long mallSkuId) {
        if (tenantId == null || mallSkuId == null) {
            throw new IllegalArgumentException("tenantId and mallSkuId are required");
        }
        TenantRespDTO tenant = tenantApi.getTenant(tenantId).getCheckedData();
        if (tenant == null) {
            throw new IllegalStateException("Tenant does not exist: " + tenantId);
        }
        if (tenant.getCode() == null || tenant.getCode().isBlank()) {
            throw new IllegalStateException("Tenant SKU code is not configured: " + tenantId);
        }
        return tenant.getCode() + "-" + tenantId + "-" + mallSkuId;
    }

}
