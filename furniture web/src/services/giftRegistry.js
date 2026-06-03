export const REGISTRY_VISIBILITY = {
  public: "public",
  searchableByEmail: "searchable_by_email",
  inviteOnly: "invite_only",
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
  id: overrides.id || "",
  event: {
    type: "Wedding",
    date: "",
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
  });

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
  const ready = hasText(draft.id) && hasText(draft.event.date) && hasText(draft.registrants.primaryName);

  return {
    ready,
    publicUrl: ready ? `/gift-registry/${draft.id}` : "",
    purchasedAutoMarking: ready,
  };
};
