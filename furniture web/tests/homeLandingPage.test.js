import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("home landing page", () => {
  it("uses buyer-facing responsive images instead of extraction placeholders", () => {
    const homePage = readSource("../src/pages/HomePage.vue");
    const styles = readSource("../src/styles.css");

    expect(homePage).toContain("home-hero-picture");
    expect(homePage).toContain("home-entry-picture");
    expect(homePage).toContain("home-entry-copy");
    expect(homePage).toContain('homeModuleCopy(index, "title")');
    expect(homePage).toContain('homeModuleCopy(index, "eyebrow")');
    expect(homePage).toContain('homeModuleCopy(index, "cta")');
    expect(homePage).toContain("generatedHomeModuleAsset(index)");
    expect(homePage).not.toContain("const homeModuleTitles = [");
    expect(homePage).not.toContain("<div>\n        <h2>{{ homeModuleTitle(index) }}</h2>\n      </div>");
    expect(homePage).not.toContain("ImageSpecPlaceholder");
    expect(homePage).not.toContain("homeHeroAssets");
    expect(homePage).not.toContain("specHeight");
    expect(homePage).not.toContain("sourceLevel");
    expect(styles).toContain(".home-hero-picture");
    expect(styles).toContain(".home-entry-picture");
    expect(styles).toContain(".home-entry-copy");
    expect(styles).not.toContain(".home-entry > div:last-child");
  });
});
