import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

const root = join(import.meta.dirname, "../../yudao电商管理平台前后端/yudao-cloud/sql/mysql");
const migrationPath = join(root, "migrations/V022__trade_fulfillment_admin_permissions.sql");
const baselinePath = join(root, "oakved-baseline.sql");
const permissions = [
  "trade:fulfillment:shipment:query",
  "trade:fulfillment:shipment:create",
  "trade:fulfillment:shipment:update",
  "trade:fulfillment:shipment:dispatch",
  "trade:fulfillment:tracking:manual",
];

describe("V022 fulfillment admin permission migration", () => {
  it("adds five idempotent button permissions under the order list", () => {
    const sql = readFileSync(migrationPath, "utf8");
    for (const permission of permissions) {
      expect(sql).toContain(`'${permission}'`);
    }
    expect(sql.match(/INSERT INTO `system_menu`/g)).toHaveLength(5);
    expect(sql.match(/`type`, `sort`, `parent_id`/g)).toHaveLength(5);
    expect(sql).not.toMatch(/INSERT INTO `system_role_menu`|role_id|tenant_id|credential|secret|api[_-]?key/i);
  });

  it("fails closed unless the order-list parent has the exact published shape", () => {
    const sql = readFileSync(migrationPath, "utf8");
    const normalized = sql.replace(/\s+/g, " ");

    expect(normalized).toContain(
      "INSERT INTO `trade_fulfillment_menu_guard` (`valid`) SELECT 0 WHERE (SELECT COUNT(*) FROM `system_menu` " +
      "WHERE `id` = 2076 AND `name` = '订单列表' AND `type` = 2 AND `parent_id` = 2072 AND `path` = 'order' " +
      "AND `component` = 'mall/trade/order/index' AND `component_name` = 'TradeOrder' AND `status` = 0 " +
      "AND `deleted` = b'0') <> 1;",
    );
    expect(sql).toContain("CONSTRAINT `chk_trade_fulfillment_menu_guard` CHECK (`valid` = 1)");
  });

  it("fails closed when an existing permission is duplicated or attached to the wrong parent or type", () => {
    const sql = readFileSync(migrationPath, "utf8");
    const normalized = sql.replace(/\s+/g, " ");

    for (const permission of permissions) {
      expect(normalized).toContain(
        `(SELECT COUNT(*) FROM \`system_menu\` WHERE \`permission\` = '${permission}' AND \`deleted\` = b'0') <> 1 ` +
        `OR (SELECT COUNT(*) FROM \`system_menu\` WHERE \`permission\` = '${permission}' ` +
        "AND `parent_id` = 2076 AND `type` = 3 AND `deleted` = b'0') <> 1",
      );
    }
  });

  it("keeps the generated baseline V022 section byte-equivalent", () => {
    const migration = readFileSync(migrationPath, "utf8").replace(/\r\n/g, "\n").replace(/\s+$/, "") + "\n";
    const baseline = readFileSync(baselinePath, "utf8").replace(/\r\n/g, "\n");
    const marker = "-- BEGIN V022__trade_fulfillment_admin_permissions.sql\n";
    const start = baseline.indexOf(marker) + marker.length;
    const end = baseline.indexOf("\n-- BEGIN Oakved demo catalog", start);
    expect(start).toBeGreaterThan(marker.length - 1);
    expect(end).toBeGreaterThan(start);
    expect(baseline.slice(start, end).replace(/\s+$/, "") + "\n").toBe(migration);
  });
});
