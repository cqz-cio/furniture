import { describe, expect, it } from "vitest";
import { existsSync, readFileSync } from "node:fs";

const root = new URL("../../", import.meta.url);
const read = (name) => readFileSync(new URL(name, root), "utf8");

describe("tenant 121 mall ERP bootstrap", () => {
  it("creates an idempotent one-to-one ERP mapping for all 26 mall SKUs", () => {
    const url = new URL("seed-mall-erp-products.ps1", root);
    expect(existsSync(url)).toBe(true);
    const seed = read("seed-mall-erp-products.ps1");
    expect(seed).toContain("$TenantId = 121");
    expect(seed).toContain("CONCAT('RH-', 121, '-', s.id)");
    expect(seed).toContain("mall_erp_product_mapping");
    expect(seed).toContain("erp_stock");
    expect(seed).toContain("product_sku");
    expect(seed).toContain("$mallSkuCount -ne 26");
    expect(seed).toContain("select id from mall_erp_product_mapping");
    expect(seed).not.toMatch(/tenant_id\s*=\s*1\b/i);
    expect(seed).not.toMatch(/\b(delete|truncate|drop)\b/i);
  });

  it("ships a read-only audit for counts, orphans, tenants and warehouse stock", () => {
    const url = new URL("audit-mall-erp-integration.ps1", root);
    expect(existsSync(url)).toBe(true);
    const audit = read("audit-mall-erp-integration.ps1");
    ["active_mall_skus", "erp_products", "unique_mappings", "orphan_mappings",
      "cross_tenant_mappings", "stock_without_warehouse"].forEach((field) =>
      expect(audit).toContain(field));
    expect(audit).not.toMatch(/\b(update|delete|insert|replace|truncate|drop)\b/i);
  });
});
