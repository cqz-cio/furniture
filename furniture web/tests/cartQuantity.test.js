import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

import { normalizeCartQuantity } from "../src/services/localCart.js";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("cart quantity normalization", () => {
  it("normalizes invalid cart quantities to one", () => {
    expect(normalizeCartQuantity(0)).toBe(1);
    expect(normalizeCartQuantity(-2)).toBe(1);
    expect(normalizeCartQuantity("")).toBe(1);
    expect(normalizeCartQuantity("abc")).toBe(1);
  });

  it("uses whole positive quantities", () => {
    expect(normalizeCartQuantity("3")).toBe(3);
    expect(normalizeCartQuantity(2.8)).toBe(2);
  });

  it("normalizes quantity updates before emitting or calling remote cart APIs", () => {
    const drawerSource = readSource("../src/components/CartDrawer.vue");
    const appSource = readSource("../src/App.vue");

    expect(drawerSource).toContain("normalizeCartQuantity");
    expect(drawerSource).toContain("handleQuantityChange");
    expect(appSource).toContain("normalizeCartQuantity(quantity)");
    expect(appSource).toContain("updateCartItemCount(item.cartId, nextQuantity)");
  });
});
