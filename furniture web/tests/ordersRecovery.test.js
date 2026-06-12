import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("orders page recovery actions", () => {
  it("offers recovery actions for token, service, and empty states", () => {
    const source = readSource("../src/pages/OrdersPage.vue");

    expect(source).toContain("membershipRoutes.checkoutAuth");
    expect(source).toContain('error.value = t("orders.error")');
    expect(source).toContain('t("orders.actions.connectAccount")');
    expect(source).toContain('t("orders.actions.retry")');
    expect(source).toContain('t("orders.actions.shop")');
    expect(source).toContain('@click="loadOrders"');
    expect(source).not.toContain("Order service is unavailable. Please try again later.");
  });

  it("styles order recovery actions", () => {
    const source = readSource("../src/styles.css");

    expect(source).toContain(".orders-recovery-actions");
    expect(source).toContain(".orders-recovery-action");
  });
});
