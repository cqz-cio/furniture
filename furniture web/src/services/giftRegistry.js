export const REGISTRY_VISIBILITY = {
  public: "public",
  searchableByEmail: "searchable_by_email",
  inviteOnly: "invite_only",
};

export const REGISTRY_STATUS = {
  draft: "draft",
  active: "active",
  hidden: "hidden",
  closed: "closed",
};

export const normalizeRegistryAddress = (address = {}) => ({
  label: address.label || "",
  kind: address.kind || "local",
  line1: String(address.line1 || "").trim(),
  line2: String(address.line2 || "").trim(),
  city: String(address.city || "").trim(),
  region: String(address.region || "").trim(),
  postalCode: String(address.postalCode || "").trim(),
  country: String(address.country || "United States").trim(),
});

export const createGiftRegistryDraft = (overrides = {}) => ({
  id: overrides.id ?? "",
  publicCode: overrides.publicCode || overrides.id || "",
  status: overrides.status || REGISTRY_STATUS.draft,
  event: {
    type: "Wedding",
    date: "",
    location: "",
    ...(overrides.event || {}),
  },
  registrants: {
    primaryName: "",
    coRegistrantName: "",
    email: "",
    phone: "",
    ...(overrides.registrants || {}),
  },
  addresses: {
    beforeEvent: normalizeRegistryAddress({
      label: "Before Event",
      kind: "local",
      ...(overrides.addresses?.beforeEvent || {}),
    }),
    afterEvent: normalizeRegistryAddress({
      label: "After Event",
      kind: "local",
      ...(overrides.addresses?.afterEvent || {}),
    }),
  },
  privacy: {
    visibility: REGISTRY_VISIBILITY.public,
    emailSubscription: true,
    giftCardPreference: true,
    ...(overrides.privacy || {}),
  },
  items: Array.isArray(overrides.items) ? overrides.items.map(normalizeGiftRegistryItem) : [],
});

export const updateGiftRegistryDraft = (draft, patch = {}) =>
  createGiftRegistryDraft({
    ...draft,
    ...patch,
    event: { ...(draft.event || {}), ...(patch.event || {}) },
    registrants: { ...(draft.registrants || {}), ...(patch.registrants || {}) },
    addresses: {
      beforeEvent: { ...(draft.addresses?.beforeEvent || {}), ...(patch.addresses?.beforeEvent || {}) },
      afterEvent: { ...(draft.addresses?.afterEvent || {}), ...(patch.addresses?.afterEvent || {}) },
    },
    privacy: { ...(draft.privacy || {}), ...(patch.privacy || {}) },
    items: patch.items || draft.items || [],
  });

export const normalizeGiftRegistryItem = (item = {}) => ({
  id: item.id ?? "",
  registryId: item.registryId ?? "",
  spuId: item.spuId ?? "",
  skuId: item.skuId ?? "",
  productName: item.productName || item.name || "",
  picUrl: item.picUrl || item.image || "",
  price: Number(item.price || 0),
  quantityRequested: Number(item.quantityRequested || item.count || 1),
  quantityPurchased: Number(item.quantityPurchased || 0),
  priority: item.priority || "normal",
  note: item.note || "",
});

export const registryProductToItemPayload = (product = {}, options = {}) => ({
  registryId: options.registryId ?? "",
  spuId: product.spuId || product.id || "",
  skuId: product.skuId || product.raw?.skuId || product.raw?.skus?.[0]?.id || "",
  productName: product.productName || product.name || "",
  picUrl: product.picUrl || product.cover || product.raw?.picUrl || "",
  price: Number(product.price || 0),
  quantityRequested: Number(options.quantityRequested || 1),
  priority: options.priority || "normal",
  note: options.note || "",
});

export const registryItemToCartProduct = (item = {}) => {
  const normalized = normalizeGiftRegistryItem(item);
  return {
    id: normalized.spuId,
    spuId: normalized.spuId,
    skuId: normalized.skuId,
    name: normalized.productName,
    cover: normalized.picUrl,
    price: normalized.price,
    quantity: 1,
    source: "yudao",
    registryContext: {
      registryId: normalized.registryId,
      registryItemId: normalized.id,
    },
  };
};

const hasText = (value) => Boolean(String(value || "").trim());

export const getGiftRegistrySteps = (draft = createGiftRegistryDraft()) => [
  {
    key: "event",
    title: "Event Details",
    complete: hasText(draft.event.type) && hasText(draft.event.date),
  },
  {
    key: "registrants",
    title: "Registrant Information",
    complete: hasText(draft.registrants.primaryName) && hasText(draft.registrants.email),
  },
  {
    key: "addresses",
    title: "Gift Delivery Addresses",
    complete: hasText(draft.addresses.beforeEvent.line1) && hasText(draft.addresses.afterEvent.line1),
  },
  {
    key: "privacy",
    title: "Privacy & Subscription",
    complete: Object.values(REGISTRY_VISIBILITY).includes(draft.privacy.visibility),
  },
  {
    key: "share",
    title: "Share Registry",
    complete: getRegistryShareState(draft).ready,
  },
];

export const getRegistryShareState = (draft = createGiftRegistryDraft()) => {
  const publicCode = draft.publicCode || draft.id;
  const ready = hasText(publicCode) && hasText(draft.event.date) && hasText(draft.registrants.primaryName);

  return {
    ready,
    publicUrl: ready ? `/gift-registry/${publicCode}` : "",
    purchasedAutoMarking: false,
  };
};

export const canUseGiftRegistryDemoFallback = (env = import.meta.env) => !env?.PROD;
