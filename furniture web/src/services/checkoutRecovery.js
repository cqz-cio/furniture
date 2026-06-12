export const getCheckoutRecoveryAction = (errorKey, routes = {}) => {
  if (errorKey === "checkout.errors.noAddress" || errorKey === "checkout.errors.addressUnavailable") {
    return {
      type: "link",
      labelKey: "checkout.actions.manageAddresses",
      href: routes.addressBook,
    };
  }

  if (errorKey === "checkout.errors.stockUnavailable") {
    return {
      type: "emit",
      labelKey: "checkout.actions.reviewBag",
      event: "open-cart",
    };
  }

  if (errorKey === "checkout.errors.sessionExpired") {
    return {
      type: "link",
      labelKey: "checkout.actions.signIn",
      href: routes.checkoutAuth,
    };
  }

  if (errorKey === "checkout.errors.priceChanged") {
    return {
      type: "retry",
      labelKey: "checkout.actions.refreshSettlement",
    };
  }

  return null;
};
