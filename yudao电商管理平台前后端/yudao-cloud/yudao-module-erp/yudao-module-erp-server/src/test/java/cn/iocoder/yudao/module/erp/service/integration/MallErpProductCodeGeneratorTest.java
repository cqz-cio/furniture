package cn.iocoder.yudao.module.erp.service.integration;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.system.api.tenant.TenantApi;
import cn.iocoder.yudao.module.system.api.tenant.dto.TenantRespDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MallErpProductCodeGeneratorTest {

    @InjectMocks
    private MallErpProductCodeGenerator generator;
    @Mock
    private TenantApi tenantApi;

    @Test
    void generateUsesStableTenantCode() {
        TenantRespDTO tenant = new TenantRespDTO();
        tenant.setId(162L);
        tenant.setName("Vanz家具");
        tenant.setCode("VANZ");
        when(tenantApi.getTenant(162L)).thenReturn(CommonResult.success(tenant));

        assertEquals("VANZ-162-78", generator.generate(162L, 78L));
    }

    @Test
    void generateRejectsTenantWithoutCode() {
        TenantRespDTO tenant = new TenantRespDTO();
        tenant.setId(162L);
        when(tenantApi.getTenant(162L)).thenReturn(CommonResult.success(tenant));

        assertThrows(IllegalStateException.class, () -> generator.generate(162L, 78L));
    }

}
