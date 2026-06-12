import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("baby and teen landing pages", () => {
  it("keeps Baby & Child buyer-facing instead of exposing extraction specs", () => {
    const page = readSource("../src/pages/BabyChildPage.vue");
    const styles = readSource("../src/styles.css");

    expect(page).toContain("child-landing-page");
    expect(page).toContain("child-landing-hero");
    expect(page).toContain("landing-feature-grid");
    expect(page).toContain("generatedFurnitureAssets");
    expect(page).not.toContain("ImageSpecPlaceholder");
    expect(page).not.toContain("babyChildPageSpecs");
    expect(page).not.toMatch(/JSON|viewport|documentHeight|sourceLevel|图片 \/ 视频投放区域抽取/);
    expect(styles).toContain(".child-landing-page");
    expect(styles).toContain(".child-landing-hero");
  });

  it("keeps Teen buyer-facing instead of exposing extraction specs", () => {
    const page = readSource("../src/pages/TeenPage.vue");
    const styles = readSource("../src/styles.css");

    expect(page).toContain("teen-landing-page");
    expect(page).toContain("teen-landing-hero");
    expect(page).toContain("landing-feature-grid");
    expect(page).toContain("generatedFurnitureAssets");
    expect(page).not.toContain("ImageSpecPlaceholder");
    expect(page).not.toContain("teenPageSpecs");
    expect(page).not.toMatch(/JSON|viewport|documentHeight|sourceLevel|图片 \/ 视频投放区域抽取/);
    expect(styles).toContain(".teen-landing-page");
    expect(styles).toContain(".teen-landing-hero");
  });
});
