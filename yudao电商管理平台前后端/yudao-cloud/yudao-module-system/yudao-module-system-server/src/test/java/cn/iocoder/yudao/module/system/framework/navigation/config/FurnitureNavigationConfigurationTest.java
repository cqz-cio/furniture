package cn.iocoder.yudao.module.system.framework.navigation.config;

import cn.iocoder.yudao.module.system.enums.tenant.TenantBusinessModeEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class FurnitureNavigationConfigurationTest {

    @Test
    void shouldKeepPlatformAdminFullAndSeparateB2cFromB2bCatalogs() throws IOException {
        FurnitureNavigationCatalog catalog = new FurnitureNavigationConfiguration()
                .furnitureNavigationCatalog(new ObjectMapper());

        assertThat(catalog.getMenuPaths())
                .contains("/system/role", "/ai/chat", "/crm/clue", "/member/user", "/seo/navigation");

        assertThat(catalog.getMenuPaths(TenantBusinessModeEnum.B2C.getCode()))
                .contains("/mall/trade/order", "/mall/trade/after-sale", "/member/user",
                        "/pay/order", "/pay/refund", "/infra/file", "/seo/navigation", "/seo/analysis")
                .doesNotContain("/crm", "/crm/clue", "/member/trade-application", "/pay/app",
                        "/ai", "/system/role", "/infra/file-config");

        assertThat(catalog.getMenuPaths(TenantBusinessModeEnum.B2B.getCode()))
                .contains("/crm/clue", "/crm/customer", "/crm/contact", "/mall/product/spu",
                        "/infra/file", "/seo/navigation", "/seo/analysis")
                .doesNotContain("/mall/trade/order", "/member/user", "/pay/order", "/ai",
                        "/system/role", "/infra/file-config");
    }

}
