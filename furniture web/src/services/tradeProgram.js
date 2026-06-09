export const tradeRoutes = {
  signIn: "/trade/sign-in",
  apply: "/trade/apply",
  faq: "/trade/faq",
};

export const businessInfoFields = [
  ["businessName", "text", true],
  ["country", "select", true],
  ["street", "text", true],
  ["address2", "text", false],
  ["city", "text", true],
  ["state", "select", true],
  ["postalCode", "text", true],
  ["businessDescription", "select", true],
  ["website", "url", false],
  ["portfolio", "url", false],
];

export const socialFields = ["instagram", "pinterest", "houzz", "linkedin"];

export const businessDescriptionOptions = ["designer", "architect", "builder", "stager", "hospitality", "other"];

export const countryOptions = ["United States", "Canada", "France", "China"];

export const stateOptions = ["CA", "NY", "FL", "TX", "IL", "WA", "ON", "QC", "Other"];

export const faqItems = [
  "program",
  "qualify",
  "singleContact",
  "quote",
  "onlineOrder",
  "samples",
  "loan",
  "authorizedUser",
  "outlet",
  "international",
  "payment",
  "creditCard",
  "salesTax",
  "discount",
  "returnPolicy",
  "orderTiming",
  "tracking",
  "shippingRequests",
  "expedite",
  "cancel",
];
