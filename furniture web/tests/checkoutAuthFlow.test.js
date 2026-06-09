import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("checkout auth split flow", () => {
  it("routes cart checkout through checkout auth in App.vue", () => {
    const source = readSource("../src/App.vue");

    expect(source).toContain("getCheckoutEntryRoute");
    expect(source).toContain("startCheckout");
    expect(source).toContain("@checkout=\"startCheckout\"");
    expect(source).toContain("@continue-checkout");
  });

  it("intercepts same-origin links for SPA navigation", () => {
    const source = readSource("../src/App.vue");

    expect(source).toContain("const handleInternalLinkClick = (event) => {");
    expect(source).toContain('document.addEventListener("click", handleInternalLinkClick)');
    expect(source).toContain('document.removeEventListener("click", handleInternalLinkClick)');
    expect(source).toContain("routeAliases");
    expect(source).toContain('"account-orders": "/account/orders"');
    expect(source).toContain('url.pathname !== pageRoutes.missing');
  });

  it("derives checkout auth options from cart items", () => {
    const source = readSource("../src/pages/CheckoutAuthPage.vue");

    expect(source).toContain("getCheckoutAuthOptions");
    expect(source).toContain("defineProps");
    expect(source).toContain("disabled");
    expect(source).toContain("continue-checkout");
  });
});
