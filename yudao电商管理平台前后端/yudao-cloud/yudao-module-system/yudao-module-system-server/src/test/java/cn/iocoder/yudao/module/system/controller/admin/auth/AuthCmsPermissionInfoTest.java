package cn.iocoder.yudao.module.system.controller.admin.auth;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthPermissionInfoRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.MenuDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.framework.navigation.config.FurnitureNavigationConfiguration;
import cn.iocoder.yudao.module.system.service.permission.*;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class AuthCmsPermissionInfoTest {
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    void retainsGrantedCmsNavigationAfterBuildingTheMenuTree() throws Exception {
        AuthPermissionInfoRespVO response = permissionInfo(true);
        assertTrue(response.getPermissions().contains("seo:page:query"));
        assertTrue(response.getFurnitureNavigationMenuPaths().contains("/seo/page-content"),
                "Menu conversion must not discard the grant used to select CMS navigation");
        var page = response.getMenus().get(0).getChildren().get(0);
        assertEquals("page-content", page.getPath());
        assertTrue(page.getChildren() == null || page.getChildren().isEmpty(),
                "Action permissions must not appear as sidebar pages");
    }

    @Test
    void doesNotAddCmsNavigationWithoutItsQueryPermission() throws Exception {
        AuthPermissionInfoRespVO response = permissionInfo(false);
        assertFalse(response.getPermissions().contains("seo:page:query"));
        assertFalse(response.getFurnitureNavigationMenuPaths().contains("/seo/page-content"));
    }

    private AuthPermissionInfoRespVO permissionInfo(boolean grantPageQuery) throws Exception {
        LoginUser login = new LoginUser(); login.setId(9001L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(login, null, List.of()));
        TenantContextHolder.setTenantId(163L);
        AdminUserDO user = new AdminUserDO(); user.setId(9001L);
        RoleDO role = new RoleDO(); role.setId(163L); role.setCode("brand_operator"); role.setStatus(0);
        TenantDO tenant = new TenantDO(); tenant.setId(163L); tenant.setPackageId(163L); tenant.setBusinessMode("B2B");
        List<MenuDO> menus = new ArrayList<>(List.of(
                menu(1L, 0L, 1, "/seo", ""), menu(2L, 1L, 2, "page-content", "")));
        if (grantPageQuery) menus.add(menu(3L, 2L, 3, "", "seo:page:query"));
        AdminUserService users = mock(AdminUserService.class);
        RoleService roles = mock(RoleService.class);
        MenuService menuService = mock(MenuService.class);
        PermissionService permissions = mock(PermissionService.class);
        TenantService tenants = mock(TenantService.class);
        when(users.getUser(9001L)).thenReturn(user);
        when(permissions.getUserRoleIdListByUserId(9001L)).thenReturn(Set.of(163L));
        when(roles.getRoleList(any())).thenReturn(new ArrayList<>(List.of(role)));
        when(permissions.getRoleMenuListByRoleId(anyCollection())).thenReturn(Set.of(1L, 2L, 3L));
        when(menuService.getMenuList(anyCollection())).thenReturn(menus);
        when(menuService.filterDisableMenus(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tenants.getTenant(163L)).thenReturn(tenant);
        AuthController controller = new AuthController();
        ReflectionTestUtils.setField(controller, "userService", users);
        ReflectionTestUtils.setField(controller, "roleService", roles);
        ReflectionTestUtils.setField(controller, "menuService", menuService);
        ReflectionTestUtils.setField(controller, "permissionService", permissions);
        ReflectionTestUtils.setField(controller, "tenantService", tenants);
        ReflectionTestUtils.setField(controller, "furnitureNavigationCatalog",
                new FurnitureNavigationConfiguration().furnitureNavigationCatalog(new ObjectMapper()));
        return controller.getPermissionInfo().getData();
    }

    private MenuDO menu(long id, long parent, int type, String path, String permission) {
        MenuDO menu = new MenuDO(); menu.setId(id); menu.setParentId(parent); menu.setType(type);
        menu.setSort((int) id); menu.setPath(path); menu.setPermission(permission); menu.setStatus(0);
        return menu;
    }
}
