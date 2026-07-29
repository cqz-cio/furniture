package cn.iocoder.yudao.module.erp.framework.rpc.config;

import cn.iocoder.yudao.module.system.api.tenant.TenantApi;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.EnableFeignClients;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RpcConfigurationTest {

    @Test
    void shouldRegisterTenantApiAsFeignClient() {
        EnableFeignClients annotation = RpcConfiguration.class.getAnnotation(EnableFeignClients.class);

        assertNotNull(annotation);
        assertTrue(Arrays.asList(annotation.clients()).contains(TenantApi.class));
    }

}
