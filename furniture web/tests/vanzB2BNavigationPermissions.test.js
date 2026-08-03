import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

const repositoryRoot = join(import.meta.dirname, "../..");
const systemRoot = join(
  repositoryRoot,
  "yudao电商管理平台前后端/yudao-cloud/yudao-module-system/yudao-module-system-server",
);
const adminRoot = join(
  repositoryRoot,
  "yudao电商管理平台前后端/yudao-ui-admin-vue3/src",
);
const migrationPath = join(
  repositoryRoot,
  "yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V038__vanz_b2b_navigation_permissions.sql",
);
const b2bCatalogPath = join(
  systemRoot,
  "src/main/resources/navigation/furniture-b2b-menu-paths.json",
);

const businessRoots = [
  "/mall",
  "/member",
  "/pay",
  "/crm",
  "/seo",
  "/dashboard",
  "/ai",
];
const isBusinessPath = (path) =>
  businessRoots.some((root) => path === root || path.startsWith(`${root}/`));

const migrationBusinessPaths = (source) => {
  const match = source.match(
    /-- BEGIN vanz-b2b business route paths([\s\S]*?)-- END vanz-b2b business route paths/,
  );
  if (!match) throw new Error("V038 B2B route block is missing");
  return [...match[1].matchAll(/\('([^']+)'\)/g)].map((entry) => entry[1]);
};

describe("VANZ B2B navigation and dashboard policy", () => {
  it("keeps the migration route scope aligned with the backend B2B catalog", () => {
    const migration = readFileSync(migrationPath, "utf8");
    const catalog = JSON.parse(readFileSync(b2bCatalogPath, "utf8"));
    const expectedBusinessPaths = catalog.filter(isBusinessPath);

    expect(new Set(migrationBusinessPaths(migration))).toEqual(
      new Set(expectedBusinessPaths),
    );
    expect(catalog).toContain("/dashboard");
    expect(catalog).toContain("/mall/product/spu");
    expect(catalog).toContain("/seo/metadata");
    expect(catalog).toContain("/crm/clue");
    expect(catalog).not.toContain("/member");
    expect(catalog).not.toContain("/mall/trade/order");
    expect(catalog).not.toContain("/pay/order");
  });

  it("isolates tenant 162 and prunes package and role permissions", () => {
    const migration = readFileSync(migrationPath, "utf8");

    expect(migration).toContain("SET @vanz_b2b_tenant_id = 162");
    expect(migration).toContain("tenant.`business_mode` = 'B2B'");
    expect(migration).toContain("vanz:tenant-162:b2b-navigation:v038");
    expect(migration).toContain("INSERT INTO `system_tenant_package`");
    expect(migration).toMatch(
      /NOT EXISTS \([\s\S]*FROM `vanz_b2b_managed_menu_scope` AS managed/,
    );
    expect(migration).toContain("UPDATE `system_role_menu` AS role_menu");
    expect(migration).toContain("role.`code` = 'tenant_admin'");
    expect(migration).not.toMatch(/@vanz_b2b_tenant_id\s*=\s*121/);
  });

  it("keeps MySQL temporary-table reads separate from their write targets", () => {
    const migration = readFileSync(migrationPath, "utf8");

    expect(migration).toContain("vanz_b2b_managed_route_scope");
    expect(migration).toMatch(
      /INSERT IGNORE INTO `vanz_b2b_managed_menu_scope`[\s\S]*?INNER JOIN `vanz_b2b_managed_route_scope` AS parent/,
    );
    expect(migration).toMatch(
      /WITH RECURSIVE `allowed_ancestors`[\s\S]*?FROM `system_menu` AS route[\s\S]*?FROM `system_menu` AS parent/,
    );
  });

  it("selects dashboard and home templates from the tenant business profile", () => {
    const home = readFileSync(join(adminRoot, "views/Home/Index.vue"), "utf8");
    const b2bHome = readFileSync(
      join(adminRoot, "views/Home/B2BHome.vue"),
      "utf8",
    );
    const dashboard = readFileSync(
      join(adminRoot, "views/dashboard/index.vue"),
      "utf8",
    );
    const inquiryDashboard = readFileSync(
      join(adminRoot, "views/dashboard/InquiryDashboard.vue"),
      "utf8",
    );

    expect(home).toContain('<B2BHome v-else-if="isB2B"');
    expect(home).toContain("useTenantBusinessProfile");
    expect(dashboard).toContain('<InquiryDashboard v-else-if="isB2B"');
    expect(dashboard).toContain("useTenantBusinessProfile");
    expect(b2bHome).toContain("ClueApi.getInquirySummary()");
    expect(b2bHome).not.toContain("今日订单");
    expect(inquiryDashboard).toContain("DashboardApi.getTrend(query)");
    expect(inquiryDashboard).toContain("访客转询盘率");
    expect(inquiryDashboard).not.toContain("净销售额");
  });

  it("returns a business-mode-specific menu catalog and prunes B2B business roots", () => {
    const catalog = readFileSync(
      join(
        systemRoot,
        "src/main/java/cn/iocoder/yudao/module/system/framework/navigation/config/FurnitureNavigationCatalog.java",
      ),
      "utf8",
    );
    const authController = readFileSync(
      join(
        systemRoot,
        "src/main/java/cn/iocoder/yudao/module/system/controller/admin/auth/AuthController.java",
      ),
      "utf8",
    );
    const syncService = readFileSync(
      join(
        systemRoot,
        "src/main/java/cn/iocoder/yudao/module/system/service/permission/FurnitureNavigationPermissionServiceImpl.java",
      ),
      "utf8",
    );

    expect(catalog).toContain("getMenuPaths(String businessMode)");
    expect(catalog).toContain("TenantBusinessModeEnum.B2B");
    expect(authController).toContain(
      "getCurrentFurnitureNavigationMenuPaths()",
    );
    expect(authController).toContain("tenant.getBusinessMode()");
    expect(syncService).toContain(
      "synchronizedMenuIds.removeAll(managedBusinessMenuIds)",
    );
    expect(syncService).toContain("TenantBusinessModeEnum.B2B");
  });
});
