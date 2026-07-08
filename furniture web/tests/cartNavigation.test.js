import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("cart drawer navigation", () => {
  it("hides utility actions in the full cart header while keeping the close control elsewhere", () => {
    const source = readSource("../src/components/CartDrawer.vue");

    expect(source).toContain("cart-topline-spacer");
    expect(source).not.toContain("cart-header-actions");
    expect(source).toContain('class="cart-close"');
  });

  it("turns the membership card into an add-to-cart action", () => {
    const source = readSource("../src/components/CartDrawer.vue");

    expect(source).toContain('defineEmits(["checkout", "close", "resync", "update-quantity", "remove", "wishlist", "add-membership"])');
    expect(source).toContain("hasMembershipServiceItem");
    expect(source).toContain('@click="emit(\'add-membership\')"');
    expect(source).toContain(':disabled="hasMembershipServiceItem"');
    expect(source).toContain('hasMembershipServiceItem ? t("cart.membership.added") : t("cart.membership.add")');
  });

  it("wires the membership card to an idempotent cart insertion in App", () => {
    const source = readSource("../src/App.vue");

    expect(source).toContain("ANNUAL_MEMBERSHIP_PRODUCT");
    expect(source).toContain("hasMembershipService");
    expect(source).toContain("const addMembershipToCart = () =>");
    expect(source).toContain("if (hasMembershipService(cartItems.value)) return;");
    expect(source).toContain("addLocalCartItem(cartItems.value, ANNUAL_MEMBERSHIP_PRODUCT, 1)");
    expect(source).toContain('@add-membership="addMembershipToCart"');
  });

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

  it("continues to keep the checkout shell free of the shared site header", () => {
    const source = readSource("../src/App.vue");

    expect(source).toContain("const usesCheckoutShell = computed(() => currentPage.value === \"checkout\")");
    expect(source).toContain("<RhHeader");
    expect(source).toContain('v-if="!usesCheckoutShell"');
  });
});
