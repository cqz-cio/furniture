import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

import { describe, expect, it } from "vitest";

import { buildRoomCatalog } from "../src/services/productCategoryModel.js";
import {
  buildProductDetailModel,
  buildProductInformation,
  formatProductDimension,
} from "../src/services/productDetailModel.js";
import { inferListingType } from "../src/services/productListingModel.js";
import { mapSpuToProduct } from "../src/services/yudaoMappers.js";

const phase1MigrationPath = fileURLToPath(
  new URL(
    "../../yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V048__product_category_and_detail_contract.sql",
    import.meta.url,
  ),
);
const oakvedDemoSeedPath = fileURLToPath(
  new URL(
    "../../yudao电商管理平台前后端/yudao-cloud/sql/mysql/oakved-demo-data.sql",
    import.meta.url,
  ),
);
const productDetailPageSource = readFileSync(
  new URL("../src/pages/SofaPdpPage.vue", import.meta.url),
  "utf8",
);

const categoryTree = [
  {
    id: 10,
    parentId: 0,
    code: "dining-room",
    name: "Dining Room Furniture",
    sort: 10,
    status: 0,
    children: [
      { id: 101, parentId: 10, code: "dining-chair", name: "DINING CHAIRS", sort: 10, status: 0 },
      { id: 102, parentId: 10, code: "bar-stool", name: "BAR STOOLS", sort: 20, status: 0 },
      { id: 103, parentId: 10, code: "dining-table", name: "DINING TABLES", sort: 30, status: 0 },
    ],
  },
  {
    id: 20,
    parentId: 0,
    code: "living-room",
    name: "Living Room Furniture",
    sort: 20,
    status: 0,
    children: [
      { id: 201, parentId: 20, code: "sofa", name: "SOFA & OCCASIONAL CHAIR", sort: 10, status: 0 },
      { id: 202, parentId: 20, code: "coffee-table", name: "SIDE TABLE & COFFEE TABLE", sort: 20, status: 0 },
      { id: 203, parentId: 20, code: "bookcase", name: "BOOKCASE & DISPLAY CABINET", sort: 30, status: 0 },
      { id: 204, parentId: 20, code: "media-console", name: "CONSOLE TABLE & BUFFET", sort: 40, status: 0 },
    ],
  },
  {
    id: 30,
    parentId: 0,
    code: "bedroom",
    name: "Bedroom Furniture",
    sort: 30,
    status: 0,
    children: [
      { id: 301, parentId: 30, code: "bed", name: "BED & HEADBOARD", sort: 10, status: 0 },
      { id: 302, parentId: 30, code: "nightstand", name: "BEDSIDE TABLE", sort: 20, status: 0 },
      { id: 303, parentId: 30, code: "dresser", name: "CHEST OF DRAWERS", sort: 30, status: 0 },
      { id: 304, parentId: 30, code: "bench", name: "BENCH", sort: 40, status: 0 },
      { id: 305, parentId: 30, code: "dressing-table", name: "DRESSING TABLE", sort: 50, status: 0 },
      { id: 306, parentId: 30, code: "wardrobe", name: "WARDROBE", sort: 60, status: 0 },
    ],
  },
];

describe("Phase 1 product category contract", () => {
  it("builds all 13 Product types from the public category tree without a second hardcoded list", () => {
    const catalog = buildRoomCatalog(categoryTree);

    expect(catalog.map((room) => room.code)).toEqual(["dining-room", "living-room", "bedroom"]);
    expect(catalog.flatMap((room) => room.productTypes.map((type) => type.code))).toEqual([
      "dining-chair",
      "bar-stool",
      "dining-table",
      "sofa",
      "coffee-table",
      "bookcase",
      "media-console",
      "bed",
      "nightstand",
      "dresser",
      "bench",
      "dressing-table",
      "wardrobe",
    ]);
    expect(catalog.flatMap((room) => room.productTypes.map((type) => type.name))).toContain("DINING CHAIRS");
    expect(catalog.flatMap((room) => room.productTypes.map((type) => type.name))).toContain("WARDROBE");
  });

  it("uses the stable P2 categoryCode and never guesses an API type from a display name", () => {
    expect(
      mapSpuToProduct({
        id: 1,
        name: "Bench with Dining Table Copy",
        categoryCode: "bench",
        categoryName: "A RENAMED DISPLAY LABEL",
      }).productType,
    ).toBe("bench");

    const unmapped = mapSpuToProduct({
      id: 2,
      name: "Dining Table Looking Product",
      categoryName: "DINING TABLES",
    });
    expect(unmapped.productType).toBe("uncategorized");
    expect(inferListingType(unmapped)).toBe("uncategorized");
    expect(unmapped.material).toBe("");
    expect(unmapped.color).toBe("");
  });

  it("preserves policy-hidden API numbers as null instead of fabricating zero values", () => {
    const product = mapSpuToProduct({
      id: 3,
      categoryCode: "sofa",
      price: null,
      marketPrice: null,
      stock: null,
      salesCount: null,
      skus: [{ id: 30, price: null, marketPrice: null, stock: null }],
      displayPolicy: {
        fields: { price: false, marketPrice: false, inventory: false, salesCount: false },
      },
    });

    expect(product).toMatchObject({
      price: null,
      marketPrice: null,
      stock: null,
      salesCount: null,
    });
    expect(buildProductDetailModel(product)).toMatchObject({ price: null, stock: null });
  });
});

describe("Phase 1 product detail contract", () => {
  it("formats dimensions once and builds Product information in the canonical order", () => {
    expect(
      formatProductDimension({ shape: "round", diameter: 140, height: 78, unit: "cm" }),
    ).toBe("Dia 140 x H 78 cm");
    expect(
      formatProductDimension({ shape: "rectangular", width: 55, depth: 54, height: 95, unit: "cm" }),
    ).toBe("W 55 x D 54 x H 95 cm");

    const information = buildProductInformation(
      {
        itemNo: "VZC0099",
        material: "Oak / Fabric",
        dimension: { shape: "rectangular", width: 55, depth: 54, height: 95, unit: "cm" },
        color: "Natural",
        finish: "",
        service: "OEM & ODM",
        sample: "Available",
        packing: "Ships in two cartons",
      },
      { fields: { finish: false } },
    );

    expect(information).toEqual([
      { key: "itemNo", label: "Item No.", value: "VZC0099" },
      { key: "material", label: "Material", value: "Oak / Fabric" },
      { key: "dimension", label: "Size", value: "W 55 x D 54 x H 95 cm" },
      { key: "color", label: "Color", value: "Natural" },
      { key: "service", label: "Service", value: "OEM & ODM" },
      { key: "sample", label: "Sample", value: "Available" },
      { key: "packing", label: "Packing", value: "Ships in two cartons" },
    ]);
  });

  it("keeps sparse API products empty instead of injecting Demo marble content", () => {
    const model = buildProductDetailModel({
      id: 88,
      name: "Rustic Dining Table",
      source: "yudao",
      productType: "dining-table",
      detailConfig: {
        collection: "",
        heroNote: "",
        fabricSelector: null,
        highlights: [],
        optionGroups: [],
        accordions: [],
        relatedLinks: [],
        finish: "",
        packing: "Ships in two cartons",
      },
      displayPolicy: {
        source: "erp-tenant",
        fields: {
          collection: true,
          heroNote: true,
          fabricSelector: true,
          highlights: true,
          optionGroups: true,
          accordions: true,
          relatedLinks: true,
          finish: true,
          packing: true,
        },
      },
    });

    expect(model).toMatchObject({
      source: "yudao",
      collection: "",
      heroNote: "",
      fabricSelector: null,
      highlights: [],
      optionGroups: [],
      accordions: [],
      relatedLinks: [],
    });
    expect(model.productInformation).toEqual([
      { key: "packing", label: "Packing", value: "Ships in two cartons" },
    ]);
    expect(JSON.stringify(model)).not.toMatch(/Marble|Carrara|220 cm|260 cm|Stone care/i);
  });

  it("retains rich templates only for explicitly marked Demo products", () => {
    const model = buildProductDetailModel({
      name: "Demo Dining Table",
      source: "demo",
      productType: "dining-table",
    });

    expect(model.collection).toBe("MARBLE DINING COLLECTION");
    expect(model.optionGroups.length).toBeGreaterThan(0);
  });

  it("guards the mobile purchase price container when an API policy hides price", () => {
    expect(productDetailPageSource).toContain('<div v-if="detail.price">');
  });
});

describe("Phase 1 V048 migration", () => {
  it("adds an active-record category code key, the 13-value matrix, and deterministic packing migration", () => {
    const sql = readFileSync(phase1MigrationPath, "utf8");

    expect(sql).toMatch(/ADD COLUMN `code` varchar\(64\)/i);
    expect(sql).toMatch(/ADD COLUMN `active_record`[\s\S]*GENERATED ALWAYS AS/i);
    expect(sql).toMatch(/`tenant_id`, `parent_id`, `code`, `active_record`/i);
    for (const code of categoryTree.flatMap((room) => room.children.map((child) => child.code))) {
      expect(sql).toContain(`'${code}'`);
    }
    expect(sql).toContain("DINING CHAIRS");
    expect(sql).toContain("WARDROBE");
    expect(sql).toMatch(/tenant\.`code`\s*=\s*'OAKVED'/i);
    expect(sql).not.toMatch(/\bDING CHAIRS\b|\bDING TABLES\b|Wadrobe/);
    expect(sql).toMatch(/IN \('chair', 'sideboard'\)\s+THEN NULL/i);
    expect(sql).toMatch(/packingDisplay[\s\S]*JSON_TYPE[\s\S]*packing/i);
    expect(sql).not.toMatch(/\bproduct\.`name`\b|\bp\.`name`\b/i);
  });

  it("keeps the post-V048 empty-database demo seed compatible with required category codes", () => {
    const sql = readFileSync(oakvedDemoSeedPath, "utf8");

    expect(sql).toMatch(/INSERT INTO product_category\(parent_id,code,name/i);
    expect(sql).toMatch(/ensure_oakved_category\(IN category_code/i);
    expect(sql).toContain("'demo-sofas','Sofas'");
    expect(sql).toMatch(/CASE category_name[\s\S]*WHEN 'Sofas' THEN 'sofa'/i);
    expect(sql).toMatch(/FROM product_spu (?:AS )?seeded_product[\s\S]*seeded_product\.creator=@seed_user/i);
  });
});
