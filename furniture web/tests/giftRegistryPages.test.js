import { existsSync, readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const pagePath = (fileName) => new URL(`../src/pages/${fileName}`, import.meta.url);

describe("gift registry pages and routes", () => {
  it("adds create, find and manage registry pages", () => {
    ["GiftRegistryCreatePage.vue", "GiftRegistryFindPage.vue", "GiftRegistryManagePage.vue"].forEach((fileName) => {
      expect(existsSync(pagePath(fileName)), `${fileName} should exist`).toBe(true);
    });
  });

  it("registers gift registry routes in App.vue", () => {
    const app = readFileSync(new URL("../src/App.vue", import.meta.url), "utf8");

    expect(app).toContain('"gift-registry-create": "/gift-registry/create"');
    expect(app).toContain('"gift-registry-find": "/gift-registry/find"');
    expect(app).toContain('"gift-registry-manage": "/gift-registry/manage"');
  });

  it("uses the gift registry model from the create page", () => {
    const source = readFileSync(pagePath("GiftRegistryCreatePage.vue"), "utf8");

    expect(source).toContain("createGiftRegistryDraft");
    expect(source).toContain("getGiftRegistrySteps");
    expect(source).toContain("getRegistryShareState");
    expect(source).toContain("createYudaoGiftRegistry");
    expect(source).toContain("readYudaoToken");
  });

  it("loads registry pages from persistent Yudao APIs instead of fixed demo data", () => {
    const manage = readFileSync(pagePath("GiftRegistryManagePage.vue"), "utf8");
    const find = readFileSync(pagePath("GiftRegistryFindPage.vue"), "utf8");
    const publicPage = readFileSync(pagePath("GiftRegistryPage.vue"), "utf8");

    expect(manage).toContain("getMyYudaoGiftRegistry");
    expect(manage).toContain("addYudaoGiftRegistryItem");
    expect(manage).toContain("registryLoadState");
    expect(manage).not.toContain('id: "registry-stone-2026"');
    expect(find).toContain("searchPublicYudaoGiftRegistries");
    expect(find).not.toContain("sampleRegistries");
    expect(publicPage).toContain("getPublicYudaoGiftRegistry");
    expect(publicPage).toContain("publicCode");
  });

  it("lets PDP add the current real product to the signed-in user's gift registry", () => {
    const source = readFileSync(pagePath("SofaPdpPage.vue"), "utf8");

    expect(source).toContain("getMyYudaoGiftRegistry");
    expect(source).toContain("addYudaoGiftRegistryItem");
    expect(source).toContain("registryProductToItemPayload");
    expect(source).toContain("handleAddToRegistry");
    expect(source).toContain('source.value !== "yudao"');
    expect(source).toContain("readYudaoToken()");
    expect(source).toContain('class="product-registry-button"');
  });

  it("lets public registry gift items enter the cart with registry context", () => {
    const source = readFileSync(pagePath("GiftRegistryPage.vue"), "utf8");

    expect(source).toContain('defineEmits(["add-to-cart"])');
    expect(source).toContain("registryItemToCartProduct");
    expect(source).toContain("handleAddRegistryGiftToCart");
    expect(source).toContain("registryContext");
    expect(source).toContain('class="registry-cart-button"');
    expect(source).toContain(":href=\"`/product?id=${item.spuId}&registryItemId=${item.id}`\"");
  });

  it("keeps production registry failures explicit instead of silently using demo data", () => {
    const service = readFileSync(new URL("../src/services/giftRegistry.js", import.meta.url), "utf8");
    const manage = readFileSync(pagePath("GiftRegistryManagePage.vue"), "utf8");
    const find = readFileSync(pagePath("GiftRegistryFindPage.vue"), "utf8");

    expect(service).toContain("canUseGiftRegistryDemoFallback");
    expect(manage).toContain("import.meta.env.PROD");
    expect(find).toContain("import.meta.env.PROD");
  });
});
