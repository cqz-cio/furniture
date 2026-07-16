import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("shared Oakved catalog page", () => {
  it("registers one shared catalog route and component", () => {
    const app = readSource("../src/App.vue");

    expect(app).toContain('const CatalogPage = defineAsyncComponent(() => import("./pages/CatalogPage.vue"))');
    expect(app).toContain('catalog: "/catalog"');
    expect(app).toContain('if (currentPage.value === "catalog") return CatalogPage');
    expect(app).toContain('title: "Oakved Catalog | Oakved"');
  });

  it("uses the existing sourcebook artwork and translated English copy", () => {
    const page = readSource("../src/pages/CatalogPage.vue");

    expect(page).toContain('generatedFurnitureAssets.home.modules["005"]');
    expect(page).toContain('t("catalogPage.eyebrow")');
    expect(page).toContain('t("catalogPage.title")');
    expect(page).toContain('t("catalogPage.introduction")');
    expect(page).toContain('<picture class="catalog-hero-picture">');
  });
});
