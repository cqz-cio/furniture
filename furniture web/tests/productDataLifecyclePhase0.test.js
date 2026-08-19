import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

import {
  assertReadOnlySql,
  auditSections,
  buildAuditQueries,
  parseAuditArgs,
  renderAuditMarkdown,
} from "../scripts/product-data-lifecycle-audit.mjs";
import { buildProductDetailModel } from "../src/services/productDetailModel.js";
import { mapSpuToProduct } from "../src/services/yudaoMappers.js";

const repositoryRoot = new URL("../../", import.meta.url);
const cloudRoot = new URL(
  "yudao电商管理平台前后端/yudao-cloud/",
  repositoryRoot,
);
const readCloud = (path) => readFileSync(new URL(path, cloudRoot), "utf8");

describe("product data lifecycle Phase 0 audit contract", () => {
  it("covers every read-only risk list required by PRD section 9.1", () => {
    expect(auditSections.map(({ key }) => key)).toEqual([
      "missing_p2_product_type",
      "unknown_product_type",
      "legacy_packing_shapes",
      "unverified_default_finish",
      "erp_mapping_integrity",
      "furniture_projection_integrity",
      "orphan_seo_records",
      "erp_soft_delete_unique_key_risks",
    ]);

    for (const section of auditSections) {
      expect(section.title).toBeTruthy();
      expect(section.requirement).toBeTruthy();
      expect(section.issueTypes.length).toBeGreaterThan(0);
      expect(new Set(section.issueTypes).size).toBe(section.issueTypes.length);
      expect(section.findingsSql).toContain("{{TENANT_ID}}");
      expect(() => assertReadOnlySql(section.findingsSql)).not.toThrow();
    }
  });

  it("builds bounded tenant-aware count and record queries without mutation statements", () => {
    for (const section of auditSections) {
      const queries = buildAuditQueries(section, { tenantId: 121, limit: 50 });

      expect(queries.countSql).toContain("SELECT COUNT(*)");
      expect(queries.breakdownSql).toContain("GROUP BY issue_type");
      expect(queries.detailSql).toContain("LIMIT 50");
      expect(queries.countSql).not.toContain("{{TENANT_ID}}");
      expect(queries.detailSql).not.toContain("{{TENANT_ID}}");
      expect(queries.countSql).toContain("121");
      expect(() => assertReadOnlySql(queries.countSql)).not.toThrow();
      expect(() => assertReadOnlySql(queries.breakdownSql)).not.toThrow();
      expect(() => assertReadOnlySql(queries.detailSql)).not.toThrow();
    }
  });

  it("switches the P2 audit to stable category codes once V048 is present", () => {
    const section = auditSections.find(({ key }) => key === "missing_p2_product_type");

    expect(section.findingsSqlWithCategoryCode).toContain("c.code NOT IN");
    expect(section.findingsSqlWithCategoryCode).toContain("parent.code NOT IN");
    expect(section.findingsSqlWithCategoryCode).toContain("'noncanonical_p2'");
    expect(() => assertReadOnlySql(section.findingsSqlWithCategoryCode)).not.toThrow();
  });

  it("rejects unsafe tenant, limit and output arguments", () => {
    expect(parseAuditArgs(["--tenant", "121", "--limit", "25", "--output", "phase-0.md"]))
      .toMatchObject({ tenantId: 121, limit: 25, outputPath: "phase-0.md" });
    expect(() => parseAuditArgs(["--tenant", "121 OR 1=1"])).toThrow(/tenant/i);
    expect(() => parseAuditArgs(["--limit", "0"])).toThrow(/limit/i);
    expect(() => assertReadOnlySql("UPDATE product_spu SET status = 0")).toThrow(/read-only/i);
  });

  it("renders counts, truncation and record details in a reviewable report", () => {
    const markdown = renderAuditMarkdown({
      schemaVersion: 1,
      generatedAt: "2026-08-14T00:00:00.000Z",
      tenantId: 121,
      source: {
        database: "ruoyi-vue-pro",
        repositoryMigration: "V047__clean_orphan_mall_erp_mappings.sql",
        databaseMigration: "V046__customer_specification_fields.sql",
      },
      sections: auditSections.map((section, index) => ({
        key: section.key,
        title: section.title,
        requirement: section.requirement,
        totalCount: index,
        breakdown: Object.fromEntries(section.issueTypes.map((issueType) => [
          issueType,
          index === 1 && issueType === "ambiguous_manual_review" ? 1 : 0,
        ])),
        records: index === 1 ? [{ spuId: 9, productType: "chair", issueType: "ambiguous_manual_review" }] : [],
        truncated: index > 2,
      })),
    });

    expect(markdown).toContain("# Product data lifecycle Phase 0 audit");
    expect(markdown).toContain("V047__clean_orphan_mall_erp_mappings.sql");
    expect(markdown).toContain("V046__customer_specification_fields.sql");
    expect(markdown).toContain("unknown_product_type");
    expect(markdown).toContain("ambiguous_manual_review");
    expect(markdown).toContain("chair");
    expect(markdown).toContain("truncated");
  });
});

describe("lifecycle breakpoint regression coverage", () => {
  it("uses stable category and detail fields without display-name inference", () => {
    const product = mapSpuToProduct({
      id: 41,
      name: "Walnut Dining Table",
      categoryId: 7,
      categoryCode: "dining-table",
      categoryName: "DINING TABLES",
      detailConfig: { material: "Oak", color: "Brown" },
      skus: [{ id: 410, stock: 3 }],
    });

    expect(product.productType).toBe("dining-table");
    expect(product.material).toBe("Oak");
    expect(product.color).toBe("Brown");
  });

  it("keeps a sparse production API product free of Demo template content", () => {
    const product = buildProductDetailModel({
      id: 42,
      source: "yudao",
      name: "Rustic Dining Table",
      productType: "round-table",
      detailConfig: {},
      stock: 0,
    });

    expect(product.collection).toBe("");
    expect(product.optionGroups).toEqual([]);
    expect(product.accordions).toEqual([]);
    expect(JSON.stringify(product)).not.toMatch(/Marble|220 cm|Stone care/i);
  });

  it("records the create-only AFTER_COMMIT ERP initialization trigger", () => {
    const spuService = readCloud(
      "yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/spu/ProductSpuServiceImpl.java",
    );
    const listener = readCloud(
      "yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/spu/event/ProductErpInitializationListener.java",
    );
    const updateBlock = spuService.slice(
      spuService.indexOf("public void updateSpu"),
      spuService.indexOf("private void initSpuFromSkus"),
    );

    expect(spuService).toContain("eventPublisher.publishEvent(new ProductSpuCreatedEvent(spu.getId()))");
    expect(updateBlock).toContain("productSkuService.updateSkuList");
    expect(updateBlock).not.toContain("publishEvent");
    expect(listener).toContain("@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)");
  });

  it("records all-SKU mapping visibility and local order stock mutation", () => {
    const appController = readCloud(
      "yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/controller/app/spu/AppProductSpuController.java",
    );
    const orderHandler = readCloud(
      "yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/order/handler/TradeProductSkuOrderHandler.java",
    );

    expect(appController).toContain("skus.stream().allMatch(sku -> mappedSkuIds.contains(sku.getId()))");
    expect(orderHandler).toContain("productSkuApi.updateSkuStock(TradeOrderConvert.INSTANCE.convertNegative(orderItems))");
    expect(orderHandler).toContain("productSkuApi.updateSkuStock(TradeOrderConvert.INSTANCE.convert(orderItems))");
  });
});
