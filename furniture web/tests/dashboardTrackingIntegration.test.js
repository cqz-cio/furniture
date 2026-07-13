import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const app = readFileSync(new URL("../src/App.vue", import.meta.url), "utf8");
const pdp = readFileSync(new URL("../src/pages/SofaPdpPage.vue", import.meta.url), "utf8");
const cart = readFileSync(new URL("../src/services/yudaoCartApi.js", import.meta.url), "utf8");
const analytics = readFileSync(new URL("../src/services/analytics.js", import.meta.url), "utf8");

describe("dashboard tracking integration", () => {
  it("tracks home, resolved product detail, and checkout start", () => {
    expect(app).toContain("trackHomeView");
    expect(app).toContain("trackCheckoutStart");
    expect(pdp).toContain("trackProductDetailView(product.value.id)");
    expect(pdp.indexOf("trackProductDetailView(product.value.id)")).toBeGreaterThan(pdp.indexOf('source.value = "yudao"'));
  });

  it("adds gated identity only to the trusted cart request", () => {
    expect(cart).toContain("analyticsIdentityHeaders()");
    expect(cart).toContain('headers: { ...analyticsIdentityHeaders()');
    expect(analytics).not.toContain("trackAddToCart");
    expect(analytics).not.toContain('ADD_TO_CART');
  });
});
