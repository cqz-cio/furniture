import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8").replace(/\r\n/g, "\n");

describe("cart control polish", () => {
  it("uses rounded risk-aware controls for cart item actions", () => {
    const drawer = readSource("../src/components/CartDrawer.vue");
    const styles = readSource("../src/styles.css");

    expect(drawer).toContain("cart-risk-action cart-risk-action-danger");
    expect(drawer).toContain("cart-risk-action cart-risk-action-neutral");
    expect(drawer).toContain("cart-quantity-stepper");
    expect(drawer).toContain("aria-label=\"Decrease quantity\"");
    expect(drawer).toContain("aria-label=\"Increase quantity\"");
    expect(drawer).toContain("adjustQuantity(item, -1)");
    expect(drawer).toContain("adjustQuantity(item, 1)");

    expect(styles).toContain(".cart-risk-action-danger:hover");
    expect(styles).toContain("background: #b42318;");
    expect(styles).toContain(".cart-risk-action-warning:hover");
    expect(styles).toContain("background: #b7791f;");
    expect(styles).toContain("border-radius: 999px;");
    expect(styles).toContain(".cart-quantity-stepper:focus-within");
  });
});
