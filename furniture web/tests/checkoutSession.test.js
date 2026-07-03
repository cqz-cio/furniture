import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import {
  buildAddressVerificationAudit,
  buildAddressConfirmationRemark,
  buildConfirmedShippingAddressInput,
  buildLocalCheckoutSummary,
  buildYudaoAddressPayload,
  buildYudaoOrderPayload,
  canUseYudaoCheckout,
  getCheckoutReturnPath,
  getCheckoutMode,
  getCheckoutPresentation,
  getSelectedAddressId,
  getOrderDetailPath,
  savedAddressToShippingForm,
} from "../src/services/checkoutSession.js";
import { YUDAO_US_DEFAULT_AREA_ID } from "../src/services/usAddress.js";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("checkout session helpers", () => {
  const yudaoItems = [
    { id: 1, spuId: 1, skuId: 11, cartId: 101, quantity: 2, price: 1200, source: "yudao", name: "Sofa" },
    { id: 2, spuId: 2, skuId: 12, cartId: 102, quantity: 1, price: 400, source: "yudao", name: "Chair" },
  ];

  it("allows yudao checkout only when every item has a remote cart id", () => {
    expect(canUseYudaoCheckout(yudaoItems)).toBe(true);
    expect(canUseYudaoCheckout([{ ...yudaoItems[0], cartId: undefined }])).toBe(false);
    expect(canUseYudaoCheckout([{ ...yudaoItems[0], id: undefined, spuId: undefined }])).toBe(false);
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

  it("builds yudao address payload from checkout shipping form fields", () => {
    expect(
      buildYudaoAddressPayload({
        firstName: "Ada",
        lastName: "Lovelace",
        street: "12 Main St",
        apartment: "Suite 4",
        city: "Boston",
        state: "MA",
        postalCode: "02116",
        phone: "555-0100",
        areaId: "301",
      }),
    ).toEqual({
      name: "Ada Lovelace",
      mobile: "555-0100",
      areaId: 301,
      detailAddress: "12 Main St, Suite 4, Boston, MA 02116",
      defaultStatus: true,
    });
  });

  it("adds confirmed address verification audit when saving checkout addresses", () => {
    const addressConfirmation = {
      source: "google-address-validation",
      addressSource: "new",
      status: "verified",
      reason: "google-address-complete",
      choice: "original",
      deliverable: true,
      confirmedAt: "2026-06-16T10:00:00.000Z",
      providerResponseId: "google-response-1",
      metadata: {
        responseId: "metadata-response-1",
      },
      selectedAddress: {
        street: "12 MAIN ST",
        city: "New York",
        state: "NY",
        postalCode: "10001",
      },
    };

    expect(
      buildYudaoAddressPayload(
        {
          firstName: "Ada",
          lastName: "Lovelace",
          street: "12 MAIN ST",
          city: "New York",
          state: "NY",
          postalCode: "10001",
          phone: "555-0100",
        },
        { addressConfirmation },
      ),
    ).toMatchObject({
      addressVerification: {
        source: "google-address-validation",
        addressSource: "new",
        status: "verified",
        providerResponseId: "google-response-1",
        selectedAddress: {
          street: "12 MAIN ST",
          city: "New York",
          state: "NY",
          postalCode: "10001",
        },
      },
    });
  });

  it("preserves buyer contact fields when saving a verified selected address", () => {
    const shippingForm = {
      firstName: "Ada",
      lastName: "Lovelace",
      street: "1 Market St",
      city: "San Francisco",
      state: "CA",
      postalCode: "94105",
      phone: "4155550134",
      areaId: 1,
    };
    const selectedAddress = {
      street: "1 MARKET ST",
      city: "San Francisco",
      state: "CA",
      postalCode: "94105",
    };

    expect(buildYudaoAddressPayload(buildConfirmedShippingAddressInput(shippingForm, selectedAddress))).toMatchObject({
      name: "Ada Lovelace",
      mobile: "4155550134",
      detailAddress: "1 MARKET ST, San Francisco, CA 94105",
    });
  });

  it("omits incomplete address verification audit when saving checkout addresses", () => {
    const addressConfirmation = {
      source: "google-address-validation",
      addressSource: "new",
      status: "verified",
      choice: "original",
      confirmedAt: "2026-06-16T10:00:00.000Z",
      selectedAddress: {
        street: "12 MAIN ST",
      },
    };

    expect(buildAddressVerificationAudit(addressConfirmation)).toBeNull();
    expect(
      buildYudaoAddressPayload(
        {
          firstName: "Ada",
          lastName: "Lovelace",
          street: "12 MAIN ST",
          city: "New York",
          state: "NY",
          postalCode: "10001",
          phone: "555-0100",
        },
        { addressConfirmation },
      ),
    ).not.toHaveProperty("addressVerification");
  });

  it("omits unsupported address verification audit values before sending Yudao orders", () => {
    const addressConfirmation = {
      source: "trusted-because-user-said-so",
      addressSource: "legacy",
      status: "maybe",
      choice: "skip-review",
      confirmedAt: "2026-06-16T10:00:00.000Z",
      selectedAddress: {
        street: "12 MAIN ST",
        city: "New York",
        state: "NY",
        postalCode: "10001",
      },
    };

    expect(buildAddressVerificationAudit(addressConfirmation)).toBeNull();
    expect(buildYudaoOrderPayload(yudaoItems, { addressId: 2001, addressConfirmation })).not.toHaveProperty(
      "addressVerification",
    );
  });

  it("adds compact address confirmation details to the order remark", () => {
    const addressConfirmation = {
      source: "google-address-validation",
      addressSource: "saved",
      status: "suggested",
      reason: "postal-region-mismatch",
      choice: "suggested",
      deliverable: true,
      confirmedAt: "2026-06-16T10:00:00.000Z",
      providerResponseId: "google-response-1",
      metadata: {
        responseId: "metadata-response-1",
        formattedAddress: "12 MAIN ST, NEW YORK, NY 10001, USA",
      },
      providerStatus: "fallback",
      originalAddress: {
        street: "12 Main Street",
        city: "Brooklyn",
        state: "CA",
        postalCode: "10001",
      },
      suggestedAddress: {
        street: "12 MAIN ST",
        city: "New York",
        state: "NY",
        postalCode: "10001",
      },
      selectedAddress: {
        street: "12 MAIN ST",
        city: "New York",
        state: "NY",
        postalCode: "10001",
      },
    };

    expect(buildAddressConfirmationRemark(addressConfirmation)).toBe(
      "Address confirmation: status=suggested; choice=suggested; addressSource=saved; reason=postal-region-mismatch; selected=12 MAIN ST, New York, NY 10001",
    );
    expect(buildAddressVerificationAudit(addressConfirmation)).toEqual({
      source: "google-address-validation",
      addressSource: "saved",
      status: "suggested",
      reason: "postal-region-mismatch",
      choice: "suggested",
      deliverable: true,
      confirmedAt: "2026-06-16T10:00:00.000Z",
      providerResponseId: "google-response-1",
      providerStatus: "fallback",
      originalAddress: {
        street: "12 Main Street",
        city: "Brooklyn",
        state: "CA",
        postalCode: "10001",
      },
      suggestedAddress: {
        street: "12 MAIN ST",
        city: "New York",
        state: "NY",
        postalCode: "10001",
      },
      selectedAddress: {
        street: "12 MAIN ST",
        city: "New York",
        state: "NY",
        postalCode: "10001",
      },
    });
    expect(buildYudaoOrderPayload(yudaoItems, { addressId: 2001, addressConfirmation })).toMatchObject({
      addressVerification: {
        source: "google-address-validation",
        addressSource: "saved",
        status: "suggested",
        providerResponseId: "google-response-1",
        providerStatus: "fallback",
      },
      remark:
        "Address confirmation: status=suggested; choice=suggested; addressSource=saved; reason=postal-region-mismatch; selected=12 MAIN ST, New York, NY 10001",
    });
  });

  it("uses the shared default Yudao area id when US checkout does not expose Yudao regions", () => {
    const source = readSource("../src/services/checkoutSession.js");

    expect(source).toContain("YUDAO_US_DEFAULT_AREA_ID");
    expect(source).not.toContain("form.areaId || 1");
    expect(
      buildYudaoAddressPayload({
        firstName: "Ada",
        lastName: "Lovelace",
        street: "12 Main St",
        city: "Boston",
        state: "MA",
        postalCode: "02116",
        phone: "555-0100",
      }),
    ).toMatchObject({
      areaId: YUDAO_US_DEFAULT_AREA_ID,
      detailAddress: "12 Main St, Boston, MA 02116",
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
      message: "This bag contains preview-only items. Demo, local, or membership preview items are not persisted to Yudao and cannot create a live Yudao order.",
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

  it("normalizes saved Yudao addresses into checkout shipping fields", () => {
    expect(
      savedAddressToShippingForm({
        name: "Ada Lovelace",
        mobile: "555-0100",
        areaName: "Boston, MA",
        detailAddress: "12 Main St, Suite 4, Boston, MA 02116",
        raw: { areaId: 1 },
      }),
    ).toEqual({
      firstName: "Ada",
      lastName: "Lovelace",
      country: "United States",
      street: "12 Main St",
      apartment: "Suite 4",
      city: "Boston",
      state: "MA",
      postalCode: "02116",
      phone: "555-0100",
      areaId: 1,
    });
  });

  it("normalizes structured saved US address fields into checkout shipping fields", () => {
    expect(
      savedAddressToShippingForm({
        name: "Grace Hopper",
        mobile: "555-0101",
        raw: {
          street: "1 Navy Way",
          apartment: "Unit 2",
          city: "Arlington",
          state: "VA",
          postalCode: "22201",
          areaId: 2,
        },
      }),
    ).toEqual({
      firstName: "Grace",
      lastName: "Hopper",
      country: "United States",
      street: "1 Navy Way",
      apartment: "Unit 2",
      city: "Arlington",
      state: "VA",
      postalCode: "22201",
      phone: "555-0101",
      areaId: 2,
    });
  });

  it("builds order detail route with query id", () => {
    expect(getOrderDetailPath(12)).toBe("/orders?id=12");
    expect(getOrderDetailPath(12, 7002)).toBe("/orders?id=12&payOrderId=7002");
  });
});
