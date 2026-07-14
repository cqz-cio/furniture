import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const source = readFileSync(new URL("../src/pages/SofasPlpPage.vue", import.meta.url), "utf8");

describe("sofas PLP browse controls", () => {
  it("wires search, type filter, sort, counts, and empty recovery into the product grid", () => {
    expect(source).toContain("buildProductListingModel");
    expect(source).toContain("resolveProductListingQuery");
    expect(source).not.toContain("supplementMissingCompanyTypes");
    expect(source).not.toContain("demoProducts");
    expect(source).toContain("const searchQuery = ref(\"\")");
    expect(source).toContain("const selectedProductType = ref(initialListingQuery.filter)");
    expect(source).toContain("const mobileFiltersOpen = ref(false)");
    expect(source).toContain("emptyFacetState");
    expect(source).toContain("const selectedSort = ref(\"featured\")");
    expect(source).toContain("const visibleProducts = computed");
    expect(source).toContain("productStockLabel");
    expect(source).toContain("const resetProductListControls = () =>");
    expect(source).toContain("window.addEventListener(\"oakved:navigation\", syncListingQueryFromLocation)");
    expect(source).toContain("v-model=\"searchQuery\"");
    expect(source).toContain("v-model=\"selectedProductType\"");
    expect(source).toContain("v-model=\"selectedSort\"");
    expect(source).toContain("product-mobile-filter-toggle");
    expect(source).toContain("product-facet-shell");
    expect(source).toContain("product-list-confidence");
    expect(source).toContain("product-card-badge");
    expect(source).toContain(":hover-src=\"product.gallery?.[0]\"");
    expect(source).toContain("product-editorial-tile");
    expect(source).toContain("v-for=\"product in visibleProducts\"");
    expect(source).toContain("product-list-empty");
    expect(source).toContain("@click=\"resetProductListControls\"");
  });
});
