import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

const root = join(import.meta.dirname, "../../yudao电商管理平台前后端/yudao-cloud/sql/mysql");
const migrationPath = join(root, "migrations/V015__trade_fulfillment_core.sql");
const trackingMappingMigrationPath = join(root, "migrations/V016__trade_tracking_status_mapping.sql");
const trackingWatermarkMigrationPath = join(root, "migrations/V017__trade_tracking_event_watermarks.sql");

describe("V015 trade fulfillment core migration", () => {
  it("creates the complete Phase 1 persistence contract", () => {
    const sql = readFileSync(migrationPath, "utf8");
    for (const table of [
      "trade_carrier",
      "trade_logistics_provider",
      "trade_shipment",
      "trade_shipment_item",
      "trade_shipment_package",
      "trade_shipment_leg",
      "trade_tracking_event",
      "trade_order_fulfillment_summary",
      "trade_fulfillment_idempotency",
      "trade_fulfillment_outbox_event",
    ]) {
      expect(sql).toContain(`CREATE TABLE IF NOT EXISTS \`${table}\``);
    }
    expect(sql).toContain("CHECK (`origin_country` IN ('US','CA'))");
    expect(sql).toContain("CHECK (`destination_country` = `origin_country`)");
    expect(sql).toContain("UNIQUE KEY `uk_tracking_event_external`");
    expect(sql).toContain("UNIQUE KEY `uk_tracking_event_hash`");
    expect(sql).toContain("UNIQUE KEY `uk_fulfillment_idempotency`");
    expect(sql).not.toMatch(/api[_-]?key|secret\s+varchar|receiver_mobile/i);
  });

  it("keeps published migrations immutable and appends version 015", () => {
    const build = readFileSync(join(root, "build-oakved-baseline.mjs"), "utf8");
    expect(build).toContain("discoverMigrations");
    expect(migrationPath).toMatch(/V015__trade_fulfillment_core\.sql$/);
    const normalized = readFileSync(migrationPath, "utf8")
      .replace(/\r\n/g, "\n")
      .replace(/\s+$/, "") + "\n";
    expect(createHash("sha256").update(normalized).digest("hex"))
      .toBe("683687685b5b4943949d965f3b3df86eaa2e4dfcdbf50641fb4fc05db8d80ec4");
  });
});

describe("V017 deterministic tracking watermarks", () => {
  it("persists the complete ordering tuple on every tracked aggregate", () => {
    const sql = readFileSync(trackingWatermarkMigrationPath, "utf8");

    expect(sql.match(/ADD COLUMN `last_event_status_priority` int DEFAULT NULL/g)).toHaveLength(3);
    expect(sql.match(/ADD COLUMN `last_event_id` bigint DEFAULT NULL/g)).toHaveLength(3);
    expect(sql).toContain("ADD COLUMN `status_priority` int NOT NULL DEFAULT 0");
    expect(sql).toContain("ALTER TABLE `trade_shipment`");
    expect(sql).toContain("ALTER TABLE `trade_shipment_package`");
    expect(sql).toContain("ALTER TABLE `trade_shipment_leg`");
    expect(sql).not.toContain("V016");
  });

  it("keeps the generated baseline V017 section byte-equivalent to the migration", () => {
    const migration = readFileSync(trackingWatermarkMigrationPath, "utf8").replace(/\r\n/g, "\n").trimEnd();
    const baseline = readFileSync(join(root, "oakved-baseline.sql"), "utf8").replace(/\r\n/g, "\n");
    const marker = "-- BEGIN V017__trade_tracking_event_watermarks.sql\n";
    const start = baseline.indexOf(marker);
    const end = baseline.indexOf("\n-- BEGIN Oakved demo catalog", start);

    expect(start).toBeGreaterThanOrEqual(0);
    expect(end).toBeGreaterThan(start);
    expect(baseline.slice(start + marker.length, end).trimEnd()).toBe(migration);
  });
});

describe("V016 tracking status mapping migration", () => {
  it("adds versioned exact-carrier mappings and replayable event decisions", () => {
    const sql = readFileSync(trackingMappingMigrationPath, "utf8");

    expect(sql).toContain("CREATE TABLE IF NOT EXISTS `trade_tracking_status_mapping`");
    for (const column of [
      "`provider_code` varchar(32) NOT NULL",
      "`carrier_code` varchar(32) NOT NULL",
      "`provider_status_normalized` varchar(128) NOT NULL",
      "`standard_status` varchar(32) NOT NULL",
      "`mapping_version` varchar(32) NOT NULL",
      "`effective_at` datetime(6) NOT NULL",
    ]) {
      expect(sql).toContain(column);
    }
    expect(sql).toContain("UNIQUE KEY `uk_tracking_status_mapping`");
    expect(sql).toContain("KEY `idx_tracking_status_mapping_effective`");
    expect(sql).toContain("ADD COLUMN `provider_status_normalized`");
    expect(sql).toContain("ADD COLUMN `mapping_known`");
    expect(sql).toContain("ADD COLUMN `transition_decision`");
    expect(sql).toContain("MODIFY COLUMN `occurred_at` datetime(6) NOT NULL");
    expect(sql).toContain("MODIFY COLUMN `received_at` datetime(6) NOT NULL");
    expect(sql).toContain("MODIFY COLUMN `last_event_occurred_at` datetime(6) DEFAULT NULL");
    expect(sql.match(/ADD COLUMN `last_event_occurred_at` datetime\(6\) DEFAULT NULL/g)).toHaveLength(2);
    expect(sql).not.toMatch(/INSERT\s+INTO\s+`?trade_tracking_status_mapping`?/i);
  });

  it("keeps the generated baseline V016 section byte-equivalent to the migration", () => {
    const migration = readFileSync(trackingMappingMigrationPath, "utf8").replace(/\r\n/g, "\n").trimEnd();
    const baseline = readFileSync(join(root, "oakved-baseline.sql"), "utf8").replace(/\r\n/g, "\n");
    const marker = "-- BEGIN V016__trade_tracking_status_mapping.sql\n";
    const start = baseline.indexOf(marker);
    const end = baseline.indexOf("\n-- BEGIN V017__trade_tracking_event_watermarks.sql", start);

    expect(start).toBeGreaterThanOrEqual(0);
    expect(end).toBeGreaterThan(start);
    expect(baseline.slice(start + marker.length, end).trimEnd()).toBe(migration);
  });
});
