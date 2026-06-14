import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("outdoor landing page", () => {
  it("renders a buyer-facing outdoor category page instead of extraction specs", () => {
    const page = readSource("../src/pages/OutdoorPage.vue");
    const styles = readSource("../src/styles.css");

    expect(page).toContain("outdoor-landing-page");
    expect(page).toContain("outdoor-landing-hero");
    expect(page).toContain("heroImage.mobile");
    expect(page).toContain("generatedFurnitureAssets.outdoor.hero");
    expect(page).toContain("generatedFurnitureAssets.sale.categories.Outdoor.desktop");
    expect(page).not.toContain('generatedFurnitureAssets.home.modules["004"].desktop');
    expect(page).not.toContain("generatedFurnitureAssets.products.pendant.cover");
    expect(page).toContain("landing-feature-grid");
    expect(page).toContain("generatedFurnitureAssets");
    expect(page).not.toContain("SpecGroup");
    expect(page).not.toContain("outdoorCollectionSpecs");
    expect(page).not.toMatch(/JSON|viewport|documentHeight|sourceLevel|图片 \/ 视频投放区域抽取|图片区域|投放区域|抽取/);
    expect(styles).toContain(".outdoor-landing-page");
    expect(styles).toContain(".outdoor-landing-hero");
  });
});
