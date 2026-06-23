import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("sale landing page", () => {
  it("uses buyer-facing image markup instead of extraction placeholders", () => {
    const salePage = readSource("../src/pages/SalePage.vue");
    const saleTile = readSource("../src/components/SaleCategoryTile.vue");
    const styles = readSource("../src/styles.css");

    expect(salePage).toContain("sale-hero-picture");
    expect(salePage).toContain("sale-membership-picture");
    expect(salePage).toContain("BrandEyebrow");
    expect(salePage).toContain('suffix="Sale"');
    expect(salePage).not.toContain(">RH Sale<");
    expect(saleTile).toContain("sale-tile-picture");
    expect(salePage).not.toContain("ImageSpecPlaceholder");
    expect(salePage).not.toContain("saleHeroSpecs");
    expect(salePage).not.toContain("saleMembershipSpec");
    expect(saleTile).not.toContain("ImageSpecPlaceholder");
    expect(saleTile).not.toContain("categoryImageSpec");
    expect(`${salePage}\n${saleTile}`).not.toMatch(/JSON|viewport|documentHeight|sourceLevel|图片区域|投放区域|抽取/);
    expect(styles).toContain(".sale-hero-picture");
    expect(styles).toContain(".sale-membership-picture");
    expect(styles).toContain(".sale-tile-picture");
  });

  it("keeps desktop sale quick links easy to click", () => {
    const styles = readSource("../src/styles.css").replace(/\r\n/g, "\n");

    expect(styles).toContain(".sale-links a {\n  min-height: 44px;");
  });
});
