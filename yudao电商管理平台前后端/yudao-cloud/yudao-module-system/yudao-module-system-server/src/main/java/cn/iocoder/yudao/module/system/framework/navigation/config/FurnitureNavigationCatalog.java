package cn.iocoder.yudao.module.system.framework.navigation.config;

import cn.iocoder.yudao.module.system.enums.tenant.TenantBusinessModeEnum;
import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 家具后台导航目录。
 *
 * <p>完整目录从 {@code navigation/furniture-lite-menu-paths.json} 加载，B2B 目录是其受控子集。
 * 两者共同供前端导航过滤和后端租户套餐权限同步使用。</p>
 */
@Getter
public class FurnitureNavigationCatalog {

    private final Set<String> menuPaths;
    private final Set<String> b2bMenuPaths;

    public FurnitureNavigationCatalog(Set<String> menuPaths) {
        this(menuPaths, menuPaths);
    }

    public FurnitureNavigationCatalog(Set<String> menuPaths, Set<String> b2bMenuPaths) {
        this.menuPaths = immutableCopy(menuPaths);
        this.b2bMenuPaths = immutableCopy(b2bMenuPaths);
        if (!this.menuPaths.containsAll(this.b2bMenuPaths)) {
            throw new IllegalArgumentException("B2B 导航目录必须是完整家具导航目录的子集");
        }
    }

    public Set<String> getMenuPaths(String businessMode) {
        return TenantBusinessModeEnum.B2B.getCode().equals(businessMode) ? b2bMenuPaths : menuPaths;
    }

    private static Set<String> immutableCopy(Set<String> source) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(source));
    }

}
