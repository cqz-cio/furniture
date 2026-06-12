const STOCK_ERROR_CODES = new Set([1006003001, 1006003002, 1006003003]);
const AUTH_ERROR_CODES = new Set([401]);

const normalizeMessage = (error = {}) =>
  String(error?.msg || error?.message || error?.data?.msg || error?.data?.message || "").toLowerCase();

const hasAny = (message, patterns) => patterns.some((pattern) => pattern.test(message));

export const getCheckoutErrorKey = (error, fallbackKey = "checkout.errors.loadUnavailable") => {
  const code = Number(error?.code);
  const message = normalizeMessage(error);

  if (AUTH_ERROR_CODES.has(code)) return "checkout.errors.sessionExpired";
  if (
    STOCK_ERROR_CODES.has(code) ||
    hasAny(message, [/stock/, /inventory/, /sold out/, /out of stock/, /库存/, /缺货/])
  ) {
    return "checkout.errors.stockUnavailable";
  }
  if (hasAny(message, [/address/, /delivery/, /shipping/, /service area/, /地址/, /配送/, /送达/])) {
    return "checkout.errors.addressUnavailable";
  }
  if (hasAny(message, [/price/, /amount/, /settle/, /settlement/, /pay price/, /价格/, /金额/, /结算/])) {
    return "checkout.errors.priceChanged";
  }

  return fallbackKey;
};
