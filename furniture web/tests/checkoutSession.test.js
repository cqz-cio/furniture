import { describe, expect, it } from "vitest";
import {
  buildLocalCheckoutSummary,
  buildYudaoOrderPayload,
  canUseYudaoCheckout,
  getCheckoutReturnPath,
  getCheckoutMode,
  getCheckoutPresentation,
  getSelectedAddressId,
  getOrderDetailPath,
} from "../src/services/checkoutSession.js";

describe("checkout session helpers", () => {
  const yudaoItems = [
    { skuId: 11, cartId: 101, quantity: 2, price: 1200, source: "yudao", name: "Sofa" },
    { skuId: 12, cartId: 102, quantity: 1, price: 400, source: "yudao", name: "Chair" },
  ];

  it("allows yudao checkout only when every item has a remote cart id", () => {
    expect(canUseYudaoCheckout(yudaoItems)).toBe(true);
    expect(canUseYudaoCheckout([{ ...yudaoItems[0], cartId: undefined }])).toBe(false);
    expect(canUseYudaoCheckout([{ ...yudaoItems[0], source: "demo" }])).toBe(false);
  });

  it("builds yudao order payload from cart ids, address, and delivery type", () => {
    expect(buildYudaoOrderPayload(yudaoItems, { addressId: 2001 })).toEqual({
      items: [
        { skuId: 11, count: 2, cartId: 101 },
        { skuId: 12, count: 1, cartId: 102 },
      ],
      pointStatus: false,
      deliveryType: 1,
      addressId: 2001,
      remark: "",
    });
  });

  it("summarizes local checkout totals without remote order data", () => {
    expect(buildLocalCheckoutSummary(yudaoItems)).toEqual({
      quantity: 3,
      subtotal: 2800,
      items: yudaoItems,
    });
  });

  it("reports checkout mode from cart source and token state", () => {
    expect(getCheckoutMode(yudaoItems, "token")).toBe("yudao");
    expect(getCheckoutMode(yudaoItems, "")).toBe("token-required");
    expect(getCheckoutMode([{ ...yudaoItems[0], source: "demo" }], "token")).toBe("local-preview");
    expect(getCheckoutMode([], "token")).toBe("empty");
  });

  it("maps checkout modes to polished page copy and action states", () => {
    expect(getCheckoutPresentation("yudao")).toEqual({
      title: "Review Your Order",
      message: "Confirm your delivery address and create the connected catalog order.",
      cta: "Create Connected Order",
      canSubmit: true,
    });
    expect(getCheckoutPresentation("token-required")).toMatchObject({
      cta: "Add Token To Continue",
      canSubmit: false,
    });
    expect(getCheckoutPresentation("local-preview")).toMatchObject({
      message: "Review your Oakved selections before final account checkout is connected.",
      cta: "Review Only",
      canSubmit: false,
    });
    expect(getCheckoutPresentation("empty")).toMatchObject({
      title: "Your Bag Is Empty",
      cta: "Return To Gallery",
      canSubmit: false,
    });
  });

  it("returns the checkout route used by the cart drawer", () => {
    expect(getCheckoutReturnPath()).toBe("/checkout");
  });

  it("uses selected address first and then default address", () => {
    expect(getSelectedAddressId(9, { id: 8 })).toBe(9);
    expect(getSelectedAddressId(undefined, { id: 8 })).toBe(8);
    expect(getSelectedAddressId(undefined, null)).toBe(undefined);
  });

  it("builds order detail route with query id", () => {
    expect(getOrderDetailPath(12)).toBe("/orders?id=12");
  });
});
