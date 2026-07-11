import { describe, expect, it } from "vitest";
import { existsSync, readFileSync } from "node:fs";

const seed = readFileSync(new URL("../../seed-furniture-agent-products.ps1", import.meta.url), "utf8");

describe("tenant 121 product seed", () => {
  it("contains 26 readable unique products and an audit phase", () => {
    expect((seed.match(/ensureProduct\(connection/g) || []).length).toBe(26);
    expect(seed).not.toMatch(/ensureProduct\([\s\S]{0,1000}"\\u[0-9a-fA-F]{4}/);
    expect(seed).toContain("auditCatalog(connection)");
    expect(seed).toContain("update system_tenant set expire_time");
  });

  it("resolves Java and the JDBC driver for the current workstation", () => {
    expect(seed).toContain("Get-Command javac.exe");
    expect(seed).toContain("$env:USERPROFILE");
    expect(seed).not.toContain("C:\\Users\\admin");
    expect(seed).not.toContain("D:\\code\\tools");
  });
});

describe("tenant 121 product audit", () => {
  it("ships a read-only audit script with every catalog invariant", () => {
    const auditUrl = new URL("../../audit-furniture-agent-products.ps1", import.meta.url);
    expect(existsSync(auditUrl)).toBe(true);
    const audit = readFileSync(auditUrl, "utf8");
    expect(audit).toContain("active_products");
    expect(audit).toContain("distinct_covers");
    expect(audit).toContain("invalid_product_fields");
    expect(audit).toContain("tenant_mismatch");
    expect(audit).toContain("sku_mismatch");
    expect(audit).toContain("Get-ChildItem -LiteralPath $workspace -Directory");
    expect(audit).not.toContain("yudao电商管理平台前后端");
    expect(audit).not.toMatch(/\b(update|delete|insert|replace)\b/i);
  });
});
