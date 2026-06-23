const STOCK_ERROR_CODES = new Set([1006003001, 1006003002, 1006003003]);
const AUTH_ERROR_CODES = new Set([401]);
const ADDRESS_CONFIRMATION_ERROR_CODES = new Set([1011000040]);
const PAYMENT_ERROR_CODES = new Set([1007002000, 1007002001, 1007002002, 1007002003, 1007003002]);

const normalizeMessage = (error = {}) =>
  String(error?.msg || error?.message || error?.data?.msg || error?.data?.message || "").toLowerCase();

const hasAny = (message, patterns) => patterns.some((pattern) => pattern.test(message));

export const getCheckoutErrorKey = (error, fallbackKey = "checkout.errors.loadUnavailable") => {
  const code = Number(error?.code);
  const message = normalizeMessage(error);

  if (AUTH_ERROR_CODES.has(code)) return "checkout.errors.sessionExpired";
  if (STOCK_ERROR_CODES.has(code) || hasAny(message, [/stock/, /inventory/, /sold out/, /out of stock/])) {
    return "checkout.errors.stockUnavailable";
  }
  if (ADDRESS_CONFIRMATION_ERROR_CODES.has(code)) {
    return "checkout.errors.addressConfirmationRequired";
  }
  if (PAYMENT_ERROR_CODES.has(code)) {
    return "checkout.errors.paymentUnavailable";
  }
  if (
    hasAny(message, [
      /confirmed address verification/,
      /address verification audit/,
      /address confirmation audit/,
      /地址核对记录/,
      /地址确认/,
    ])
  ) {
    return "checkout.errors.addressConfirmationRequired";
  }
  if (hasAny(message, [/address/, /delivery/, /shipping/, /service area/])) {
    return "checkout.errors.addressUnavailable";
  }
  if (hasAny(message, [/pay order/, /payment channel/, /payment method/, /cashier/, /pay module/])) {
    return "checkout.errors.paymentUnavailable";
  }
  if (hasAny(message, [/price/, /amount/, /settle/, /settlement/, /pay price/])) {
    return "checkout.errors.priceChanged";
  }

  return fallbackKey;
};
