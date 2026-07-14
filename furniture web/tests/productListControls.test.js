import { describe, expect, it } from "vitest";
import {
  applyProductListControls,
  buildProductTypeOptions,
  normalizeProductTypeLabel,
} from "../src/services/productListControls.js";

const products = [
  {
    id: "sofa",
    name: "Cloud Modular Sofa",
    subtitle: "Low deep seating",
    price: 3299,
    productType: "sofa",
  },
  {
    id: "bed",
    name: "Oak Shelter Bed",
    subtitle: "Smoked oak frame",
    price: 2599,
    productType: "bed",
  },
  {
    id: "lighting",
    name: "Brass Pendant",
    subtitle: "Linen shade",
    price: 899,
    productType: "lighting",
  },
];

describe("product list controls", () => {
  it("builds stable product type options from available products", () => {
    expect(buildProductTypeOptions(products)).toEqual([
      { value: "bed", label: "Bed" },
      { value: "lighting", label: "Lighting" },
      { value: "sofa", label: "Sofa" },
    ]);
  });

  it("filters by search query and product type before sorting", () => {
    const result = applyProductListControls(products, {
      query: "oak",
      productType: "bed",
      sort: "priceDesc",
    });

    expect(result.map((product) => product.id)).toEqual(["bed"]);
  });

  it("sorts by price without mutating the source collection", () => {
    const result = applyProductListControls(products, {
      query: "",
      productType: "all",
      sort: "priceAsc",
    });

    expect(result.map((product) => product.id)).toEqual(["lighting", "bed", "sofa"]);
    expect(products.map((product) => product.id)).toEqual(["sofa", "bed", "lighting"]);
  });

  it("returns an empty result for unmatched searches", () => {
    expect(applyProductListControls(products, { query: "sectional", productType: "bed", sort: "featured" })).toEqual([]);
  });

  it("normalizes product type labels for buyer-facing controls", () => {
    expect(normalizeProductTypeLabel("dining-table")).toBe("Dining Table");
  });
});
