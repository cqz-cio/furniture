package cn.iocoder.yudao.module.system.service.permission;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.MenuDO;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantPackageDO;
import cn.iocoder.yudao.module.system.dal.mysql.permission.MenuMapper;
import cn.iocoder.yudao.module.system.dal.mysql.tenant.TenantMapper;
import cn.iocoder.yudao.module.system.dal.mysql.tenant.TenantPackageMapper;
import cn.iocoder.yudao.module.system.enums.permission.MenuTypeEnum;
import cn.iocoder.yudao.module.system.enums.tenant.TenantBusinessModeEnum;
import cn.iocoder.yudao.module.system.framework.navigation.config.FurnitureNavigationCatalog;
import cn.iocoder.yudao.module.system.framework.navigation.config.FurnitureNavigationProperties;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import static cn.iocoder.yudao.module.system.dal.dataobject.permission.MenuDO.ID_ROOT;

/**
 * 家具后台导航与租户权限同步 Service 实现。
 */
@Service
@Slf4j
public class FurnitureNavigationPermissionServiceImpl implements FurnitureNavigationPermissionService {

    private static final List<String> MANAGED_NAVIGATION_ROOTS = List.of(
            "/mall", "/member", "/pay", "/crm", "/seo", "/dashboard", "/ai", "/infra", "/system");

    @Resource
    private FurnitureNavigationProperties properties;
    @Resource
    private FurnitureNavigationCatalog catalog;
    @Resource
    private MenuMapper menuMapper;
    @Resource
    private TenantMapper tenantMapper;
    @Resource
    private TenantPackageMapper tenantPackageMapper;
    @Resource
    private TenantService tenantService;

    @Override
    @DSTransactional
    public void syncMenuPermissions() {
        if (!properties.isEnabled() || CollUtil.isEmpty(properties.getTenantIds())) {
            return;
        }

        List<TenantDO> targetTenants = getExistingTargetTenants();
        if (targetTenants.isEmpty()) {
            log.info("[syncMenuPermissions][未找到家具导航目标租户({})，跳过同步]", properties.getTenantIds());
            return;
        }

        List<MenuDO> menus = menuMapper.selectList();
        Set<Long> defaultDesiredMenuIds = resolveDesiredMenuIds(menus, catalog.getMenuPaths());
        if (defaultDesiredMenuIds.isEmpty()) {
            throw new IllegalStateException("家具导航目录没有匹配到任何可用系统菜单，拒绝清空租户套餐权限");
        }
        Set<Long> managedNavigationMenuIds = resolveManagedNavigationMenuIds(menus);

        Map<Long, List<TenantDO>> tenantsByPackageId = new HashMap<>();
        for (TenantDO tenant : targetTenants) {
            if (tenant.getPackageId() == null || Objects.equals(tenant.getPackageId(), TenantDO.PACKAGE_ID_SYSTEM)) {
                throw new IllegalStateException("家具导航目标租户 " + tenant.getId() + " 未配置独立租户套餐");
            }
            tenantsByPackageId.computeIfAbsent(tenant.getPackageId(), key -> new ArrayList<>()).add(tenant);
        }

        for (Map.Entry<Long, List<TenantDO>> entry : tenantsByPackageId.entrySet()) {
            Long packageId = entry.getKey();
            validatePackageOwnership(packageId);
            Set<String> businessModes = entry.getValue().stream()
                    .map(TenantDO::getBusinessMode)
                    .map(mode -> StrUtil.blankToDefault(mode, TenantBusinessModeEnum.B2C.getCode()))
                    .collect(java.util.stream.Collectors.toSet());
            if (businessModes.size() != 1) {
                throw new IllegalStateException(
                        "家具导航目标套餐 " + packageId + " 被不同业务模式租户共用，请先拆分套餐");
            }
            String businessMode = businessModes.iterator().next();
            Set<Long> desiredMenuIds = resolveDesiredMenuIds(menus, catalog.getMenuPaths(businessMode));
            if (desiredMenuIds.isEmpty()) {
                throw new IllegalStateException(
                        "家具导航目标套餐 " + packageId + " 的业务模式 " + businessMode
                                + " 没有匹配到任何可用菜单，拒绝修改套餐权限");
            }
            TenantPackageDO tenantPackage = tenantPackageMapper.selectById(packageId);
            if (tenantPackage == null) {
                throw new IllegalStateException("家具导航目标套餐不存在: " + packageId);
            }

            Set<Long> synchronizedMenuIds = new HashSet<>(CollUtil.emptyIfNull(tenantPackage.getMenuIds()));
            // 对 B2C、B2B 都先清理受控导航，再写入各自目录，避免历史套餐残留 CRM、系统配置等越权入口。
            synchronizedMenuIds.removeAll(managedNavigationMenuIds);
            synchronizedMenuIds.addAll(desiredMenuIds);
            synchronizedMenuIds = sortedSet(synchronizedMenuIds);
            if (!Objects.equals(tenantPackage.getMenuIds(), synchronizedMenuIds)) {
                TenantPackageDO updateObj = new TenantPackageDO();
                updateObj.setId(packageId);
                updateObj.setMenuIds(synchronizedMenuIds);
                tenantPackageMapper.updateById(updateObj);
            }
            for (TenantDO tenant : entry.getValue()) {
                tenantService.updateTenantRoleMenu(tenant.getId(), synchronizedMenuIds);
            }
            log.info("[syncMenuPermissions][套餐({}) 已同步 {} 个家具导航菜单，套餐菜单总数 {}，租户数 {}]",
                    packageId, desiredMenuIds.size(), synchronizedMenuIds.size(), entry.getValue().size());
        }
    }

    private List<TenantDO> getExistingTargetTenants() {
        List<TenantDO> tenants = new ArrayList<>();
        for (Long tenantId : properties.getTenantIds()) {
            TenantDO tenant = tenantMapper.selectById(tenantId);
            if (tenant != null) {
                tenants.add(tenant);
            }
        }
        return tenants;
    }

    private void validatePackageOwnership(Long packageId) {
        List<TenantDO> packageTenants = tenantMapper.selectListByPackageId(packageId);
        List<Long> unrelatedTenantIds = packageTenants.stream()
                .map(TenantDO::getId)
                .filter(tenantId -> !properties.getTenantIds().contains(tenantId))
                .toList();
        if (CollUtil.isNotEmpty(unrelatedTenantIds)) {
            throw new IllegalStateException(
                    "家具导航目标套餐 " + packageId + " 仍被非目标租户 " + unrelatedTenantIds + " 共用，拒绝扩大权限");
        }
    }

    Set<Long> resolveDesiredMenuIds(List<MenuDO> menus) {
        return resolveDesiredMenuIds(menus, catalog.getMenuPaths());
    }

    Set<Long> resolveDesiredMenuIds(List<MenuDO> menus, Set<String> allowedMenuPaths) {
        Map<Long, MenuDO> menuMap = new HashMap<>();
        for (MenuDO menu : menus) {
            menuMap.put(menu.getId(), menu);
        }

        Map<Long, String> pathCache = new HashMap<>();
        Map<Long, Boolean> enabledCache = new HashMap<>();
        Set<Long> matchedRouteIds = new HashSet<>();
        Set<Long> desiredRouteIds = new HashSet<>();
        for (MenuDO menu : menus) {
            if (MenuTypeEnum.BUTTON.getType().equals(menu.getType())
                    || !isMenuEnabled(menu, menuMap, enabledCache, new HashSet<>())) {
                continue;
            }
            String fullPath = resolveFullPath(menu, menuMap, pathCache, new HashSet<>());
            if (fullPath != null && allowedMenuPaths.contains(fullPath)) {
                matchedRouteIds.add(menu.getId());
                addMenuAndAncestors(menu, menuMap, desiredRouteIds);
            }
        }

        Set<Long> desiredMenuIds = new HashSet<>(desiredRouteIds);
        for (MenuDO menu : menus) {
            if (!MenuTypeEnum.BUTTON.getType().equals(menu.getType())
                    || !isMenuEnabled(menu, menuMap, enabledCache, new HashSet<>())) {
                continue;
            }
            if (belongsToMatchedRoute(menu, menuMap, matchedRouteIds)) {
                desiredMenuIds.add(menu.getId());
            }
        }
        return desiredMenuIds;
    }

    private Set<Long> resolveManagedNavigationMenuIds(List<MenuDO> menus) {
        Map<Long, MenuDO> menuMap = new HashMap<>();
        for (MenuDO menu : menus) {
            menuMap.put(menu.getId(), menu);
        }
        Map<Long, String> pathCache = new HashMap<>();
        Map<Long, Boolean> enabledCache = new HashMap<>();
        Set<Long> managedRouteIds = new HashSet<>();
        for (MenuDO menu : menus) {
            if (MenuTypeEnum.BUTTON.getType().equals(menu.getType())
                    || !isMenuEnabled(menu, menuMap, enabledCache, new HashSet<>())) {
                continue;
            }
            String fullPath = resolveFullPath(menu, menuMap, pathCache, new HashSet<>());
            if (fullPath != null && isManagedNavigationPath(fullPath)) {
                managedRouteIds.add(menu.getId());
            }
        }
        Set<Long> managedMenuIds = new HashSet<>(managedRouteIds);
        for (MenuDO menu : menus) {
            if (MenuTypeEnum.BUTTON.getType().equals(menu.getType())
                    && belongsToManagedRoute(menu, menuMap, managedRouteIds)) {
                managedMenuIds.add(menu.getId());
            }
        }
        return managedMenuIds;
    }

    private static boolean isManagedNavigationPath(String path) {
        return MANAGED_NAVIGATION_ROOTS.stream()
                .anyMatch(root -> path.equals(root) || path.startsWith(root + "/"));
    }

    private static boolean belongsToManagedRoute(MenuDO menu, Map<Long, MenuDO> menuMap,
                                                  Set<Long> managedRouteIds) {
        Long parentId = menu.getParentId();
        Set<Long> visited = new HashSet<>();
        while (parentId != null && !Objects.equals(parentId, ID_ROOT) && visited.add(parentId)) {
            if (managedRouteIds.contains(parentId)) {
                return true;
            }
            MenuDO parent = menuMap.get(parentId);
            if (parent == null) {
                return false;
            }
            parentId = parent.getParentId();
        }
        return false;
    }

    private boolean isMenuEnabled(MenuDO menu, Map<Long, MenuDO> menuMap,
                                  Map<Long, Boolean> enabledCache, Set<Long> visiting) {
        Boolean cached = enabledCache.get(menu.getId());
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(menu.getId())
                || !CommonStatusEnum.ENABLE.getStatus().equals(menu.getStatus())) {
            enabledCache.put(menu.getId(), false);
            return false;
        }
        boolean enabled;
        if (Objects.equals(menu.getParentId(), ID_ROOT)) {
            enabled = true;
        } else {
            MenuDO parent = menuMap.get(menu.getParentId());
            enabled = parent != null && isMenuEnabled(parent, menuMap, enabledCache, visiting);
        }
        visiting.remove(menu.getId());
        enabledCache.put(menu.getId(), enabled);
        return enabled;
    }

    private String resolveFullPath(MenuDO menu, Map<Long, MenuDO> menuMap,
                                   Map<Long, String> pathCache, Set<Long> visiting) {
        if (pathCache.containsKey(menu.getId())) {
            return pathCache.get(menu.getId());
        }
        if (!visiting.add(menu.getId())) {
            return null;
        }
        String fullPath;
        if (Objects.equals(menu.getParentId(), ID_ROOT)) {
            fullPath = normalizePath(menu.getPath(), "");
        } else {
            MenuDO parent = menuMap.get(menu.getParentId());
            String parentPath = parent == null ? null : resolveFullPath(parent, menuMap, pathCache, visiting);
            fullPath = parentPath == null ? null : normalizePath(menu.getPath(), parentPath);
        }
        visiting.remove(menu.getId());
        if (fullPath != null) {
            pathCache.put(menu.getId(), fullPath);
        }
        return fullPath;
    }

    private static String normalizePath(String path, String parentPath) {
        String rawPath = StrUtil.blankToDefault(path, "");
        String joinedPath = StrUtil.isNotBlank(parentPath)
                ? StrUtil.removeSuffix(parentPath, "/") + "/" + StrUtil.removePrefix(rawPath, "/")
                : "/" + StrUtil.removePrefix(rawPath, "/");
        String normalizedPath = joinedPath.replaceAll("/+", "/");
        return normalizedPath.length() > 1 ? StrUtil.removeSuffix(normalizedPath, "/") : normalizedPath;
    }

    private static void addMenuAndAncestors(MenuDO menu, Map<Long, MenuDO> menuMap, Set<Long> target) {
        MenuDO current = menu;
        while (current != null && target.add(current.getId())
                && !Objects.equals(current.getParentId(), ID_ROOT)) {
            current = menuMap.get(current.getParentId());
        }
    }

    private static boolean belongsToMatchedRoute(MenuDO menu, Map<Long, MenuDO> menuMap,
                                                 Collection<Long> matchedRouteIds) {
        Long parentId = menu.getParentId();
        Set<Long> visited = new HashSet<>();
        while (parentId != null && !Objects.equals(parentId, ID_ROOT) && visited.add(parentId)) {
            MenuDO parent = menuMap.get(parentId);
            if (parent == null) {
                return false;
            }
            if (!MenuTypeEnum.BUTTON.getType().equals(parent.getType())) {
                return matchedRouteIds.contains(parentId);
            }
            parentId = parent.getParentId();
        }
        return false;
    }

    private static Set<Long> sortedSet(Set<Long> menuIds) {
        return new LinkedHashSet<>(new TreeSet<>(menuIds));
    }

}
