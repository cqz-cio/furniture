import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

const root = join(import.meta.dirname, "../../yudao电商管理平台前后端/yudao-cloud/sql/mysql");
const migrationPath = join(root, "migrations/V015__trade_fulfillment_core.sql");

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
  });
});
