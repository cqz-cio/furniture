import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8").replace(/\r\n/g, "\n");

describe("home landing page", () => {
  it("uses buyer-facing responsive images instead of extraction placeholders", () => {
    const homePage = readSource("../src/pages/HomePage.vue");
    const styles = readSource("../src/styles.css");

    expect(homePage).toContain("home-hero-picture");
    expect(homePage).toContain("home-entry-picture");
    expect(homePage).toContain("home-entry-copy");
    expect(homePage).toContain('homeModuleCopy(index, "title")');
    expect(homePage).toContain("homeModuleEyebrowSuffix(index)");
    expect(homePage).toContain("BrandEyebrow");
    expect(homePage).toContain('homeModuleCopy(index, "cta")');
    expect(homePage).toContain("generatedHomeModuleAsset(index)");
    expect(homePage).not.toContain("const homeModuleTitles = [");
    expect(homePage).not.toContain("<div>\n        <h2>{{ homeModuleTitle(index) }}</h2>\n      </div>");
    expect(homePage).not.toContain("ImageSpecPlaceholder");
    expect(homePage).not.toContain("homeHeroAssets");
    expect(homePage).not.toContain("specHeight");
    expect(homePage).not.toContain("sourceLevel");
    expect(styles).toContain(".home-hero-picture");
    expect(styles).toContain("homeHeroCrossfade");
    expect(styles).toContain("homeSlowZoom");
    expect(styles).toContain(".home-hero-slide-2");
    expect(styles).toContain(".home-entry-picture");
    expect(styles).toContain(".home-entry-copy");
    expect(styles).toContain(".home-grid {\n  width: 100%;\n  margin: 0;\n  display: grid;\n  grid-template-columns: 1fr;\n  gap: 0;");
    expect(styles).toContain("height: clamp(907.88px, 62vw, 1320px);");
    expect(styles).toContain(".home-entry-copy {\n  position: absolute;\n  left: 50%;\n  top: 58%;");
    expect(styles).toContain("bottom: auto;\n  z-index: 2;");
    expect(styles).toContain("transform: translate(-50%, -50%);");
    expect(styles).not.toContain(".home-entry > div:last-child");
  });

  it("uses the Oakved logo artwork in the hero instead of RH text", () => {
    const homePage = readSource("../src/pages/HomePage.vue");
    const styles = readSource("../src/styles.css");

    expect(homePage).toContain('class="home-hero-logo"');
    expect(homePage).toContain('src="/assets/brand/oakved-logo-white.png"');
    expect(homePage).toContain('alt="Oakved"');
    expect(homePage).not.toContain("<h1>RH</h1>");
    expect(styles).toContain(".home-hero-logo");
  });

  it("uses adaptive Oakved badges for RH-style module eyebrows", () => {
    const homePage = readSource("../src/pages/HomePage.vue");
    const styles = readSource("../src/styles.css");

    expect(homePage).toContain(':suffix="homeModuleEyebrowSuffix(index)"');
    expect(homePage).not.toContain('<p class="eyebrow">{{ homeModuleCopy(index, "eyebrow") }}</p>');
    expect(styles).toContain(".brand-eyebrow-logo");
  });
});
