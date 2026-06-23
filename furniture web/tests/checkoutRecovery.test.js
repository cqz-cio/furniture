import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

import { getCheckoutRecoveryAction } from "../src/services/checkoutRecovery.js";

describe("checkout recovery actions", () => {
  const routes = {
    addressBook: "/account/address-book",
    checkoutAuth: "/checkout/auth",
  };

  it("routes missing or unavailable addresses to the address book", () => {
    expect(getCheckoutRecoveryAction("checkout.errors.noAddress", routes)).toEqual({
      type: "link",
      labelKey: "checkout.actions.manageAddresses",
      href: "/account/address-book",
    });
    expect(getCheckoutRecoveryAction("checkout.errors.addressUnavailable", routes)).toEqual({
      type: "link",
      labelKey: "checkout.actions.manageAddresses",
      href: "/account/address-book",
    });
  });

  it("opens the bag for stock recovery", () => {
    expect(getCheckoutRecoveryAction("checkout.errors.stockUnavailable", routes)).toEqual({
      type: "emit",
      labelKey: "checkout.actions.reviewBag",
      event: "open-cart",
    });
  });

  it("routes expired sessions back through checkout auth", () => {
    expect(getCheckoutRecoveryAction("checkout.errors.sessionExpired", routes)).toEqual({
      type: "link",
      labelKey: "checkout.actions.signIn",
      href: "/checkout/auth",
    });
  });

  it("refreshes settlement when prices change", () => {
    expect(getCheckoutRecoveryAction("checkout.errors.priceChanged", routes)).toEqual({
      type: "retry",
      labelKey: "checkout.actions.refreshSettlement",
    });
  });

  it("keeps address confirmation failures on checkout for a fresh address review", () => {
    expect(getCheckoutRecoveryAction("checkout.errors.addressConfirmationRequired", routes)).toEqual({
      type: "address-review",
      labelKey: "checkout.actions.reviewAddress",
    });
  });

  it("links to the created order when payment is unavailable after order creation", () => {
    expect(
      getCheckoutRecoveryAction("checkout.errors.paymentUnavailable", {
        ...routes,
        orderDetail: "/orders?id=902",
      }),
    ).toEqual({
      type: "link",
      labelKey: "checkout.actions.viewOrder",
      href: "/orders?id=902",
    });
  });

  it("does not show recovery actions for generic errors", () => {
    expect(getCheckoutRecoveryAction("checkout.errors.orderUnavailable", routes)).toBeNull();
  });

  it("refreshes the cart before opening the created order", () => {
    const source = readFileSync(new URL("../src/App.vue", import.meta.url), "utf8");

    expect(source).toContain("const handleOrderCreated = async (orderId)");
    expect(source).toContain("await loadRemoteCart()");
    expect(source).toContain("openOrderDetail(orderId)");
    expect(source).toContain('@order-created="handleOrderCreated"');
  });
});
