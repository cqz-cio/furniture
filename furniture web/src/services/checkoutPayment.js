const firstValue = (...values) => values.find((value) => value !== undefined && value !== null && value !== "");
const isSafeRedirectTarget = (value) => /^https?:\/\//i.test(value) || (value.startsWith("/") && !value.startsWith("//"));
const isYudaoNumericId = (value) => /^\d+$/.test(String(value || "").trim());
const invalidPayChannelCodes = new Set(["YOUR_CHANNEL_CODE", "CHANGE_ME", "CHANGEME"]);
export const normalizeYudaoPayChannelCode = (value) => {
  const code = String(value || "").trim();
  if (!code || invalidPayChannelCodes.has(code.toUpperCase())) return "";
  return /^[A-Za-z0-9_-]+$/.test(code) ? code : "";
};
const paymentOrderSource = (orderResult) => {
  if (!orderResult || typeof orderResult !== "object") return {};
  return orderResult;
};
const nestedPaymentOrderSources = (orderResult) => [
  paymentOrderSource(orderResult),
  paymentOrderSource(orderResult?.data),
  paymentOrderSource(orderResult?.raw),
];
const paymentReturnStatusMap = new Map([
  ["cancel", "cancelled"],
  ["cancelled", "cancelled"],
  ["canceled", "cancelled"],
  ["fail", "failed"],
  ["failed", "failed"],
  ["failure", "failed"],
  ["error", "failed"],
  ["success", "paid"],
  ["paid", "paid"],
  ["complete", "paid"],
  ["completed", "paid"],
  ["waiting", "waiting"],
  ["pending", "waiting"],
]);

const normalizePaymentReturnStatus = (status) => {
  const rawStatus = String(status || "").trim().toLowerCase();
  if (!rawStatus) return "";
  return paymentReturnStatusMap.get(rawStatus) || "unknown";
};

export const getCreatedOrderId = (orderResult) =>
  firstValue(
    ...nestedPaymentOrderSources(orderResult).flatMap((source) => [source.id, source.orderId, source.order_id]),
    typeof orderResult === "object" ? "" : orderResult,
  ) || "";

export const getPayOrderId = (orderResult) =>
  firstValue(
    ...nestedPaymentOrderSources(orderResult).flatMap((source) => [
      source.payOrderId,
      source.pay_order_id,
      source.payOrder?.id,
      source.pay_order?.id,
    ]),
  ) || "";

export const buildYudaoPaymentPayload = (orderResult, options = {}) => {
  const payOrderId = getPayOrderId(orderResult);
  const channelCode = normalizeYudaoPayChannelCode(options.channelCode);
  const returnUrl = String(options.returnUrl || "").trim();

  if (!payOrderId || !isYudaoNumericId(payOrderId) || !channelCode || !/^https?:\/\//i.test(returnUrl)) return null;

  return {
    id: payOrderId,
    channelCode,
    channelExtras: options.channelExtras || {},
    displayMode: String(options.displayMode || "url").trim(),
    returnUrl,
  };
};

export const buildPaymentReturnUrl = (origin = "", orderId = "", payOrderId = "") => {
  const search = new URLSearchParams();
  if (orderId) search.set("id", String(orderId));
  if (payOrderId) search.set("payOrderId", String(payOrderId));

  return `${String(origin || "").replace(/\/$/, "")}/orders?${search.toString()}`;
};

export const getPaymentRedirectTarget = (paymentResult) => {
  const displayContent =
    String(paymentResult?.displayMode || "").toLowerCase() === "url" ? paymentResult?.displayContent : "";
  const target = String(
    firstValue(
      paymentResult?.payUrl,
      paymentResult?.redirectUrl,
      paymentResult?.url,
      paymentResult?.channelExtras?.payUrl,
      paymentResult?.channelExtras?.redirectUrl,
      paymentResult?.data?.payUrl,
      paymentResult?.data?.redirectUrl,
      displayContent,
    ) || "",
  ).trim();

  return target && isSafeRedirectTarget(target) ? target : "";
};

export const getPaymentFormDisplayContent = (paymentResult) => {
  const displayContent = String(paymentResult?.displayContent || "").trim();
  if (String(paymentResult?.displayMode || "").toLowerCase() !== "form") return "";
  return /<form[\s>]/i.test(displayContent) ? displayContent : "";
};

const removeUnsafePaymentFormContent = (form) => {
  form.querySelectorAll?.("script, iframe, object, embed").forEach((node) => node.remove());
  const elements = [form, ...Array.from(form.querySelectorAll?.("*") || [])];
  elements.forEach((element) => {
    Array.from(element.attributes || []).forEach((attribute) => {
      const name = String(attribute.name || "").toLowerCase();
      const value = String(attribute.value || "").trim();
      if (name.startsWith("on") || (["action", "formaction", "href", "src"].includes(name) && /^javascript:/i.test(value))) {
        element.removeAttribute?.(attribute.name);
      }
    });
  });
};

export const submitPaymentFormDisplay = (paymentResult, documentRef = globalThis.document) => {
  const displayContent = getPaymentFormDisplayContent(paymentResult);
  if (!displayContent || !documentRef?.createElement || !documentRef?.body) return false;

  const container = documentRef.createElement("div");
  container.innerHTML = displayContent;

  const form = container.querySelector?.("form");
  const action = String(form?.getAttribute?.("action") || "").trim();
  if (!form || !isSafeRedirectTarget(action)) return false;

  removeUnsafePaymentFormContent(form);
  const safeContainer = documentRef.createElement("div");
  safeContainer.hidden = true;
  safeContainer.appendChild(form);
  documentRef.body.appendChild(safeContainer);
  if (typeof form.submit === "function") {
    form.submit();
  } else {
    form.dispatchEvent?.(new Event("submit", { cancelable: true }));
  }
  return true;
};

export const getPaymentReturnParams = (search = "") => {
  const params = new URLSearchParams(String(search || "").replace(/^\?/, ""));
  const status = normalizePaymentReturnStatus(firstValue(params.get("status"), params.get("result"), params.get("payStatus")));
  const explicitOrderId = firstValue(
    params.get("orderId"),
    params.get("order_id"),
    params.get("merchantOrderId"),
    params.get("merchant_order_id"),
  );
  const idParam = params.get("id");

  return {
    orderId: String(firstValue(explicitOrderId, idParam) || ""),
    payOrderId: String(firstValue(params.get("payOrderId"), params.get("pay_order_id"), params.get("payId"), explicitOrderId ? idParam : "") || ""),
    status,
    message: String(firstValue(params.get("message"), params.get("msg"), params.get("reason")) || ""),
  };
};

export const getPaymentReturnSummary = (paymentReturn = {}) => {
  const status = normalizePaymentReturnStatus(paymentReturn.status);
  if (!status) return null;

  const safeStatus = ["cancelled", "failed", "paid", "waiting"].includes(status) ? status : "unknown";

  return {
    status: safeStatus,
    titleKey: `orders.paymentReturn.${safeStatus}.title`,
    messageKey: `orders.paymentReturn.${safeStatus}.message`,
    canRetry: ["cancelled", "failed", "waiting", "unknown"].includes(safeStatus),
    detail: String(paymentReturn.message || "").trim(),
  };
};
