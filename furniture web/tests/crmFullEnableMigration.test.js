import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const root = new URL("../../", import.meta.url);
const read = (path) => readFileSync(new URL(path, root), "utf8");

const cloud = "yudao电商管理平台前后端/yudao-cloud/";
const admin = "yudao电商管理平台前后端/yudao-ui-admin-vue3/";
const migrationPath =
  `${cloud}sql/mysql/migrations/V031__enable_full_crm.sql`;

const crmTables = [
  "crm_business",
  "crm_business_product",
  "crm_business_status",
  "crm_business_status_type",
  "crm_clue",
  "crm_contact",
  "crm_contact_business",
  "crm_contract",
  "crm_contract_config",
  "crm_contract_product",
  "crm_customer",
  "crm_customer_limit_config",
  "crm_customer_pool_config",
  "crm_follow_up_record",
  "crm_permission",
  "crm_product",
  "crm_product_category",
  "crm_receivable",
  "crm_receivable_plan",
];

describe("V031 full CRM enablement", () => {
  it("creates every table used by the CRM data objects", () => {
    const migration = read(migrationPath);

    expect(crmTables).toHaveLength(19);
    for (const table of crmTables) {
      expect(migration).toContain(`CREATE TABLE IF NOT EXISTS \`${table}\``);
    }
    expect(migration).toContain(") <> 19;");
  });

  it("loads the CRM server module into the unified backend", () => {
    const pom = read(`${cloud}yudao-server/pom.xml`);

    expect(pom).toContain(
      "<artifactId>yudao-module-crm-server</artifactId>",
    );
    expect(pom).toContain(
      "<artifactId>yudao-module-bpm-server</artifactId>",
    );
  });

  it("keeps the CRM dependency jar loadable inside the unified backend", () => {
    const pom = read(
      `${cloud}yudao-module-crm/yudao-module-crm-server/pom.xml`,
    );

    expect(pom).toContain("<artifactId>spring-boot-maven-plugin</artifactId>");
    expect(pom).toContain("<classifier>exec</classifier>");
  });

  it("keeps the CRM approval dependency loadable inside the unified backend", () => {
    const pom = read(
      `${cloud}yudao-module-bpm/yudao-module-bpm-server/pom.xml`,
    );

    expect(pom).toContain("<artifactId>spring-boot-maven-plugin</artifactId>");
    expect(pom).toContain("<classifier>exec</classifier>");
  });

  it("opens the complete recursive CRM menu tree for the VANZ tenant admin", () => {
    const migration = read(migrationPath);

    expect(migration).toMatch(
      /WHERE `id` = 162[\s\S]*WHERE `id` = 121/,
    );
    expect(migration).toContain("WITH RECURSIVE `crm_menu_tree`");
    expect(migration).toContain("child.`parent_id` = parent.`id`");
    expect(migration).toContain("JSON_ARRAYAGG(ordered_menu.`menu_id`)");
    expect(migration).toContain("role.`code` = 'tenant_admin'");
    expect(migration).toContain("role_menu.`menu_id` = scope.`menu_id`");
  });

  it("allows CRM dynamic and fixed routes in furniture-lite mode", () => {
    const source = read(`${admin}src/config/furnitureLite.ts`);
    const navigationCatalog = JSON.parse(
      read(
        `${cloud}yudao-module-system/yudao-module-system-server/src/main/resources/navigation/furniture-lite-menu-paths.json`,
      ),
    );
    const deniedPrefixes =
      source.match(/const deniedFixedRoutePrefixes = \[[^\]]*\]/)?.[0] || "";

    expect(navigationCatalog).toContain("/crm");
    expect(navigationCatalog).toContain("/crm/clue");
    expect(navigationCatalog).toContain("/crm/config/contract-config");
    expect(source).toContain("synchronizedMenuPaths");
    expect(deniedPrefixes).not.toContain("'/crm'");
  });

  it("keeps the generated baseline section byte-equivalent to V031", () => {
    const migration =
      read(migrationPath).replace(/\r\n/g, "\n").replace(/\s+$/, "") + "\n";
    const baseline = read(
      `${cloud}sql/mysql/oakved-baseline.sql`,
    ).replace(/\r\n/g, "\n");
    const marker = "-- BEGIN V031__enable_full_crm.sql\n";
    const start = baseline.indexOf(marker);
    const end = baseline.indexOf("\n-- BEGIN Oakved demo catalog", start);

    expect(start).toBeGreaterThanOrEqual(0);
    expect(end).toBeGreaterThan(start);
    expect(
      baseline.slice(start + marker.length, end).replace(/\s+$/, "") + "\n",
    ).toBe(migration);
  });
});
