package cn.iocoder.yudao.module.system.service.permission;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
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
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.LinkedHashSet;
import java.util.Set;

import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomPojo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Import(FurnitureNavigationPermissionServiceImpl.class)
class FurnitureNavigationPermissionServiceImplTest extends BaseDbUnitTest {

    private static final Long TARGET_TENANT_ID = 162L;
    private static final Long TARGET_PACKAGE_ID = 115L;

    @Resource
    private FurnitureNavigationPermissionServiceImpl navigationPermissionService;
    @Resource
    private MenuMapper menuMapper;
    @Resource
    private TenantMapper tenantMapper;
    @Resource
    private TenantPackageMapper tenantPackageMapper;

    @MockitoBean
    private FurnitureNavigationProperties properties;
    @MockitoBean
    private FurnitureNavigationCatalog catalog;
    @MockitoBean
    private TenantService tenantService;

    @BeforeEach
    void setUp() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getTenantIds()).thenReturn(Set.of(TARGET_TENANT_ID));
        when(catalog.getMenuPaths()).thenReturn(Set.of("/system/role"));
        when(catalog.getMenuPaths(any())).thenReturn(Set.of("/system/role"));
    }

    @Test
    void syncMenuPermissions_addsNavigationTreeAndButtonsWithoutRemovingExistingMenus() {
        insertTargetPackageAndTenant();
        menuMapper.insert(buildMenu(1L, 0L, "/system", MenuTypeEnum.DIR));
        menuMapper.insert(buildMenu(2L, 1L, "role", MenuTypeEnum.MENU));
        menuMapper.insert(buildMenu(3L, 2L, "", MenuTypeEnum.BUTTON));
        menuMapper.insert(buildMenu(4L, 1L, "dept", MenuTypeEnum.MENU));
        menuMapper.insert(buildMenu(5L, 4L, "", MenuTypeEnum.BUTTON));

        navigationPermissionService.syncMenuPermissions();

        TenantPackageDO tenantPackage = tenantPackageMapper.selectById(TARGET_PACKAGE_ID);
        assertEquals(new LinkedHashSet<>(Set.of(1L, 2L, 3L, 999L)), tenantPackage.getMenuIds());
        verify(tenantService).updateTenantRoleMenu(
                TARGET_TENANT_ID, new LinkedHashSet<>(Set.of(1L, 2L, 3L, 999L)));
    }

    @Test
    void resolveDesiredMenuIds_ignoresDisabledBranchesAndKeepsAllowedAncestors() {
        MenuDO system = buildMenu(1L, 0L, "/system", MenuTypeEnum.DIR);
        MenuDO role = buildMenu(2L, 1L, "role", MenuTypeEnum.MENU);
        MenuDO button = buildMenu(3L, 2L, "", MenuTypeEnum.BUTTON);
        MenuDO disabledButton = buildMenu(4L, 2L, "", MenuTypeEnum.BUTTON);
        disabledButton.setStatus(CommonStatusEnum.DISABLE.getStatus());

        Set<Long> result = navigationPermissionService.resolveDesiredMenuIds(
                java.util.List.of(system, role, button, disabledButton));

        assertEquals(Set.of(1L, 2L, 3L), result);
    }

    @Test
    void syncMenuPermissions_rejectsPackageSharedWithUnrelatedTenant() {
        insertTargetPackageAndTenant();
        tenantMapper.insert(randomPojo(TenantDO.class, tenant -> tenant
                .setId(999L)
                .setName("unrelated")
                .setPackageId(TARGET_PACKAGE_ID)
                .setStatus(CommonStatusEnum.ENABLE.getStatus())));
        menuMapper.insert(buildMenu(1L, 0L, "/system", MenuTypeEnum.DIR));
        menuMapper.insert(buildMenu(2L, 1L, "role", MenuTypeEnum.MENU));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> navigationPermissionService.syncMenuPermissions());

        assertTrue(exception.getMessage().contains("非目标租户"));
        assertEquals(Set.of(999L), tenantPackageMapper.selectById(TARGET_PACKAGE_ID).getMenuIds());
    }

    @Test
    void syncMenuPermissions_b2bRemovesTransactionModulesAndKeepsProductCenter() {
        when(catalog.getMenuPaths()).thenReturn(Set.of("/mall/product/spu", "/mall/trade/order"));
        when(catalog.getMenuPaths(TenantBusinessModeEnum.B2B.getCode()))
                .thenReturn(Set.of("/mall/product/spu"));
        insertTargetPackageAndTenant(TenantBusinessModeEnum.B2B.getCode());
        tenantPackageMapper.updateById(new TenantPackageDO()
                .setId(TARGET_PACKAGE_ID)
                .setMenuIds(Set.of(1L, 2L, 3L, 4L, 5L, 6L, 999L)));
        menuMapper.insert(buildMenu(1L, 0L, "/mall", MenuTypeEnum.DIR));
        menuMapper.insert(buildMenu(2L, 1L, "product", MenuTypeEnum.DIR));
        menuMapper.insert(buildMenu(3L, 2L, "spu", MenuTypeEnum.MENU));
        menuMapper.insert(buildMenu(4L, 3L, "", MenuTypeEnum.BUTTON));
        menuMapper.insert(buildMenu(5L, 1L, "trade", MenuTypeEnum.DIR));
        menuMapper.insert(buildMenu(6L, 5L, "order", MenuTypeEnum.MENU));

        navigationPermissionService.syncMenuPermissions();

        Set<Long> expected = new LinkedHashSet<>(Set.of(1L, 2L, 3L, 4L, 999L));
        assertEquals(expected, tenantPackageMapper.selectById(TARGET_PACKAGE_ID).getMenuIds());
        verify(tenantService).updateTenantRoleMenu(TARGET_TENANT_ID, expected);
    }

    @Test
    void syncMenuPermissions_b2cRemovesInquiryAndTechnicalModulesButKeepsOperations() {
        Set<String> fullCatalog = Set.of(
                "/mall/trade/order", "/crm/clue", "/system/role", "/pay/app", "/pay/order",
                "/infra/file-config", "/infra/file");
        when(catalog.getMenuPaths()).thenReturn(fullCatalog);
        when(catalog.getMenuPaths(TenantBusinessModeEnum.B2C.getCode()))
                .thenReturn(Set.of("/mall/trade/order", "/pay/order", "/infra/file"));
        insertTargetPackageAndTenant(TenantBusinessModeEnum.B2C.getCode());
        tenantPackageMapper.updateById(new TenantPackageDO()
                .setId(TARGET_PACKAGE_ID)
                .setMenuIds(Set.of(
                        1L, 2L, 3L, 4L,
                        10L, 11L, 12L,
                        20L, 21L, 22L,
                        30L, 31L, 32L, 33L, 34L,
                        40L, 41L, 42L, 43L, 44L,
                        999L)));

        menuMapper.insert(buildMenu(1L, 0L, "/mall", MenuTypeEnum.DIR));
        menuMapper.insert(buildMenu(2L, 1L, "trade", MenuTypeEnum.DIR));
        menuMapper.insert(buildMenu(3L, 2L, "order", MenuTypeEnum.MENU));
        menuMapper.insert(buildMenu(4L, 3L, "", MenuTypeEnum.BUTTON));
        menuMapper.insert(buildMenu(10L, 0L, "/crm", MenuTypeEnum.DIR));
        menuMapper.insert(buildMenu(11L, 10L, "clue", MenuTypeEnum.MENU));
        menuMapper.insert(buildMenu(12L, 11L, "", MenuTypeEnum.BUTTON));
        menuMapper.insert(buildMenu(20L, 0L, "/system", MenuTypeEnum.DIR));
        menuMapper.insert(buildMenu(21L, 20L, "role", MenuTypeEnum.MENU));
        menuMapper.insert(buildMenu(22L, 21L, "", MenuTypeEnum.BUTTON));
        menuMapper.insert(buildMenu(30L, 0L, "/pay", MenuTypeEnum.DIR));
        menuMapper.insert(buildMenu(31L, 30L, "app", MenuTypeEnum.MENU));
        menuMapper.insert(buildMenu(32L, 31L, "", MenuTypeEnum.BUTTON));
        menuMapper.insert(buildMenu(33L, 30L, "order", MenuTypeEnum.MENU));
        menuMapper.insert(buildMenu(34L, 33L, "", MenuTypeEnum.BUTTON));
        menuMapper.insert(buildMenu(40L, 0L, "/infra", MenuTypeEnum.DIR));
        menuMapper.insert(buildMenu(41L, 40L, "file-config", MenuTypeEnum.MENU));
        menuMapper.insert(buildMenu(42L, 41L, "", MenuTypeEnum.BUTTON));
        menuMapper.insert(buildMenu(43L, 40L, "file", MenuTypeEnum.MENU));
        menuMapper.insert(buildMenu(44L, 43L, "", MenuTypeEnum.BUTTON));

        navigationPermissionService.syncMenuPermissions();

        Set<Long> expected = new LinkedHashSet<>(Set.of(
                1L, 2L, 3L, 4L, 30L, 33L, 34L, 40L, 43L, 44L, 999L));
        assertEquals(expected, tenantPackageMapper.selectById(TARGET_PACKAGE_ID).getMenuIds());
        verify(tenantService).updateTenantRoleMenu(TARGET_TENANT_ID, expected);
    }

    @Test
    void syncMenuPermissions_b2bRejectsEmptyMatchedCatalogBeforePruning() {
        when(catalog.getMenuPaths(TenantBusinessModeEnum.B2B.getCode())).thenReturn(Set.of());
        insertTargetPackageAndTenant(TenantBusinessModeEnum.B2B.getCode());
        menuMapper.insert(buildMenu(1L, 0L, "/system", MenuTypeEnum.DIR));
        menuMapper.insert(buildMenu(2L, 1L, "role", MenuTypeEnum.MENU));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> navigationPermissionService.syncMenuPermissions());

        assertTrue(exception.getMessage().contains("拒绝修改套餐权限"));
        assertEquals(Set.of(999L), tenantPackageMapper.selectById(TARGET_PACKAGE_ID).getMenuIds());
    }

    private void insertTargetPackageAndTenant() {
        insertTargetPackageAndTenant(TenantBusinessModeEnum.B2C.getCode());
    }

    private void insertTargetPackageAndTenant(String businessMode) {
        tenantPackageMapper.insert(randomPojo(TenantPackageDO.class, tenantPackage -> tenantPackage
                .setId(TARGET_PACKAGE_ID)
                .setName("家具导航套餐")
                .setStatus(CommonStatusEnum.ENABLE.getStatus())
                .setMenuIds(Set.of(999L))));
        tenantMapper.insert(randomPojo(TenantDO.class, tenant -> tenant
                .setId(TARGET_TENANT_ID)
                .setName("Vanz家具")
                .setPackageId(TARGET_PACKAGE_ID)
                .setBusinessMode(businessMode)
                .setStatus(CommonStatusEnum.ENABLE.getStatus())));
    }

    private static MenuDO buildMenu(Long id, Long parentId, String path, MenuTypeEnum type) {
        return randomPojo(MenuDO.class, menu -> menu
                .setId(id)
                .setParentId(parentId)
                .setPath(path)
                .setType(type.getType())
                .setStatus(CommonStatusEnum.ENABLE.getStatus()));
    }

}
