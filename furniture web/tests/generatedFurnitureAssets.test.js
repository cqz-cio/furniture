import { existsSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { demoProducts } from "../src/data/demoProducts.js";
import { generatedFurnitureAssets } from "../src/data/generatedFurnitureAssets.js";

const publicAssetExists = (assetPath) =>
  existsSync(new URL(`../public${assetPath}`, import.meta.url));

describe("generated furniture image assets", () => {
  it("maps homepage and sale placeholders to local optimized WebP assets", () => {
    expect(generatedFurnitureAssets.home.hero.desktop).toBe("/assets/generated-furniture/home-hero-desktop.webp");
    expect(generatedFurnitureAssets.home.hero.mobile).toBe("/assets/generated-furniture/home-hero-mobile.webp");
    expect(generatedFurnitureAssets.sale.hero.desktop).toBe("/assets/generated-furniture/sale-hero-desktop.webp");
    expect(generatedFurnitureAssets.sale.categories.Living.desktop).toBe(
      "/assets/generated-furniture/sale-category-living-desktop.webp",
    );
    expect(generatedFurnitureAssets.sale.categories["Bath Towels"].mobile).toBe(
      "/assets/generated-furniture/sale-category-bath-towels-mobile.webp",
    );
    expect(generatedFurnitureAssets.outdoor.hero.desktop).toBe(
      "/assets/generated-furniture/outdoor-landing-hero-desktop.webp",
    );
    expect(generatedFurnitureAssets.babyChild.collections.nursery).toBe(
      "/assets/generated-furniture/baby-child-nursery.webp",
    );
    expect(generatedFurnitureAssets.teen.collections.lounge).toBe(
      "/assets/generated-furniture/teen-lounge.webp",
    );

    const paths = [
      generatedFurnitureAssets.home.hero.desktop,
      generatedFurnitureAssets.home.modules["002"].desktop,
      generatedFurnitureAssets.sale.hero.desktop,
      generatedFurnitureAssets.sale.membership.mobile,
      generatedFurnitureAssets.sale.categories.Lighting.desktop,
      generatedFurnitureAssets.outdoor.hero.mobile,
      generatedFurnitureAssets.babyChild.hero.desktop,
      generatedFurnitureAssets.babyChild.collections.playroom,
      generatedFurnitureAssets.teen.hero.mobile,
      generatedFurnitureAssets.teen.collections.study,
    ];
    expect(paths.every(publicAssetExists)).toBe(true);
  });

  it("gives every demo product a lightweight cover and PDP gallery image", () => {
    expect(demoProducts).toHaveLength(5);
    expect(demoProducts.every((product) => product.cover.endsWith(".webp"))).toBe(true);
    expect(demoProducts.every((product) => product.gallery.length >= 1)).toBe(true);
    expect(demoProducts.map((product) => product.cover)).toEqual([
      "/assets/generated-furniture/product-sofa-cover.webp",
      "/assets/generated-furniture/product-bed-cover.webp",
      "/assets/generated-furniture/product-table-cover.webp",
      "/assets/generated-furniture/product-chair-cover.webp",
      "/assets/generated-furniture/product-pendant-cover.webp",
    ]);
    expect(demoProducts.flatMap((product) => [product.cover, ...product.gallery]).every(publicAssetExists)).toBe(true);
  });
});
