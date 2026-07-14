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
    expect(files).toHaveLength(14);
    expect(files[0]).toMatch(/^V001__/);
    expect(files.at(-1)).toMatch(/^V014__/);
    expect(files.map((name) => Number(name.slice(1, 4)))).toEqual(
      Array.from({ length: 14 }, (_, index) => index + 1),
    );
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
