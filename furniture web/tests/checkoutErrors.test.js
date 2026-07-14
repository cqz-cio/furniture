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

  it("maps missing backend address confirmation audit failures to address review recovery", () => {
    expect(getCheckoutErrorKey({ code: 1011000040 })).toBe(
      "checkout.errors.addressConfirmationRequired"
    );
    expect(getCheckoutErrorKey({ msg: "快递订单必须包含用户确认后的地址核对记录" })).toBe(
      "checkout.errors.addressConfirmationRequired"
    );
    expect(getCheckoutErrorKey({ message: "Express orders require confirmed address verification audit" })).toBe(
      "checkout.errors.addressConfirmationRequired"
    );
  });

  it("maps price and settlement changes to a review message", () => {
    expect(getCheckoutErrorKey({ msg: "Order price changed, please resettle" })).toBe(
      "checkout.errors.priceChanged"
    );
  });

  it("maps payment channel failures to a payment unavailable message", () => {
    expect(getCheckoutErrorKey({ msg: "Pay order submit failed: payment channel disabled" })).toBe(
      "checkout.errors.paymentUnavailable"
    );
  });

  it("maps Yudao pay order state errors to payment recovery", () => {
    expect(getCheckoutErrorKey({ code: 1007002000, msg: "支付订单不存在" })).toBe(
      "checkout.errors.paymentUnavailable"
    );
    expect(getCheckoutErrorKey({ code: 1007002001, msg: "支付订单不处于待支付" })).toBe(
      "checkout.errors.paymentUnavailable"
    );
    expect(getCheckoutErrorKey({ code: 1007002002, msg: "订单已支付，请刷新页面" })).toBe(
      "checkout.errors.paymentUnavailable"
    );
    expect(getCheckoutErrorKey({ code: 1007002003, msg: "支付订单已经过期" })).toBe(
      "checkout.errors.paymentUnavailable"
    );
  });

  it("maps auth failures and falls back to the caller default", () => {
    expect(getCheckoutErrorKey({ code: 401 })).toBe("checkout.errors.sessionExpired");
    expect(getCheckoutErrorKey({ code: 500 }, "checkout.errors.orderUnavailable")).toBe(
      "checkout.errors.orderUnavailable"
    );
  });
});
