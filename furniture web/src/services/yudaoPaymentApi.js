import { requestYudao } from "./yudaoRequest.js";

const isYudaoNumericId = (id) => /^\d+$/.test(String(id || "").trim());

export const submitPayOrder = (payload, options = {}) => {
  if (!isYudaoNumericId(payload?.id)) {
    return Promise.reject(new Error("Invalid Yudao pay order id"));
  }

  return requestYudao("/pay/order/submit", {
    ...options,
    method: "POST",
    body: JSON.stringify(payload),
  });
};

export const getPayOrder = (id, options = {}) => {
  const { sync, ...requestOptions } = options;
  const payOrderRef = String(id || "").trim();
  if (!payOrderRef) {
    return Promise.reject(new Error("Invalid Yudao pay order reference"));
  }

  const search = new URLSearchParams();
  search.set(isYudaoNumericId(payOrderRef) ? "id" : "no", payOrderRef);
  if (sync) search.set("sync", "true");
  return requestYudao(`/pay/order/get?${search.toString()}`, requestOptions);
};
