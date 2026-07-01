import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8").replace(/\r\n/g, "\n");

describe("account wishlist page", () => {
  it("registers a real account wishlist route and page component", () => {
    const source = readSource("../src/App.vue");

    expect(source).toContain('defineAsyncComponent');
    expect(source).toContain('const AccountWishlistPage = defineAsyncComponent(() => import("./pages/AccountWishlistPage.vue"));');
    expect(source).toContain('"account-wishlist": "/account/wishlist"');
    expect(source).not.toContain('"/account/wishlist": "account"');
    expect(source).toContain('if (currentPage.value === "account-wishlist") return AccountWishlistPage;');
    expect(source).toContain("@add-to-cart=\"addToCart\"");
  });

  it("builds the wishlist page from local wishlist data with RH-inspired item controls", () => {
    const source = readSource("../src/pages/AccountWishlistPage.vue");

    expect(source).toContain("getRemoteWishlistItems");
    expect(source).toContain("deleteFavorite");
    expect(source).toContain("deleteFavorite(item)");
    expect(source).toContain("updateFavoriteCount");
    expect(source).toContain("readLocalWishlist");
    expect(source).toContain("removeLocalWishlistItem");
    expect(source).toContain("updateLocalWishlistItemQuantity");
    expect(source).toContain("readYudaoToken");
    expect(source).toContain("ProductImage");
    expect(source).toContain('defineEmits(["add-to-cart"])');
    expect(source).toContain('t("wishlist.title")');
    expect(source).toContain('t("wishlist.addToCart")');
    expect(source).toContain('t("wishlist.remove")');
    expect(source).toContain('t("wishlist.emptyTitle")');
    expect(source).toContain("wishlist-line");
    expect(source).toContain("wishlist-spec-list");
    expect(source).toContain("wishlist-price-panel");
  });

  it("surfaces remote wishlist loading, retry, and auth states", () => {
    const pageSource = readSource("../src/pages/AccountWishlistPage.vue");
    const i18nSource = readSource("../src/i18n.js");

    expect(pageSource).toContain("wishlist.loading");
    expect(pageSource).toContain("wishlist.remoteUnavailable");
    expect(pageSource).toContain("wishlist.signInRequired");
    expect(pageSource).toContain("loadWishlist");
    expect(pageSource).toContain("@click=\"loadWishlist\"");
    expect(i18nSource).toContain("remoteUnavailable:");
    expect(i18nSource).toContain("signInRequired:");
    expect(i18nSource).toContain("retrySync:");
  });

  it("marks wishlist-specific layout hooks for Product Design polish", () => {
    const pageSource = readSource("../src/pages/AccountWishlistPage.vue");
    const styles = readSource("../src/styles.css");

    expect(pageSource).toContain("account-wishlist-sidebar");
    expect(pageSource).toContain("wishlist-line-commerce");
    expect(styles).toContain(".account-wishlist-page .account-wishlist-sidebar");
    expect(styles).toContain("overflow-x: auto");
    expect(styles).toContain(".wishlist-line-commerce");
    expect(styles).toContain("grid-template-columns: minmax(0, 1fr) minmax(118px, 0.42fr) minmax(132px, 0.42fr)");
    expect(styles).toContain("max-width: 212px");
  });

  it("defines adaptive wishlist copy for every supported locale", () => {
    const source = readSource("../src/i18n.js");

    expect(source.match(/wishlist:\s*{/g)).toHaveLength(2);
    expect(source).toContain('messages["zh-CN"].wishlist = {');
    for (const key of [
      "title",
      "itemCount",
      "addToCart",
      "remove",
      "emptyTitle",
      "emptyHelp",
      "continueShopping",
      "addedToCart",
      "removed",
      "itemNumber",
      "color",
      "fabric",
      "width",
      "member",
      "regular",
      "availability",
    ]) {
      expect(source).toContain(`${key}:`);
    }
  });
});
