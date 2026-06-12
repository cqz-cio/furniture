import { describe, expect, it } from "vitest";
import { getCheckoutErrorKey } from "../src/services/checkoutErrors.js";

describe("checkout error messages", () => {
  it("maps inventory and stock failures to a buyer-facing stock message", () => {
    expect(getCheckoutErrorKey({ code: 1006003001 })).toBe("checkout.errors.stockUnavailable");
    expect(getCheckoutErrorKey({ message: "SKU inventory is not enough" })).toBe(
      "checkout.errors.stockUnavailable"
    );
  });

  it("maps address and delivery failures to an address action message", () => {
    expect(getCheckoutErrorKey({ msg: "Delivery address is outside service area" })).toBe(
      "checkout.errors.addressUnavailable"
    );
    expect(getCheckoutErrorKey({ message: "shipping method not supported" })).toBe(
      "checkout.errors.addressUnavailable"
    );
  });

  it("maps price and settlement changes to a review message", () => {
    expect(getCheckoutErrorKey({ msg: "Order price changed, please resettle" })).toBe(
      "checkout.errors.priceChanged"
    );
  });

  it("maps auth failures and falls back to the caller default", () => {
    expect(getCheckoutErrorKey({ code: 401 })).toBe("checkout.errors.sessionExpired");
    expect(getCheckoutErrorKey({ code: 500 }, "checkout.errors.orderUnavailable")).toBe(
      "checkout.errors.orderUnavailable"
    );
  });
});
