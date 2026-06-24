import { describe, expect, it } from "vitest";
import { demoProducts } from "../src/data/demoProducts.js";
import {
  buildProductListingModel,
  inferListingType,
  productFacetGroups,
  productListingFilters,
  productListingQueryFilterLabels,
  productListingQueryFilters,
  resolveProductListingQuery,
} from "../src/services/productListingModel.js";

describe("product listing model", () => {
  it("exposes calm storefront filters for the product listing page", () => {
    expect(productListingFilters.map((filter) => filter.value)).toEqual([
      "all",
      "nightstand",
      "bed-bench",
      "dresser",
      "vanity",
      "desk",
      "round-table",
      "single-sofa",
      "chair",
    ]);
  });

  it("filters by furniture type and sorts with showroom-friendly defaults", () => {
    const listing = buildProductListingModel(demoProducts, {
      filter: "nightstand",
      sort: "price-desc",
    });

    expect(listing.products.map((product) => product.productType)).toEqual(["nightstand"]);
    expect(listing.summary).toMatchObject({
      productCount: 1,
      collectionCount: 1,
      heroImage: "/assets/generated-furniture/home-module-002-bedroom-desktop.webp",
    });
  });

  it("keeps all products and sorts by price ascending when requested", () => {
    const listing = buildProductListingModel(demoProducts, {
      filter: "all",
      sort: "price-asc",
    });

    expect(listing.products.map((product) => product.price)).toEqual([899, 1299, 1499, 1899, 2199, 2499, 3299, 3599]);
    expect(listing.summary.productCount).toBe(8);
  });

  it("maps navigation query strings to existing listing filters and facets", () => {
    expect(resolveProductListingQuery("?category=nightstand")).toMatchObject({
      filter: "nightstand",
      facets: {},
    });
    expect(resolveProductListingQuery("?category=storage")).toMatchObject({
      filter: "storage",
      facets: {},
    });
    expect(resolveProductListingQuery("?category=desk-table")).toMatchObject({
      filter: "desk-table",
      facets: {},
    });
    expect(resolveProductListingQuery("?category=seating")).toMatchObject({
      filter: "seating",
      facets: {},
    });
    expect(resolveProductListingQuery("?room=bedroom")).toMatchObject({
      filter: "bedroom-room",
      facets: {},
    });
    expect(resolveProductListingQuery("?material=walnut")).toMatchObject({
      filter: "all",
      facets: { material: "wood", color: "brown" },
    });
    expect(resolveProductListingQuery("?tag=in-stock")).toMatchObject({
      filter: "all",
      facets: { availability: "in-stock" },
    });
  });

  it("supports navigation group filters without adding extra visible product tabs", () => {
    expect(productListingQueryFilters).toEqual(
      expect.arrayContaining(["storage", "desk-table", "seating", "bedroom-room", "study", "living"]),
    );
    expect(productListingQueryFilterLabels).toMatchObject({
      storage: "Storage Cabinets",
      "desk-table": "Desks & Tables",
      seating: "Seating & Benches",
      "bedroom-room": "Bedroom Furniture",
    });

    expect(
      buildProductListingModel(demoProducts, {
        filter: "storage",
      }).products.map((product) => product.productType),
    ).toEqual(["nightstand", "dresser"]);
    expect(
      buildProductListingModel(demoProducts, {
        filter: "desk-table",
      }).products.map((product) => product.productType),
    ).toEqual(["round-table", "vanity", "desk"]);
    expect(
      buildProductListingModel(demoProducts, {
        filter: "seating",
      }).products.map((product) => product.productType),
    ).toEqual(["single-sofa", "bed-bench", "chair"]);
    expect(
      buildProductListingModel(demoProducts, {
        filter: "bedroom-room",
      }).summary.productCount,
    ).toBe(8);
  });

  it("supports launch-ready faceted filtering by material, color, availability and price", () => {
    expect(productFacetGroups.map((group) => group.key)).toEqual(["material", "color", "availability", "price"]);

    const listing = buildProductListingModel(demoProducts, {
      filter: "all",
      sort: "featured",
      facets: {
        material: "wood",
        color: "brown",
        availability: "low-stock",
        price: "1500-3500",
      },
    });

    expect(listing.products.map((product) => product.name)).toEqual(["Walnut Writing Desk"]);
    expect(listing.summary).toMatchObject({
      productCount: 1,
      activeFacetCount: 4,
    });
  });

  it("infers listing filter type from remote product names when productType is missing", () => {
    const listing = buildProductListingModel(
      [
        { name: "Cloud Sofa", subtitle: "Deep sectional", price: 3200 },
        { name: "Brass Pendant Lamp", subtitle: "Warm dining light", price: 900 },
      ],
      {
        filter: "single-sofa",
        sort: "featured",
      },
    );

    expect(listing.products.map((product) => product.name)).toEqual(["Cloud Sofa"]);
    expect(listing.summary.collectionCount).toBe(1);
  });

  it("keeps explicit company productType ahead of descriptive words", () => {
    expect(
      inferListingType({
        productType: "chair",
        name: "Bedroom Side Chair",
        subtitle: "Wood-framed chair for vanity, desk or lounge pairing",
      }),
    ).toBe("chair");
    expect(
      inferListingType({
        productType: "round-table",
        name: "Round Oak Table",
        subtitle: "Small table for bedside or lounge use",
      }),
    ).toBe("round-table");
  });
});
