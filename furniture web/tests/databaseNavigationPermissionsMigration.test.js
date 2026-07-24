import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

const root = join(import.meta.dirname, "../../yudao电商管理平台前后端");
const migrationPath = join(
  root,
  "yudao-cloud/sql/mysql/migrations/V030__align_furniture_navigation_permissions.sql",
);
const baselinePath = join(root, "yudao-cloud/sql/mysql/oakved-baseline.sql");
const furnitureNavigationCatalogPath = join(
  root,
  "yudao-cloud/yudao-module-system/yudao-module-system-server/src/main/resources/navigation/furniture-lite-menu-paths.json",
);
const systemServerJavaPath = join(
  root,
  "yudao-cloud/yudao-module-system/yudao-module-system-server/src/main/java/cn/iocoder/yudao/module/system",
);
const furnitureLiteConfigPath = join(
  root,
  "yudao-ui-admin-vue3/src/config/furnitureLite.ts",
);
const permissionStorePath = join(
  root,
  "yudao-ui-admin-vue3/src/store/modules/permission.ts",
);

const extractQuotedPaths = (source) =>
  [...source.matchAll(/'((?:\/dashboard)|(?:\/seo(?:\/[^']*)?)|(?:\/mall(?:\/[^']*)?)|(?:\/ai(?:\/[^']*)?))'/g)]
    .map((match) => match[1]);

const extractMigrationRoutePaths = (source) => {
  const match = source.match(
    /-- BEGIN furniture-lite custom route paths([\s\S]*?)-- END furniture-lite custom route paths/,
  );
  if (!match) throw new Error("Unable to find the furniture-lite path block in V030");
  return extractQuotedPaths(match[1]);
};

describe("V030 furniture navigation permission alignment", () => {
  it("keeps the package migration aligned with every custom furniture-lite route", () => {
    const migration = readFileSync(migrationPath, "utf8");
    const navigationCatalog = JSON.parse(
      readFileSync(furnitureNavigationCatalogPath, "utf8"),
    );
    const customNavigationPaths = navigationCatalog.filter((path) =>
      ["/dashboard", "/seo", "/mall", "/ai"].some(
        (rootPath) => path === rootPath || path.startsWith(`${rootPath}/`),
      ),
    ).filter((path) => path !== "/mall");

    expect(new Set(extractMigrationRoutePaths(migration)))
      .toEqual(new Set(customNavigationPaths));
    expect(migration).toContain("('/mall');");
    for (const rootPath of ["/mall", "/seo", "/dashboard", "/ai"]) {
      expect(migration).toContain(`\`path\` = '${rootPath}'`);
    }
  });

  it("updates only the furniture tenant packages and only auto-grants tenant admins", () => {
    const migration = readFileSync(migrationPath, "utf8");

    expect(migration).toMatch(/tenant\.`id` IN \(121, 162\)/);
    expect(migration).toContain("JSON_MERGE_PRESERVE");
    expect(migration).toContain("JSON_ARRAYAGG(scope.`menu_id`)");
    expect(migration).toContain("oakved:furniture-navigation-permissions:source-package:");
    expect(migration).toMatch(
      /INSERT INTO `system_tenant_package`[\s\S]*FROM `oakved_navigation_shared_package`/,
    );
    expect(migration).toMatch(
      /UPDATE `system_tenant` AS tenant[\s\S]*tenant\.`package_id` = clone\.`id`/,
    );
    expect(migration).toContain("tenant.`id` NOT IN (121, 162)");
    expect(migration).toContain("role.`code` = 'tenant_admin'");
    expect(migration).toContain("INSERT INTO `system_role_menu`");
    expect(migration).not.toContain("mall_operator");
    expect(migration).not.toMatch(/package\.`id` = 115|package_id\s*=\s*115/);
  });

  it("keeps the generated baseline V030 section byte-equivalent", () => {
    const migration = readFileSync(migrationPath, "utf8")
      .replace(/\r\n/g, "\n")
      .replace(/\s+$/, "") + "\n";
    const baseline = readFileSync(baselinePath, "utf8").replace(/\r\n/g, "\n");
    const marker = "-- BEGIN V030__align_furniture_navigation_permissions.sql\n";
    const start = baseline.indexOf(marker);
    const end = baseline.indexOf("\n-- BEGIN Oakved demo catalog", start);

    expect(start).toBeGreaterThanOrEqual(0);
    expect(end).toBeGreaterThan(start);
    expect(baseline.slice(start + marker.length, end).replace(/\s+$/, "") + "\n")
      .toBe(migration);
  });
});

describe("furniture navigation permission auto-sync", () => {
  it("uses one backend-owned navigation catalog for both the sidebar and package sync", () => {
    const catalog = JSON.parse(readFileSync(furnitureNavigationCatalogPath, "utf8"));
    const furnitureLiteConfig = readFileSync(furnitureLiteConfigPath, "utf8");
    const permissionStore = readFileSync(permissionStorePath, "utf8");
    const authController = readFileSync(
      join(systemServerJavaPath, "controller/admin/auth/AuthController.java"),
      "utf8",
    );
    const syncService = readFileSync(
      join(
        systemServerJavaPath,
        "service/permission/FurnitureNavigationPermissionServiceImpl.java",
      ),
      "utf8",
    );

    expect(catalog).toContain("/dashboard");
    expect(catalog).toContain("/system/role");
    expect(catalog).toContain("/system/messages/mail/mail-account");
    expect(catalog).toContain("/system/messages/mail/mail-template");
    expect(catalog).toContain("/system/messages/mail/mail-log");
    expect(furnitureLiteConfig).not.toMatch(
      /const allowedMenuPaths = new Set\(\[[\s\S]*?\]\)/,
    );
    expect(furnitureLiteConfig).toContain("synchronizedMenuPaths");
    expect(permissionStore).toContain("userInfo?.furnitureNavigationMenuPaths");
    expect(authController).toContain(
      "setFurnitureNavigationMenuPaths(furnitureNavigationCatalog.getMenuPaths())",
    );
    expect(syncService).toContain("catalog.getMenuPaths().contains(fullPath)");
  });

  it("syncs on startup and every menu lifecycle change without dropping existing package menus", () => {
    const initializer = readFileSync(
      join(
        systemServerJavaPath,
        "service/permission/FurnitureNavigationPermissionInitializer.java",
      ),
      "utf8",
    );
    const menuService = readFileSync(
      join(systemServerJavaPath, "service/permission/MenuServiceImpl.java"),
      "utf8",
    );
    const syncService = readFileSync(
      join(
        systemServerJavaPath,
        "service/permission/FurnitureNavigationPermissionServiceImpl.java",
      ),
      "utf8",
    );
    const monolithApplicationConfig = readFileSync(
      join(root, "yudao-cloud/yudao-server/src/main/resources/application.yaml"),
      "utf8",
    );
    const systemApplicationConfig = readFileSync(
      join(
        root,
        "yudao-cloud/yudao-module-system/yudao-module-system-server/src/main/resources/application.yaml",
      ),
      "utf8",
    );

    expect(initializer).toContain("implements ApplicationRunner");
    expect(initializer).toContain(
      "furnitureNavigationPermissionService.syncMenuPermissions()",
    );
    expect(
      menuService.match(
        /furnitureNavigationPermissionService\.syncMenuPermissions\(\)/g,
      ),
    ).toHaveLength(4);
    expect(syncService).toContain(
      "new HashSet<>(CollUtil.emptyIfNull(tenantPackage.getMenuIds()))",
    );
    expect(syncService).toContain("synchronizedMenuIds.addAll(desiredMenuIds)");
    expect(syncService).toContain("validatePackageOwnership(packageId)");
    for (const applicationConfig of [
      monolithApplicationConfig,
      systemApplicationConfig,
    ]) {
      expect(applicationConfig).toContain("furniture-navigation:");
      expect(applicationConfig).toContain(
        "${OAKVED_FURNITURE_NAVIGATION_TENANT_IDS:121,162}",
      );
    }
  });
});
