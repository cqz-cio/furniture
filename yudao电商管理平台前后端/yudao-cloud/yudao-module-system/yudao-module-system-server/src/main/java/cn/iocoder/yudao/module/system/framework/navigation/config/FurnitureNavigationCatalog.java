package cn.iocoder.yudao.module.system.framework.navigation.config;

import cn.iocoder.yudao.module.system.enums.tenant.TenantBusinessModeEnum;
import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 家具后台导航目录。
 *
 * <p>完整目录供平台开发管理员使用；B2C、B2B 各自使用独立的受控子集。
 * 三者共同供前端导航过滤和后端租户套餐权限同步使用。</p>
 */
@Getter
public class FurnitureNavigationCatalog {

    private final Set<String> menuPaths;
    private final Set<String> b2cMenuPaths;
    private final Set<String> b2bMenuPaths;

    public FurnitureNavigationCatalog(Set<String> menuPaths) {
        this(menuPaths, menuPaths, menuPaths);
    }

    public FurnitureNavigationCatalog(Set<String> menuPaths, Set<String> b2cMenuPaths,
                                      Set<String> b2bMenuPaths) {
        this.menuPaths = immutableCopy(menuPaths);
        this.b2cMenuPaths = immutableCopy(b2cMenuPaths);
        this.b2bMenuPaths = immutableCopy(b2bMenuPaths);
        if (!this.menuPaths.containsAll(this.b2cMenuPaths)) {
            throw new IllegalArgumentException("B2C 导航目录必须是完整家具导航目录的子集");
        }
        if (!this.menuPaths.containsAll(this.b2bMenuPaths)) {
            throw new IllegalArgumentException("B2B 导航目录必须是完整家具导航目录的子集");
        }
    }

    public Set<String> getMenuPaths(String businessMode) {
        return TenantBusinessModeEnum.B2B.getCode().equals(businessMode) ? b2bMenuPaths : b2cMenuPaths;
    }

    private static Set<String> immutableCopy(Set<String> source) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(source));
    }

}
