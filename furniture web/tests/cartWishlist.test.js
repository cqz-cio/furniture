import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8").replace(/\r\n/g, "\n");

describe("cart wishlist action", () => {
  it("handles the cart drawer wishlist event in App.vue", () => {
    const source = readSource("../src/App.vue");

    expect(source).toContain('import { addLocalWishlistItem } from "./services/localWishlist.js";');
    expect(source).toContain('import { createFavorite } from "./services/yudaoFavoriteApi.js";');
    expect(source).toContain("const addToWishlist = async (item) => {");
    expect(source).toContain("readYudaoToken()");
    expect(source).toContain("createFavorite");
    expect(source).toContain("addLocalWishlistItem(item)");
    expect(source).toContain('@wishlist="addToWishlist"');
  });

  it("shows feedback after adding an item to the wishlist", () => {
    const appSource = readSource("../src/App.vue");
    const drawerSource = readSource("../src/components/CartDrawer.vue");
    const styles = readSource("../src/styles.css");

    expect(appSource).toContain("cartWishlistNoticeKey = ref(\"\")");
    expect(appSource).toContain(':wishlist-notice-key="cartWishlistNoticeKey"');
    expect(drawerSource).toContain("wishlistNoticeKey");
    expect(drawerSource).toContain('t(wishlistNoticeKey)');
    expect(drawerSource).toContain("cart-wishlist-notice");
    expect(styles).toContain(".cart-wishlist-notice");
  });
});
