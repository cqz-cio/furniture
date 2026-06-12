import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const source = readFileSync(new URL("../src/pages/SofasPlpPage.vue", import.meta.url), "utf8");

describe("sofas PLP browse controls", () => {
  it("wires search, type filter, sort, counts, and empty recovery into the product grid", () => {
    expect(source).toContain("applyProductListControls");
    expect(source).toContain("buildProductTypeOptions");
    expect(source).toContain("const searchQuery = ref(\"\")");
    expect(source).toContain("const selectedProductType = ref(\"all\")");
    expect(source).toContain("const selectedSort = ref(\"featured\")");
    expect(source).toContain("const visibleProducts = computed");
    expect(source).toContain("const resetProductListControls = () =>");
    expect(source).toContain("v-model=\"searchQuery\"");
    expect(source).toContain("v-model=\"selectedProductType\"");
    expect(source).toContain("v-model=\"selectedSort\"");
    expect(source).toContain("v-for=\"product in visibleProducts\"");
    expect(source).toContain("product-list-empty");
    expect(source).toContain("@click=\"resetProductListControls\"");
  });
});
