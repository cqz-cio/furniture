import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("cart drawer navigation", () => {
  it("keeps shoppers on the current page after add-to-cart", () => {
    const source = readSource("../src/App.vue");
    const addToCartBody = source.slice(source.indexOf("const addToCart = async"), source.indexOf("const updateCartQuantity"));

    expect(addToCartBody).toContain("addLocalCartItem");
    expect(addToCartBody).toContain("addCartItem");
    expect(addToCartBody).not.toContain("cartOpen.value = true");
  });

  it("renders cart category labels as navigable links", () => {
    const source = readSource("../src/components/CartDrawer.vue");

    expect(source).toContain("cartPrimaryNavItems");
    expect(source).toContain(':href="item.href"');
    expect(source).toContain("cart-nav-link");
    expect(source).not.toContain('<span v-for="item in cartNavItems"');
  });

  it("links merchandise cart items to their product detail pages", () => {
    const source = readSource("../src/components/CartDrawer.vue");

    expect(source).toContain("cartItemDetailHref");
    expect(source).toContain('v-if="cartItemDetailHref(item)"');
    expect(source).toContain(':href="cartItemDetailHref(item)"');
    expect(source).toContain("cart-item-media-link");
    expect(source).toContain("cart-item-title-link");
  });
});
