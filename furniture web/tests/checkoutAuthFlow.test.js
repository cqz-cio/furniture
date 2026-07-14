import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("checkout auth split flow", () => {
  it("routes cart checkout through the checkout entry helper in App.vue", () => {
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
    expect(source).toContain('"/orders": "account-orders"');
    expect(source).toContain('url.pathname !== pageRoutes.missing');
  });

  it("does not drop order query params when an alias route already maps to the active page", () => {
    const source = readSource("../src/App.vue");

    expect(source).toContain("watch(currentPage, (page) =>");
    expect(source).toContain("if (pageFromPath(window.location.pathname) === page) return;");
    expect(source).toContain("window.location.search");
    expect(source).toContain("window.location.hash");
  });

  it("derives checkout auth options from cart items", () => {
    const source = readSource("../src/pages/CheckoutAuthPage.vue");

    expect(source).toContain("getCheckoutAuthOptions");
    expect(source).toContain("defineProps");
    expect(source).toContain("disabled");
    expect(source).toContain("continue-checkout");
  });
});
