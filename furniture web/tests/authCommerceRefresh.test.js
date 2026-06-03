import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("auth commerce refresh wiring", () => {
  it("wires RhHeader auth-change into App commerce refresh state", () => {
    const source = readSource("../src/App.vue");

    expect(source).toContain("const authVersion = ref(0)");
    expect(source).toContain("const handleAuthChange = async");
    expect(source).toContain("authVersion.value += 1");
    expect(source).toContain("@auth-change=\"handleAuthChange\"");
    expect(source).toContain(":auth-version=\"authVersion\"");
  });

  it("replaces remote cart with local cart when remote auth loading fails", () => {
    const source = readSource("../src/App.vue");

    expect(source).toContain("cartItems.value = readLocalCart()");
    expect(source).toContain("cartMode.value = \"local\"");
  });

  it("reloads and clears checkout data when authVersion changes", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain("authVersion");
    expect(source).toContain("watch(() => props.authVersion, loadCheckoutData)");
    expect(source).toContain("addresses.value = []");
    expect(source).toContain("settlement.value = null");
    expect(source).not.toContain("error.value = err.message");
  });

  it("reloads and clears orders when authVersion changes", () => {
    const source = readSource("../src/pages/OrdersPage.vue");

    expect(source).toContain("authVersion");
    expect(source).toContain("watch(() => props.authVersion, loadOrders)");
    expect(source).toContain("orders.value = []");
    expect(source).toContain("detail.value = null");
    expect(source).not.toContain("error.value = err.message");
  });
});
