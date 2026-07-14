import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("baby and teen landing pages", () => {
  it("keeps Baby & Child buyer-facing instead of exposing extraction specs", () => {
    const page = readSource("../src/pages/BabyChildPage.vue");
    const styles = readSource("../src/styles.css");

    expect(page).toContain("child-landing-page");
    expect(page).toContain("child-landing-hero");
    expect(page).toContain("BrandEyebrow");
    expect(page).toContain(':suffix="t(\'babyChild.hero.eyebrow\')"');
    expect(page).not.toContain(">RH Baby &amp; Child<");
    expect(page).toContain("heroImage.mobile");
    expect(page).toContain("generatedFurnitureAssets.babyChild.hero");
    expect(page).toContain("generatedFurnitureAssets.babyChild.collections.nursery");
    expect(page).toContain("generatedFurnitureAssets.babyChild.collections.playroom");
    expect(page).toContain("landing-feature-grid");
    expect(page).toContain("generatedFurnitureAssets");
    expect(page).not.toContain("generatedFurnitureAssets.products.bed.cover");
    expect(page).not.toContain("generatedFurnitureAssets.products.chair.cover");
    expect(page).not.toContain("ImageSpecPlaceholder");
    expect(page).not.toContain("babyChildPageSpecs");
    expect(page).not.toMatch(/JSON|viewport|documentHeight|sourceLevel|图片 \/ 视频投放区域抽取/);
    expect(styles).toContain(".child-landing-page");
    expect(styles).toContain(".child-landing-hero");
  });

  it("routes Baby & Child navigation items to local placeholder product pages", () => {
    const app = readSource("../src/App.vue");
    const categoryPage = readSource("../src/pages/BabyChildCategoryPage.vue");
    const styles = readSource("../src/styles.css");

    expect(app).toContain('const BabyChildCategoryPage = defineAsyncComponent(() => import("./pages/BabyChildCategoryPage.vue"));');
    expect(app).toContain('"baby-child-furniture": "/baby-child/furniture"');
    expect(app).toContain('"baby-child-bedding": "/baby-child/bedding"');
    expect(app).toContain('"baby-child-nursery": "/baby-child/nursery"');
    expect(app).toContain('"baby-child-registry": "/baby-child/registry"');
    expect(app).toContain('if (currentPage.value.startsWith("baby-child-")) return BabyChildCategoryPage;');
    expect(app).toContain(':page-key="currentPage"');
    expect(categoryPage).toContain("const categoryPages = {");
    expect(categoryPage).toContain('"baby-child-furniture"');
    expect(categoryPage).toContain('"baby-child-registry"');
    expect(categoryPage).toContain("baby-category-placeholder");
    expect(categoryPage).toContain("BrandEyebrow");
    expect(categoryPage).toContain(':suffix="t(\'babyChild.hero.eyebrow\')"');
    expect(categoryPage).not.toContain(">RH Baby &amp; Child<");
    expect(categoryPage).toContain('t("babyChild.category.placeholderDescription")');
    expect(styles).toContain(".baby-category-placeholder");
  });

  it("keeps Teen buyer-facing instead of exposing extraction specs", () => {
    const page = readSource("../src/pages/TeenPage.vue");
    const styles = readSource("../src/styles.css");

    expect(page).toContain("teen-landing-page");
    expect(page).toContain("teen-landing-hero");
    expect(page).toContain("BrandEyebrow");
    expect(page).toContain(':suffix="t(\'teen.hero.eyebrow\')"');
    expect(page).not.toContain(">RH Teen<");
    expect(page).toContain("heroImage.mobile");
    expect(page).toContain("generatedFurnitureAssets.teen.hero");
    expect(page).toContain("generatedFurnitureAssets.teen.collections.bedroom");
    expect(page).toContain("generatedFurnitureAssets.teen.collections.lounge");
    expect(page).toContain("landing-feature-grid");
    expect(page).toContain("generatedFurnitureAssets");
    expect(page).not.toContain("generatedFurnitureAssets.products.sofa.gallery");
    expect(page).not.toContain("generatedFurnitureAssets.products.table.gallery");
    expect(page).not.toContain("ImageSpecPlaceholder");
    expect(page).not.toContain("teenPageSpecs");
    expect(page).not.toMatch(/JSON|viewport|documentHeight|sourceLevel|图片 \/ 视频投放区域抽取/);
    expect(styles).toContain(".teen-landing-page");
    expect(styles).toContain(".teen-landing-hero");
  });
});
