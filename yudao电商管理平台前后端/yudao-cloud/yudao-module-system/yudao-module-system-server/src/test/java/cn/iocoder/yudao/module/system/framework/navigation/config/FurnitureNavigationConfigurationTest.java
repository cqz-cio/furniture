package cn.iocoder.yudao.module.system.framework.navigation.config;

import cn.iocoder.yudao.module.system.enums.tenant.TenantBusinessModeEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class FurnitureNavigationConfigurationTest {

    @Test
    void shouldRegisterWebsiteNavigationInFullAndB2bCatalogs() throws IOException {
        FurnitureNavigationCatalog catalog = new FurnitureNavigationConfiguration()
                .furnitureNavigationCatalog(new ObjectMapper());

        assertThat(catalog.getMenuPaths()).contains("/seo/navigation");
        assertThat(catalog.getMenuPaths(TenantBusinessModeEnum.B2B.getCode()))
                .contains("/seo/navigation");
    }

}
