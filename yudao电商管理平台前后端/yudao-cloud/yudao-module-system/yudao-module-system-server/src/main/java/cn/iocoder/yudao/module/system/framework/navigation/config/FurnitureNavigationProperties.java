package cn.iocoder.yudao.module.system.framework.navigation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 家具后台导航权限同步配置。
 */
@ConfigurationProperties(prefix = "yudao.furniture-navigation")
@Data
public class FurnitureNavigationProperties {

    /**
     * 是否启用家具导航与租户套餐的自动同步。
     */
    private boolean enabled;

    /**
     * 使用家具后台导航的租户编号。
     */
    private Set<Long> tenantIds = new LinkedHashSet<>();

}
