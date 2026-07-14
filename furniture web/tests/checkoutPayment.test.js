import { describe, expect, it } from "vitest";
import {
  buildPaymentReturnUrl,
  buildYudaoPaymentPayload,
  getCreatedOrderId,
  getPaymentRedirectTarget,
  getPaymentReturnParams,
  getPaymentReturnSummary,
  getPaymentFormDisplayContent,
  getPayOrderId,
  submitPaymentFormDisplay,
} from "../src/services/checkoutPayment.js";

describe("checkout payment helpers", () => {
  it("normalizes created order ids from numeric and object responses", () => {
    expect(getCreatedOrderId(901)).toBe(901);
    expect(getCreatedOrderId({ id: 902, payOrderId: 7002 })).toBe(902);
    expect(getCreatedOrderId({ data: { id: 903, payOrderId: 7003 } })).toBe(903);
    expect(getCreatedOrderId({ raw: { id: 904, pay_order_id: 7004 } })).toBe(904);
  });

  it("extracts Yudao pay order ids from common order response shapes", () => {
    expect(getPayOrderId({ payOrderId: 7002 })).toBe(7002);
    expect(getPayOrderId({ payOrder: { id: 7003 } })).toBe(7003);
    expect(getPayOrderId({ pay_order_id: 7004 })).toBe(7004);
    expect(getPayOrderId({ data: { payOrderId: 7005 } })).toBe(7005);
    expect(getPayOrderId({ raw: { payOrder: { id: 7006 } } })).toBe(7006);
    expect(getPayOrderId(901)).toBe("");
  });

  it("builds a Yudao payment submission payload only when payment is actionable", () => {
    expect(
      buildYudaoPaymentPayload(
        { id: 902, payOrderId: 7002 },
        { channelCode: "mock", returnUrl: "https://shop.example/orders?id=902" },
      ),
    ).toEqual({
      id: 7002,
      channelCode: "mock",
      channelExtras: {},
      displayMode: "url",
      returnUrl: "https://shop.example/orders?id=902",
    });

    expect(buildYudaoPaymentPayload({ id: 902 }, { channelCode: "mock" })).toBeNull();
    expect(buildYudaoPaymentPayload({ id: 902, payOrderId: 7002 }, { channelCode: "" })).toBeNull();
    expect(buildYudaoPaymentPayload({ id: 902, payOrderId: 7002 }, { channelCode: "mock" })).toBeNull();
    expect(
      buildYudaoPaymentPayload(
        { id: 902, payOrderId: "PAY/7002" },
        { channelCode: "mock", returnUrl: "https://shop.example/orders?id=902" },
      ),
    ).toBeNull();
    expect(
      buildYudaoPaymentPayload(
        { id: 902, payOrderId: 7002 },
        { channelCode: "mock", returnUrl: "/orders?id=902" },
      ),
    ).toBeNull();
    expect(
      buildYudaoPaymentPayload(
        { id: 902, payOrderId: 7002 },
        { channelCode: "alipay pc", returnUrl: "https://shop.example/orders?id=902" },
      ),
    ).toBeNull();
    expect(
      buildYudaoPaymentPayload(
        { id: 902, payOrderId: 7002 },
        { channelCode: "https://pay.example/channel", returnUrl: "https://shop.example/orders?id=902" },
      ),
    ).toBeNull();
    expect(
      buildYudaoPaymentPayload(
        { id: 902, payOrderId: 7002 },
        { channelCode: "YOUR_CHANNEL_CODE", returnUrl: "https://shop.example/orders?id=902" },
      ),
    ).toBeNull();
  });

  it("builds a payment return URL with order and pay order ids", () => {
    expect(buildPaymentReturnUrl("https://shop.example", 902, 7002)).toBe(
      "https://shop.example/orders?id=902&payOrderId=7002",
    );
    expect(buildPaymentReturnUrl("", "SO/902", "PAY/7002")).toBe(
      "/orders?id=SO%2F902&payOrderId=PAY%2F7002",
    );
  });

  it("extracts safe redirect targets from payment submission responses", () => {
    expect(getPaymentRedirectTarget({ payUrl: "https://pay.example/checkout/7002" })).toBe(
      "https://pay.example/checkout/7002",
    );
    expect(getPaymentRedirectTarget({ redirectUrl: "/orders?id=902" })).toBe("/orders?id=902");
    expect(getPaymentRedirectTarget({ displayMode: "url", displayContent: "https://pay.example/display" })).toBe(
      "https://pay.example/display",
    );
    expect(getPaymentRedirectTarget({ channelExtras: { payUrl: "https://pay.example/channel" } })).toBe(
      "https://pay.example/channel",
    );
  });

  it("rejects unsafe or missing payment redirect targets", () => {
    expect(getPaymentRedirectTarget({ payUrl: "javascript:alert(1)" })).toBe("");
    expect(getPaymentRedirectTarget({ payUrl: "//pay.example/checkout" })).toBe("");
    expect(getPaymentRedirectTarget({ displayMode: "form", displayContent: "<form></form>" })).toBe("");
    expect(getPaymentRedirectTarget(null)).toBe("");
  });

  it("extracts Yudao HTML form payment displays without treating them as redirects", () => {
    const html = '<form action="https://pay.example/submit" method="post"><input name="token" value="abc"></form>';

    expect(getPaymentFormDisplayContent({ displayMode: "form", displayContent: html })).toBe(html);
    expect(getPaymentRedirectTarget({ displayMode: "form", displayContent: html })).toBe("");
    expect(getPaymentFormDisplayContent({ displayMode: "url", displayContent: "https://pay.example" })).toBe("");
    expect(getPaymentFormDisplayContent({ displayMode: "form", displayContent: "<p>No form</p>" })).toBe("");
  });

  it("submits only the safe payment form display content", () => {
    const documentRef = createPaymentFormDocument();
    const submitted = submitPaymentFormDisplay(
      {
        displayMode: "form",
        displayContent:
          '<script>window.evil = true</script><img src=x onerror="window.evil=true"><form action="https://pay.example/submit" method="post" onsubmit="window.evil=true"><input name="token" value="abc"></form>',
      },
      documentRef,
    );

    expect(submitted).toBe(true);
    expect(documentRef.body.children).toHaveLength(1);
    expect(documentRef.body.children[0].children).toEqual([documentRef.form]);
    expect(documentRef.form.submitted).toBe(true);
    expect(documentRef.form.removedAttributes).toContain("onsubmit");
  });

  it("rejects unsafe payment form actions before appending anything", () => {
    const documentRef = createPaymentFormDocument();
    const submitted = submitPaymentFormDisplay(
      {
        displayMode: "form",
        displayContent: '<form action="javascript:alert(1)" method="post"><input name="token" value="abc"></form>',
      },
      documentRef,
    );

    expect(submitted).toBe(false);
    expect(documentRef.body.children).toHaveLength(0);
    expect(documentRef.form.submitted).toBe(false);
  });

  it("parses payment return query params for order and pay order refresh", () => {
    expect(getPaymentReturnParams("?id=902&payOrderId=7002")).toEqual({
      orderId: "902",
      payOrderId: "7002",
      status: "",
      message: "",
    });
    expect(getPaymentReturnParams("?orderId=903&pay_order_id=7003")).toEqual({
      orderId: "903",
      payOrderId: "7003",
      status: "",
      message: "",
    });
    expect(getPaymentReturnParams("?orderId=902&id=7002&result=success")).toMatchObject({
      orderId: "902",
      payOrderId: "7002",
      status: "paid",
    });
    expect(getPaymentReturnParams("?merchantOrderId=903&id=7003&payStatus=pending")).toMatchObject({
      orderId: "903",
      payOrderId: "7003",
      status: "waiting",
    });
  });

  it("normalizes payment return status for order page recovery messaging", () => {
    expect(getPaymentReturnParams("?id=902&payOrderId=7002&status=cancelled&message=Buyer%20closed%20checkout")).toEqual({
      orderId: "902",
      payOrderId: "7002",
      status: "cancelled",
      message: "Buyer closed checkout",
    });
    expect(getPaymentReturnParams("?id=902&payOrderId=7002&result=fail")).toMatchObject({
      status: "failed",
    });

    expect(getPaymentReturnSummary({ status: "cancelled", message: "Buyer closed checkout" })).toEqual({
      status: "cancelled",
      titleKey: "orders.paymentReturn.cancelled.title",
      messageKey: "orders.paymentReturn.cancelled.message",
      canRetry: true,
      detail: "Buyer closed checkout",
    });
    expect(getPaymentReturnSummary({ status: "paid" })).toMatchObject({
      status: "paid",
      titleKey: "orders.paymentReturn.paid.title",
      canRetry: false,
    });
    expect(getPaymentReturnSummary({ status: "" })).toBeNull();
  });

  it("keeps unclassified payment return statuses visible for recovery", () => {
    expect(getPaymentReturnParams("?id=902&payOrderId=7002&status=processing")).toEqual({
      orderId: "902",
      payOrderId: "7002",
      status: "unknown",
      message: "",
    });
    expect(getPaymentReturnSummary({ status: "processing" })).toEqual({
      status: "unknown",
      titleKey: "orders.paymentReturn.unknown.title",
      messageKey: "orders.paymentReturn.unknown.message",
      canRetry: true,
      detail: "",
    });
  });
});

const createPaymentFormDocument = () => {
  const form = {
    attributes: [
      { name: "action", value: "" },
      { name: "method", value: "post" },
      { name: "onsubmit", value: "window.evil=true" },
    ],
    children: [],
    removedAttributes: [],
    submitted: false,
    getAttribute(name) {
      return this.attributes.find((attribute) => attribute.name === name)?.value || "";
    },
    removeAttribute(name) {
      this.removedAttributes.push(name);
      this.attributes = this.attributes.filter((attribute) => attribute.name !== name);
    },
    querySelectorAll() {
      return [];
    },
    submit() {
      this.submitted = true;
    },
  };
  const script = { removed: false, remove() { this.removed = true; } };
  const image = { removed: false, remove() { this.removed = true; } };

  return {
    form,
    body: {
      children: [],
      appendChild(child) {
        this.children.push(child);
      },
    },
    createElement() {
      return {
        hidden: false,
        children: [],
        set innerHTML(value) {
          const action = String(value).match(/action="([^"]*)"/i)?.[1] || "";
          form.attributes[0].value = action;
          this.children = [script, image, form];
        },
        querySelector(selector) {
          return selector === "form" ? form : null;
        },
        querySelectorAll(selector) {
          return selector === "script" ? [script] : [];
        },
        appendChild(child) {
          this.children.push(child);
        },
      };
    },
  };
};
