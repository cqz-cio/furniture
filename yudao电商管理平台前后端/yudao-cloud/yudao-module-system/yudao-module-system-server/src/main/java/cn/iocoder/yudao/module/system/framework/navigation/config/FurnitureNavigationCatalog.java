package cn.iocoder.yudao.module.system.framework.navigation.config;

import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 家具后台导航目录。
 *
 * <p>目录从 {@code navigation/furniture-lite-menu-paths.json} 加载，是前端导航过滤和
 * 后端租户套餐权限同步共同使用的唯一菜单路径来源。</p>
 */
@Getter
public class FurnitureNavigationCatalog {

    private final Set<String> menuPaths;

    public FurnitureNavigationCatalog(Set<String> menuPaths) {
        this.menuPaths = Collections.unmodifiableSet(new LinkedHashSet<>(menuPaths));
    }

}
