import { existsSync, readFileSync, readdirSync } from "node:fs";
import { describe, expect, it } from "vitest";

const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("safe database deployment workflow", () => {
  it("keeps normal startup non-destructive", () => {
    const rootStart = read("../../start-yudao-infra.ps1");
    const localStart = read(
      "../../yudao电商管理平台前后端/yudao-cloud/script/docker/start-local-infra.ps1",
    );

    expect(rootStart).not.toMatch(/ReimportSql|Recreate|down\s+-v/i);
    expect(localStart).not.toMatch(/Recreate|down\s+-v/i);
    expect(localStart).toContain("invoke-local-migrations.ps1");
  });

  it("has a strictly numbered immutable migration catalog", () => {
    const directory = new URL(
      "../../yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/",
      import.meta.url,
    );
    expect(existsSync(directory)).toBe(true);
    const files = readdirSync(directory).filter((name) => name.endsWith(".sql")).sort();
    const expectedVersions = Array.from({ length: 30 }, (_, index) => index + 1);
    expect(files).toHaveLength(expectedVersions.length);
    expect(files[0]).toMatch(/^V001__/);
    expect(files.slice(-8)).toEqual([
      "V023__trade_fulfillment_legacy_migration_fact.sql",
      "V024__normalize_dashboard_route_path.sql",
      "V025__expose_oakved_mail_management.sql",
      "V026__seo_keyword_relevance_analysis.sql",
      "V027__repair_seo_analysis_menu_registration.sql",
      "V028__tenant_business_mode.sql",
      "V029__website_inquiry_notify.sql",
      "V030__align_furniture_navigation_permissions.sql",
    ]);
    expect(files.at(-1)).toBe("V030__align_furniture_navigation_permissions.sql");
    expect(files.map((name) => Number(name.slice(1, 4)))).toEqual(
      expectedVersions,
    );
  });

  it("isolates Oakved mail routes in a dedicated tenant package", () => {
    const migration = read(
      "../../yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V025__expose_oakved_mail_management.sql",
    );

    expect(migration).toContain("oakved:tenant-121:mail-management");
    expect(migration).toContain("INSERT INTO `system_tenant_package`");
    expect(migration).toContain("UPDATE `system_tenant`");
    expect(migration).toContain("INSERT INTO `system_role_menu`");
    expect(migration).toContain("system/mail/account/index");
    expect(migration).toContain("system/mail/template/index");
    expect(migration).toContain("system/mail/log/index");
    expect(migration).toContain("JSON_ARRAY_APPEND");
    expect(migration).toContain("JSON_TABLE");
  });

  it("generates one complete baseline with catalog and ERP demo data", () => {
    const baseline = read(
      "../../yudao电商管理平台前后端/yudao-cloud/sql/mysql/oakved-baseline.sql",
    );
    const seed = read(
      "../../yudao电商管理平台前后端/yudao-cloud/sql/mysql/oakved-demo-data.sql",
    );

    expect(baseline).toContain("CREATE TABLE IF NOT EXISTS `schema_migrations`");
    expect(baseline).toContain("CREATE TABLE IF NOT EXISTS `erp_product`");
    expect(baseline).toContain("CREATE TABLE IF NOT EXISTS `mall_erp_product_mapping`");
    expect(baseline).toContain("Oakved demo catalog");
    expect(seed).toContain("SET @tenant_id = 121");
    expect(seed.match(/CALL seed_oakved_product\(/g)).toHaveLength(26);
    expect(seed).toContain("INSERT INTO erp_product");
    expect(seed).toContain("INSERT INTO mall_erp_product_mapping");
    expect(seed).toContain("INSERT INTO erp_stock");
    expect(seed).toContain("SIGNAL SQLSTATE '45000'");
    expect(baseline).not.toMatch(/INSERT INTO `infra_file_config` .*\\\"accessKey\\\"/);
    expect(baseline).not.toContain("INSERT INTO `system_sms_channel`");
    expect(baseline).not.toContain("INSERT INTO `system_mail_account`");
    expect(baseline).toContain("Sensitive external-service seed omitted");
  });

  it("tracks migration checksums under an advisory lock", () => {
    const runner = read(
      "../../yudao电商管理平台前后端/yudao-cloud/script/docker/invoke-local-migrations.ps1",
    );
    expect(runner).toContain("GET_LOCK('oakved_schema_migrations'");
    expect(runner).toContain("RELEASE_LOCK('oakved_schema_migrations')");
    expect(runner).toContain("Get-FileHash");
    expect(runner).toContain("checksum_sha256");
    expect(runner).not.toContain("down -v");
  });

  it("requires a verified backup and exact reset confirmation", () => {
    const reset = read(
      "../../yudao电商管理平台前后端/yudao-cloud/script/docker/reset-local-infra.ps1",
    );
    expect(reset).toContain("mysqldump");
    expect(reset).toContain("RESET OAKVED LOCAL DATA");
    expect(reset).toMatch(/Length\s+-le\s+0/);
    expect(reset.indexOf("mysqldump")).toBeLessThan(reset.indexOf("down -v"));
  });

  it("mounts only the generated baseline for first initialization", () => {
    const compose = read(
      "../../yudao电商管理平台前后端/yudao-cloud/script/docker/docker-compose-local-infra.yml",
    );
    const initMounts = compose.match(/docker-entrypoint-initdb\.d/g) ?? [];
    expect(initMounts).toHaveLength(1);
    expect(compose).toContain("oakved-baseline.sql:/docker-entrypoint-initdb.d/01-oakved-baseline.sql:ro");
  });
});
