import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8").replace(/\r\n/g, "\n");

describe("product wishlist actions", () => {
  it("wires product list cards to shared wishlist state and save events", () => {
    const source = readSource("../src/pages/SofasPlpPage.vue");

    expect(source).toContain('const emit = defineEmits(["add-to-cart", "add-to-wishlist"])');
    expect(source).toContain("loadWishlistIdentityState");
    expect(source).toContain("wishlistIdentityStatusKey");
    expect(source).toContain("isWishlistItemSaved(product, wishlistIdentityKeys.value)");
    expect(source).toContain('emit("add-to-wishlist", product)');
    expect(source).toContain('t(isProductSaved(product) ? "wishlist.saved" : "wishlist.save")');
  });

  it("wires product detail to shared wishlist state and save events", () => {
    const source = readSource("../src/pages/SofaPdpPage.vue");

    expect(source).toContain('const emit = defineEmits(["add-to-cart", "add-to-wishlist"])');
    expect(source).toContain("loadWishlistIdentityState");
    expect(source).toContain("wishlistStatusMessage");
    expect(source).toContain("isCurrentProductSaved");
    expect(source).toContain('emit("add-to-wishlist", product.value)');
    expect(source).toContain('t(isCurrentProductSaved ? "wishlist.saved" : "wishlist.save")');
  });

  it("routes page wishlist events through the app-level backend/local handler", () => {
    const source = readSource("../src/App.vue");

    expect(source).toContain('@add-to-wishlist="addToWishlist"');
    expect(source).toContain("authVersion.value += 1");
  });

  it("adds adaptive save labels for supported wishlist locales", () => {
    const source = readSource("../src/i18n.js");

    expect(source).toContain('save: "Save to wishlist"');
    expect(source).toContain('saved: "Saved"');
    expect(source).toContain('save: "保存到心愿单"');
    expect(source).toContain('save: "Enregistrer"');
  });

  it("styles wishlist actions as secondary controls beside primary purchase buttons", () => {
    const styles = readSource("../src/styles.css");

    expect(styles).toContain(".product-card-wishlist");
    expect(styles).toContain(".product-wishlist-button");
    expect(styles).toContain('grid-template-columns: 96px minmax(0, 1fr) minmax(0, 1fr)');
  });
});
