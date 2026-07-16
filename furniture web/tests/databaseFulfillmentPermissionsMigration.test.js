import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

const root = join(import.meta.dirname, "../../yudao电商管理平台前后端/yudao-cloud/sql/mysql");
const migrationPath = join(root, "migrations/V019__trade_fulfillment_admin_permissions.sql");
const baselinePath = join(root, "oakved-baseline.sql");
const permissions = [
  "trade:fulfillment:shipment:query",
  "trade:fulfillment:shipment:create",
  "trade:fulfillment:shipment:update",
  "trade:fulfillment:shipment:dispatch",
  "trade:fulfillment:tracking:manual",
];

describe("V019 fulfillment admin permission migration", () => {
  it("adds five idempotent button permissions under the order list", () => {
    const sql = readFileSync(migrationPath, "utf8");
    for (const permission of permissions) {
      expect(sql).toContain(`'${permission}'`);
    }
    expect(sql.match(/INSERT INTO `system_menu`/g)).toHaveLength(5);
    expect(sql.match(/SELECT 1 FROM `system_menu` WHERE `id` = 2076 AND `deleted` = b'0'/g))
      .toHaveLength(5);
    expect(sql.match(/`type`, `sort`, `parent_id`/g)).toHaveLength(5);
    expect(sql).not.toMatch(/INSERT INTO `system_role_menu`|role_id|tenant_id|credential|secret|api[_-]?key/i);
  });

  it("keeps the generated baseline V019 section byte-equivalent", () => {
    const migration = readFileSync(migrationPath, "utf8").replace(/\r\n/g, "\n").replace(/\s+$/, "") + "\n";
    const baseline = readFileSync(baselinePath, "utf8").replace(/\r\n/g, "\n");
    const marker = "-- BEGIN V019__trade_fulfillment_admin_permissions.sql\n";
    const start = baseline.indexOf(marker) + marker.length;
    const end = baseline.indexOf("\n-- BEGIN V020__trade_fulfillment_legacy_migration_fact.sql", start);
    expect(start).toBeGreaterThan(marker.length - 1);
    expect(end).toBeGreaterThan(start);
    expect(baseline.slice(start, end).replace(/\s+$/, "") + "\n").toBe(migration);
  });
});
