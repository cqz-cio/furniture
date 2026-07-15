package cn.iocoder.yudao.module.product.framework.rpc.config;

import cn.iocoder.yudao.module.erp.api.integration.MallErpProductApi;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.EnableFeignClients;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RpcConfigurationTest {

    @Test
    void shouldRegisterMallErpProductApiAsFeignClient() {
        EnableFeignClients annotation = RpcConfiguration.class.getAnnotation(EnableFeignClients.class);

        assertTrue(Arrays.asList(annotation.clients()).contains(MallErpProductApi.class));
    }

}
