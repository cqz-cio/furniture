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

  it("optimistically updates remote cart quantities before waiting for the API", () => {
    const appSource = readSource("../src/App.vue");
    const updateBody = appSource.slice(appSource.indexOf("const updateCartQuantity = async"), appSource.indexOf("const removeFromCart"));
    const localUpdateIndex = updateBody.indexOf("cartItems.value = updateLocalCartItemQuantity(cartItems.value, item.skuId, nextQuantity)");
    const remoteUpdateIndex = updateBody.indexOf("await updateCartItemCount(item.cartId, nextQuantity)");

    expect(localUpdateIndex).toBeGreaterThan(-1);
    expect(remoteUpdateIndex).toBeGreaterThan(-1);
    expect(localUpdateIndex).toBeLessThan(remoteUpdateIndex);
  });

  it("does not remove local cart rows after a failed remote cart delete", () => {
    const appSource = readSource("../src/App.vue");
    const removeBody = appSource.slice(appSource.indexOf("const removeFromCart = async"), appSource.indexOf("const addToWishlist"));
    const mutationNoticeIndex = removeBody.indexOf('noticeKey: "cart.remoteMutationUnavailable"');
    const localRemoveIndex = removeBody.indexOf("cartItems.value = removeLocalCartItem(cartItems.value, item.skuId)");
    const remoteFailureBranch = removeBody.slice(mutationNoticeIndex, localRemoveIndex);

    expect(mutationNoticeIndex).toBeGreaterThan(-1);
    expect(localRemoveIndex).toBeGreaterThan(-1);
    expect(remoteFailureBranch).toContain("return;");
  });
});
