import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("ERP-aligned storefront catalog", () => {
  it("never imports or falls back to fixed demo products", () => {
    const listPage = read("../src/pages/SofasPlpPage.vue");
    const detailPage = read("../src/pages/SofaPdpPage.vue");
    const assistant = read("../src/services/furnitureAssistant.js");

    expect(listPage).not.toContain("demoProducts");
    expect(detailPage).not.toContain("demoProducts");
    expect(assistant).not.toContain("demoProducts");
  });

  it("uses empty and unavailable states instead of local product cards", () => {
    const listPage = read("../src/pages/SofasPlpPage.vue");
    const detailPage = read("../src/pages/SofaPdpPage.vue");

    expect(listPage).toContain("const products = ref([])");
    expect(listPage).toContain('t("catalogEmpty")');
    expect(listPage).toContain('t("catalogUnavailable")');
    expect(detailPage).toContain("const product = ref(null)");
    expect(detailPage).toContain('t("productUnavailable")');
  });
});
