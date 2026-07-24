import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

const root = join(import.meta.dirname, "../../yudao电商管理平台前后端");
const migrationPath = join(
  root,
  "yudao-cloud/sql/mysql/migrations/V030__align_furniture_navigation_permissions.sql",
);
const baselinePath = join(root, "yudao-cloud/sql/mysql/oakved-baseline.sql");
const furnitureLitePath = join(root, "yudao-ui-admin-vue3/src/config/furnitureLite.ts");

const extractQuotedPaths = (source) =>
  [...source.matchAll(/'((?:\/dashboard)|(?:\/seo(?:\/[^']*)?)|(?:\/mall(?:\/[^']*)?)|(?:\/ai(?:\/[^']*)?))'/g)]
    .map((match) => match[1]);

const extractFurnitureLitePaths = (source) => {
  const match = source.match(/const allowedMenuPaths = new Set\(\[([\s\S]*?)\]\)/);
  if (!match) throw new Error("Unable to find allowedMenuPaths in furnitureLite.ts");
  return extractQuotedPaths(match[1]);
};

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
    const furnitureLite = readFileSync(furnitureLitePath, "utf8");

    expect(new Set(extractMigrationRoutePaths(migration)))
      .toEqual(new Set(extractFurnitureLitePaths(furnitureLite)));
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
