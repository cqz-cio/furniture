import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("purchase controls", () => {
  it("disables list add-to-cart actions when products are unavailable", () => {
    const source = readSource("../src/pages/SofasPlpPage.vue");

    expect(source).toContain("const isProductAvailable = (product)");
    expect(source).toContain(":disabled=\"!isProductAvailable(product)\"");
    expect(source).toContain(":aria-disabled=\"!isProductAvailable(product)\"");
    expect(source).toContain("product-card-unavailable");
  });

  it("normalizes PDP purchase quantity before adding to cart", () => {
    const source = readSource("../src/pages/SofaPdpPage.vue");

    expect(source).toContain("const maxPurchaseQuantity = computed");
    expect(source).toContain("const normalizedPurchaseQuantity = computed");
    expect(source).toContain("const handleAddToCart = (event) =>");
    expect(source).toContain("trigger: event?.currentTarget");
    expect(source).toContain("Math.min(Math.floor(Number(quantity.value) || 1), maxPurchaseQuantity.value)");
    expect(source).toContain("@change=\"quantity = normalizedPurchaseQuantity\"");
    expect(source).toContain(":max=\"maxPurchaseQuantity\"");
    expect(source).toContain(":disabled=\"!canPurchase\"");
    expect(source.match(/<input(?:(?!<input)[\s\S])*?:disabled="!canPurchase"(?:(?!<input)[\s\S])*?@change="quantity = normalizedPurchaseQuantity"/g)).toHaveLength(2);
    expect(source).not.toContain("@click=\"emit('add-to-cart', product, quantity)\"");
  });
});
