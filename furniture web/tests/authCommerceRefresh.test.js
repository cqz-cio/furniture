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
    expect(source).toContain("let remoteCartRequestId = 0");
    expect(source).toContain("if (requestId !== remoteCartRequestId) return");
  });

  it("replaces remote cart with local cart when remote auth loading fails", () => {
    const source = readSource("../src/App.vue");

    expect(source).toContain("cartItems.value = readLocalCart()");
    expect(source).toContain("cartMode.value = \"local\"");
    expect(source).toContain("if (cartMode.value !== \"yudao\") writeLocalCart(items)");
    expect(source).toContain("const switchToLocalCart = () =>");
    expect(source).toContain("switchToLocalCart()");
  });

  it("reloads and clears checkout data when authVersion changes", () => {
    const source = readSource("../src/pages/CheckoutPage.vue");

    expect(source).toContain("authVersion");
    expect(source).toContain("preserveAddressSelection");
    expect(source).toContain("watch(() => props.authVersion");
    expect(source).toContain("watch(() => props.items");
    expect(source).toContain("let checkoutRequestId = 0");
    expect(source).toContain("if (requestId !== checkoutRequestId) return");
    expect(source).toContain("addresses.value = []");
    expect(source).toContain("settlement.value = null");
    expect(source).not.toContain("error.value = err.message");
  });

  it("reloads and clears orders when authVersion changes", () => {
    const source = readSource("../src/pages/OrdersPage.vue");

    expect(source).toContain("authVersion");
    expect(source).toContain("watch(() => props.authVersion, loadOrders)");
    expect(source).toContain("let ordersRequestId = 0");
    expect(source).toContain("if (requestId !== ordersRequestId) return");
    expect(source).toContain("orders.value = []");
    expect(source).toContain("detail.value = null");
    expect(source).not.toContain("error.value = err.message");
  });

  it("renders account modal as a viewport-level overlay", () => {
    const modalSource = readSource("../src/components/AuthModal.vue");
    const stylesSource = readSource("../src/styles.css");
    const layerStyles = stylesSource.match(/\.account-modal-layer \{[\s\S]*?\n\}/)?.[0] || "";

    expect(modalSource).toContain("<Teleport to=\"body\">");
    expect(modalSource).toContain("document.body.classList.toggle(\"auth-modal-open\", isOpen)");
    expect(stylesSource).toContain("body.auth-modal-open");
    expect(layerStyles).toContain("position: fixed;");
    expect(layerStyles).toContain("inset: 0;");
    expect(layerStyles).toContain("z-index: 200;");
    expect(layerStyles).not.toContain("top: 136px");
  });
});
