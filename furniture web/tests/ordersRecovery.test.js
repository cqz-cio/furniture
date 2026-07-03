import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("orders page recovery actions", () => {
  it("offers recovery actions for token, service, and empty states", () => {
    const source = readSource("../src/pages/OrdersPage.vue");

    expect(source).toContain("isYudaoAuthError");
    expect(source).toContain("membershipRoutes.checkoutAuth");
    expect(source).toContain('error.value = t("orders.error")');
    expect(source).toContain('t("orders.actions.connectAccount")');
    expect(source).toContain('t("orders.actions.retry")');
    expect(source).toContain('t("orders.actions.shop")');
    expect(source).toContain('@click="loadOrders"');
    expect(source).not.toContain("Order service is unavailable. Please try again later.");
  });

  it("treats expired Yudao sessions as sign-in-required instead of a generic order load error", () => {
    const source = readSource("../src/pages/OrdersPage.vue");

    expect(source).toContain("if (isYudaoAuthError(error))");
    expect(source).toContain("tokenRequired.value = true");
    expect(source).toContain("return");
    expect(source).toContain('error.value = t("orders.error")');
  });

  it("refreshes pay order status when returning from a payment channel", () => {
    const source = readSource("../src/pages/OrdersPage.vue");

    expect(source).toContain("getPaymentReturnParams");
    expect(source).toContain("getPayOrder");
    expect(source).toContain("paymentReturn");
    expect(source).toContain("payOrderStatus");
    expect(source).toContain('detail.value.payStatus ? "paid" : "unpaid"');
    expect(source).toContain("await getPayOrder(activePayOrderId.value, { sync: true })");
    expect(source).toContain('t("orders.paymentStatus"');
    expect(source).toContain('t("orders.payOrderLabel"');
  });

  it("maps raw pay order statuses to localized readable labels", () => {
    const source = readSource("../src/pages/OrdersPage.vue");
    const i18n = readSource("../src/i18n.js");

    expect(source).toContain("payOrderStatusLabelKey");
    expect(source).toContain("normalizePayOrderStatus");
    expect(source).toContain("payOrderStatusMap");
    expect(source).toContain('["0", "waiting"]');
    expect(source).toContain('["10", "paid"]');
    expect(source).toContain('["20", "refunded"]');
    expect(source).toContain('["30", "closed"]');
    expect(source).toContain('t(payOrderStatusLabelKey)');
    expect(i18n).toContain("paymentStatuses");
    expect(i18n).toContain("waiting");
    expect(i18n).toContain("paid");
    expect(i18n).toContain("closed");
    expect(i18n).toContain("refunded");
    expect(i18n).toContain("unknown");
  });

  it("lets buyers resume a waiting payment from the order detail", () => {
    const source = readSource("../src/pages/OrdersPage.vue");
    const i18n = readSource("../src/i18n.js");

    expect(source).toContain("getOrderDetailPath");
    expect(source).toContain("getOrderDetailPath(order.id, order.payOrderId)");
    expect(source).toContain("submitPayOrder");
    expect(source).toContain("buildYudaoPaymentPayload");
    expect(source).toContain("normalizeYudaoPayChannelCode");
    expect(source).toContain("normalizeYudaoPayChannelCode(import.meta.env.VITE_YUDAO_PAY_CHANNEL_CODE)");
    expect(source).toContain("buildPaymentReturnUrl");
    expect(source).toContain("getPaymentRedirectTarget");
    expect(source).toContain("submitPaymentFormDisplay");
    expect(source).toContain("paymentChannelCode");
    expect(source).toContain("paymentChannelConfigured");
    expect(source).toContain("paymentResumeBusy");
    expect(source).toContain("paymentResumeError");
    expect(source).toContain("activePayOrderId");
    expect(source).toContain("paymentReturn.value.payOrderId || detail.value?.payOrderId");
    expect(source).toContain("resolvedPayOrderId");
    expect(source).toContain("payOrder.value?.id");
    expect(source).toContain("canResumePayment");
    expect(source).toContain("Boolean(activePayOrderId.value)");
    expect(source).toContain('normalizePayOrderStatus(payOrderStatus.value) === "waiting"');
    expect(source).toContain("resumePayment");
    expect(source).toContain("{ payOrderId: resolvedPayOrderId.value }");
    expect(source).toContain("buildPaymentReturnUrl(window.location.origin, orderId.value, resolvedPayOrderId.value)");
    expect(source).toContain("await submitPayOrder(paymentPayload)");
    expect(source).toContain("submitPaymentFormDisplay(paymentResult, window.document)");
    expect(source).toContain("window.location.assign(paymentRedirectTarget)");
    expect(source).toContain('t("orders.actions.resumePayment")');
    expect(source).toContain('t("orders.paymentResumeUnavailable")');
    expect(source).toContain('class="orders-payment-resume"');
    expect(i18n).toContain("resumePayment");
    expect(i18n).toContain("paymentResumeUnavailable");
  });

  it("explains failed or cancelled payment returns and offers recovery actions", () => {
    const source = readSource("../src/pages/OrdersPage.vue");
    const i18n = readSource("../src/i18n.js");

    expect(source).toContain("getPaymentReturnSummary");
    expect(source).toContain("paymentReturnSummary");
    expect(source).toContain('class="orders-payment-return"');
    expect(source).toContain('t(paymentReturnSummary.titleKey)');
    expect(source).toContain('t(paymentReturnSummary.messageKey)');
    expect(source).toContain("paymentReturnSummary.detail");
    expect(source).toContain("paymentReturnSummary.canRetry");
    expect(source).toContain('t("orders.actions.resumePayment")');
    expect(source).toContain('t("orders.actions.refreshPaymentStatus")');
    expect(i18n).toContain("paymentReturn");
    expect(i18n).toContain("cancelled");
    expect(i18n).toContain("failed");
  });

  it("does not offer resume payment from a return summary without a pay order id", () => {
    const source = readSource("../src/pages/OrdersPage.vue");

    expect(source).toContain('<button v-if="activePayOrderId" class="orders-payment-resume"');
    expect(source).toContain("paymentReturnSummary.canRetry");
  });

  it("explains why a waiting payment cannot resume when the payment channel is missing", () => {
    const source = readSource("../src/pages/OrdersPage.vue");
    const i18n = readSource("../src/i18n.js");

    expect(source).toContain("canShowPaymentChannelNotice");
    expect(source).toContain("!paymentChannelConfigured.value");
    expect(source).toContain('normalizePayOrderStatus(payOrderStatus.value) === "waiting"');
    expect(source).toContain('t("orders.paymentChannelUnavailable")');
    expect(source).toContain('v-if="canShowPaymentChannelNotice"');
    expect(i18n).toContain("paymentChannelUnavailable");
  });

  it("keeps the order detail visible when pay order status refresh fails", () => {
    const source = readSource("../src/pages/OrdersPage.vue");
    const i18n = readSource("../src/i18n.js");

    expect(source).toContain("payOrderError");
    expect(source).toContain("refreshPayOrderStatus");
    expect(source).toContain("if (!activePayOrderId.value) return;");
    expect(source).toContain("await getPayOrder(activePayOrderId.value, { sync: true })");
    expect(source).toContain('payOrderError.value = t("orders.paymentStatusUnavailable")');
    expect(source).toContain("await refreshPayOrderStatus(requestId)");
    expect(source).toContain('v-if="payOrderError"');
    expect(source).toContain("{{ payOrderError }}");
    expect(source).toContain('class="orders-payment-retry"');
    expect(source).toContain('t("orders.actions.refreshPaymentStatus")');
    expect(source).toContain('@click="loadOrders"');
    expect(i18n).toContain("paymentStatusUnavailable");
  });

  it("does not offer duplicate payment when a paid return cannot refresh immediately", () => {
    const source = readSource("../src/pages/OrdersPage.vue");

    expect(source).toContain("hasPaidPaymentReturn");
    expect(source).toContain('paymentReturnSummary.value?.status === "paid"');
    expect(source).toContain('hasPaidPaymentReturn.value ? "paid"');
    expect(source).toContain("!hasPaidPaymentReturn.value");
    expect(source).toContain("canResumePayment");
    expect(source).toContain("canShowPaymentChannelNotice");
  });

  it("shows address verification audit details on order detail", () => {
    const source = readSource("../src/pages/OrdersPage.vue");
    const service = readSource("../src/services/orderAddressVerification.js");
    const i18n = readSource("../src/i18n.js");

    expect(source).toContain("addressVerificationSummary");
    expect(source).toContain("buildOrderAddressVerificationSummary");
    expect(source).toContain("detail.value?.addressVerification");
    expect(source).toContain('t("orders.addressVerification.title")');
    expect(service).toContain("orders.addressVerification.verificationSources");
    expect(source).toContain('t("orders.addressVerification.source"');
    expect(source).toContain("addressVerificationSummary.addressSource");
    expect(service).toContain("orders.addressVerification.addressSources");
    expect(source).toContain('t("orders.addressVerification.addressSource"');
    expect(service).toContain("orders.addressVerification.statuses");
    expect(source).toContain('t("orders.addressVerification.status"');
    expect(service).toContain("orders.addressVerification.choices");
    expect(source).toContain('t("orders.addressVerification.choice"');
    expect(source).toContain('t("orders.addressVerification.reason"');
    expect(source).toContain("addressVerificationSummary.reasonLabelKey");
    expect(source).toContain('t("orders.addressVerification.confirmedAt"');
    expect(source).toContain('t("orders.addressVerification.original"');
    expect(source).toContain('t("orders.addressVerification.suggested"');
    expect(source).toContain('t("orders.addressVerification.selected"');
    expect(source).toContain('t("orders.addressVerification.providerResponseId"');
    expect(source).toContain('t("orders.addressVerification.providerStatus"');
    expect(source).toContain("addressVerificationSummary.providerStatusLabelKey");
    expect(source).toContain("addressVerificationSummary.warningKey");
    expect(source).toContain("addressVerificationSummary.providerWarningKey");
    expect(source).toContain("addressVerificationSummary.sourceWarningKey");
    expect(source).toContain("order-address-verification-warning");
    expect(i18n).toContain("addressVerification");
    expect(i18n).toContain("postal-region-mismatch");
    expect(i18n).toContain("providerResponseId");
    expect(i18n).toContain("providerStatuses");
    expect(i18n).toContain("providerFallbackWarning");
    expect(i18n).toContain("localPostalRegionWarning");
  });

  it("styles order recovery actions", () => {
    const source = readSource("../src/styles.css");

    expect(source).toContain(".orders-recovery-actions");
    expect(source).toContain(".orders-recovery-action");
    expect(source).toContain(".orders-payment-warning");
    expect(source).toContain(".orders-payment-return");
    expect(source).toContain(".orders-payment-retry");
    expect(source).toContain(".orders-payment-resume");
    expect(source).toContain(".order-address-verification dd");
    expect(source).toContain("overflow-wrap: anywhere");
  });
});
