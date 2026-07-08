import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8").replace(/\r\n/g, "\n");

const visibleSourceFiles = [
  "../src/App.vue",
  "../src/components/CartDrawer.vue",
  "../src/components/RhHeader.vue",
  "../src/components/RhPromoBanner.vue",
  "../src/pages/AccountPage.vue",
  "../src/pages/BabyChildCategoryPage.vue",
  "../src/pages/BabyChildPage.vue",
  "../src/pages/CheckoutPage.vue",
  "../src/pages/GiftRegistryCreatePage.vue",
  "../src/pages/GiftRegistryFindPage.vue",
  "../src/pages/GiftRegistryManagePage.vue",
  "../src/pages/GiftRegistryPage.vue",
  "../src/pages/HomePage.vue",
  "../src/pages/MissingExtractionPage.vue",
  "../src/pages/OutdoorPage.vue",
  "../src/pages/SalePage.vue",
  "../src/pages/SofaPdpPage.vue",
  "../src/pages/SofasPlpPage.vue",
  "../src/pages/TeenPage.vue",
];

const forbiddenVisibleSnippets = [
  ">Account Dashboard<",
  ">Add Gift<",
  ">Add Gift To Bag<",
  "Add to Gift Registry",
  ">Billing address same as shipping<",
  ">Build a coordinated wood furniture setting<",
  "Build the room around bedside storage",
  ">Cart<",
  ">Check back after the owner adds items.<",
  ">Clear all<",
  ">Click, scroll or use arrow keys to switch views<",
  ">Collection<",
  ">Complete The Room<",
  ">Create Flow<",
  ">Create a Registry<",
  ">Design Services<",
  ">Featured collection<",
  ">Filter<",
  ">Find a Registry<",
  ">Gift Message <",
  ">Gift Registry<",
  ">Images, sizes and stock stay visible while browsing<",
  ">Join RH Members<",
  ">Manage Registry<",
  ">Manage Your Registry<",
  ">Material and finish guidance<",
  ">Member Savings<",
  ">No Gifts Yet<",
  ">Oakved Edit<",
  ">Open-air rooms, fully composed.<",
  ">Order Description <",
  ">Order Summary<",
  ">Outdoor furniture planning<",
  ">Payment<",
  ">Personal rooms, fully considered.<",
  ">Product page placeholder<",
  ">Registry Gifts<",
  ">Room Inspiration<",
  ">Rooms built around proportion, material and calm.<",
  ">Rooms for first chapters.<",
  ">Save this credit card to my account<",
  ">Search<",
  ">Ship to United States ",
  ">Shop Outdoor<",
  ">Shop Teen<",
  ">Shop the edit<",
  ">Sign In Required<",
  ">Start with RH Members<",
  ">Style the full Oakved room<",
  ">This registry does not have public gift items yet.<",
  ">View Cart ",
  ">View Product<",
  ">View Registry<",
  ">占位中，后续接入该分类真实商品、筛选和图片素材。<",
  ">这些页面先保留开发占位<",
  ">素材与页面方案待定<",
  ">鍗犱綅涓",
  ">杩欎簺椤甸潰鍏堜繚鐣欏紑鍙戝崰浣",
  ">绱犳潗涓庨〉闈㈡柟妗堝緟瀹",
];

const mojibakePattern = /[閿熼柍闁兼稉婵崶缁句絻鐦絔]/;

describe("visible copy localization coverage", () => {
  it("keeps targeted runtime source files free of known hard-coded visible copy", () => {
    for (const path of visibleSourceFiles) {
      const source = readSource(path);

      for (const snippet of forbiddenVisibleSnippets) {
        expect(source, `${path} still contains visible snippet: ${snippet}`).not.toContain(snippet);
      }
    }
  });

  it("keeps covered runtime localization text free of common mojibake markers", () => {
    const source = readSource("../src/i18n.js");

    expect(source).not.toMatch(mojibakePattern);
    expect(source).not.toContain("Fran鑾絘is");
    expect(source).not.toContain("娑擃厽鏋");
  });
});
