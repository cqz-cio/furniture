import { describe, expect, it } from "vitest";
import { existsSync, readFileSync } from "node:fs";

const schemaUrl = new URL(
  "../../yudao电商管理平台前后端/yudao-cloud/sql/mysql/mall-erp-integration.sql",
  import.meta.url,
);

describe("mall ERP integration schema", () => {
  it("defines ERP product, stock and mall mapping tables", () => {
    expect(existsSync(schemaUrl)).toBe(true);
    const sql = readFileSync(schemaUrl, "utf8");
    [
      "erp_product_unit",
      "erp_product_category",
      "erp_product",
      "erp_warehouse",
      "erp_stock",
      "mall_erp_product_mapping",
      "mall_erp_sync_log",
    ].forEach((table) => expect(sql).toContain(`CREATE TABLE IF NOT EXISTS \`${table}\``));
    expect(sql).toContain("tenant_id");
    expect(sql).toContain("uk_mall_erp_mapping_tenant_sku_deleted");
    expect(sql).toContain("uk_mall_erp_mapping_tenant_erp_product_deleted");
    expect(sql).toContain("uk_mall_erp_mapping_tenant_erp_code_deleted");
  });
});
