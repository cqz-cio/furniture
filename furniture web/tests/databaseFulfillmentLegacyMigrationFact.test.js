import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

const root = join(import.meta.dirname, "../../yudao电商管理平台前后端/yudao-cloud/sql/mysql");
const migrationName = "V020__trade_fulfillment_legacy_migration_fact.sql";
const migrationPath = join(root, "migrations", migrationName);
const tradeServerRoot = join(root, "../../yudao-module-mall/yudao-module-trade-server");

const normalize = (value) => value.replace(/\r\n/g, "\n").replace(/\s+$/, "") + "\n";

describe("V020 approved legacy fulfillment migration facts", () => {
  it("creates one absolute, non-sensitive approval row per tenant order", () => {
    const sql = readFileSync(migrationPath, "utf8");

    expect(sql).toContain("CREATE TABLE IF NOT EXISTS `trade_fulfillment_legacy_migration_fact`");
    for (const column of [
      "`tenant_id` bigint NOT NULL",
      "`order_id` bigint NOT NULL",
      "`origin_country` char(2) NOT NULL",
      "`destination_country` char(2) NOT NULL",
      "`origin_timezone` varchar(64) NOT NULL",
      "`destination_timezone` varchar(64) NOT NULL",
      "`warehouse_id` bigint NOT NULL",
      "`migration_provider_id` bigint NOT NULL",
      "`approved_by` bigint NOT NULL",
      "`approved_at` datetime(6) NOT NULL",
      "`source_reference` varchar(255) NOT NULL",
    ]) {
      expect(sql).toContain(column);
    }
    expect(sql).toContain("UNIQUE KEY `uk_legacy_migration_fact_order` (`tenant_id`,`order_id`)");
    expect(sql).not.toContain("`tenant_id`,`order_id`,`deleted`");
    expect(sql).not.toMatch(/credential|api[_-]?key|secret|tracking|phone|receiver|address/i);
  });

  it("keeps V015 through V019 immutable and baseline V020 byte-equivalent", () => {
    const expectedHashes = new Map([
      ["V015__trade_fulfillment_core.sql", "683687685b5b4943949d965f3b3df86eaa2e4dfcdbf50641fb4fc05db8d80ec4"],
      ["V016__trade_tracking_status_mapping.sql", "21dbb820f0e1099b73154bcc2d6011cdc1ea98556f580aaa0e5ccdd7ed7951da"],
      ["V017__trade_tracking_event_watermarks.sql", "4bddf6d0d0833138a45a6c4b52a6634e67a2798d66b7bf43cee8908a02bed46b"],
      ["V018__trade_manual_tracking_audit.sql", "002dad8815da46261f6a361ac9bf36850a345287844c0ea6e3295b53fdc8812d"],
      ["V019__trade_fulfillment_admin_permissions.sql", "2b7094e055a3ab0fce335a96fcf0f539d4cb337a7190efa50fc7c7f538778e18"],
    ]);
    for (const [name, expected] of expectedHashes) {
      const digest = createHash("sha256")
        .update(normalize(readFileSync(join(root, "migrations", name), "utf8")))
        .digest("hex");
      expect(digest).toBe(expected);
    }

    const migration = normalize(readFileSync(migrationPath, "utf8"));
    const baseline = readFileSync(join(root, "oakved-baseline.sql"), "utf8").replace(/\r\n/g, "\n");
    const marker = `-- BEGIN ${migrationName}\n`;
    const start = baseline.indexOf(marker);
    const end = baseline.indexOf("\n-- BEGIN Oakved demo catalog", start);

    expect(start).toBeGreaterThanOrEqual(0);
    expect(end).toBeGreaterThan(start);
    expect(normalize(baseline.slice(start + marker.length, end))).toBe(migration);
  });

  it("keeps the H2 fixture and cleanup order aligned", () => {
    const createTables = readFileSync(join(tradeServerRoot, "src/test/resources/sql/create_tables.sql"), "utf8");
    const clean = readFileSync(join(tradeServerRoot, "src/test/resources/sql/clean.sql"), "utf8");
    const factDelete = clean.indexOf("DELETE FROM trade_fulfillment_legacy_migration_fact;");
    const carrierDelete = clean.indexOf("DELETE FROM trade_carrier;");

    expect(createTables).toContain('CREATE TABLE IF NOT EXISTS "trade_fulfillment_legacy_migration_fact"');
    expect(createTables).toContain('CONSTRAINT "uk_legacy_migration_fact_order" UNIQUE ("tenant_id", "order_id")');
    expect(factDelete).toBeGreaterThanOrEqual(0);
    expect(factDelete).toBeLessThan(carrierDelete);
  });
});
