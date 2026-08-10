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
    expect(localStart).not.toContain("invoke-local-migrations.ps1");
    expect(localStart).toContain("packaged Flyway migrations");
  });

  it("has a strictly numbered immutable migration catalog", () => {
    const directory = new URL(
      "../../yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/",
      import.meta.url,
    );
    expect(existsSync(directory)).toBe(true);
    const files = readdirSync(directory).filter((name) => name.endsWith(".sql")).sort();
    const expectedVersions = Array.from({ length: files.length }, (_, index) => index + 1);
    expect(files).toHaveLength(expectedVersions.length);
    expect(files[0]).toMatch(/^V001__/);
    expect(files.at(-1)).toMatch(/^V\d{3}__[a-z0-9_]+\.sql$/);
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
    const flywayDirectory = new URL(
      "../../yudao电商管理平台前后端/yudao-cloud/sql/mysql/flyway/",
      import.meta.url,
    );
    const flywayBaselines = readdirSync(flywayDirectory)
      .filter((name) => /^B\d{3}__oakved_baseline\.sql$/.test(name))
      .sort();
    expect(flywayBaselines.length).toBeGreaterThan(0);
    const flywayBaseline = readFileSync(new URL(flywayBaselines.at(-1), flywayDirectory), "utf8");
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
    const latestMigration = readdirSync(new URL(
      "../../yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/",
      import.meta.url,
    )).filter((name) => /^V\d{3}__/.test(name)).sort().at(-1);
    if (flywayBaselines.at(-1).slice(1, 4) === latestMigration.slice(1, 4)) {
      expect(flywayBaseline).toBe(baseline);
    }
  });

  it("retires the standalone migration writer", () => {
    const runner = read(
      "../../yudao电商管理平台前后端/yudao-cloud/script/docker/invoke-local-migrations.ps1",
    );
    expect(runner).toContain("standalone SQL migration runner has been retired");
    expect(runner).not.toContain("GET_LOCK");
    expect(runner).not.toContain("INSERT INTO schema_migrations");
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

  it("does not bind a branch or worktree SQL file into MySQL", () => {
    const compose = read(
      "../../yudao电商管理平台前后端/yudao-cloud/script/docker/docker-compose-local-infra.yml",
    );
    expect(compose).not.toContain("docker-entrypoint-initdb.d");
    expect(compose).not.toContain("oakved-baseline.sql");
    expect(compose).toContain("yudao_mysql_data:/var/lib/mysql");
  });

  it("keeps historical Flyway baselines available for validation", () => {
    const generator = read(
      "../../yudao电商管理平台前后端/yudao-cloud/sql/mysql/build-oakved-baseline.mjs",
    );
    const workflow = read("../../.github/workflows/database-and-backend-ci.yml");
    expect(generator).not.toContain("rmSync");
    expect(generator).toContain("--create-flyway-baseline");
    expect(workflow).toContain("Protect immutable migration history");
    expect(workflow).toContain("Committed V/B migrations are immutable");
  });
});
