import {
  REGISTRY_STATUS,
  createGiftRegistryDraft,
  normalizeRegistryAddress,
  normalizeGiftRegistryItem,
} from "./giftRegistry.js";
import { requestYudao } from "./yudaoRequest.js";

const compact = (object = {}) =>
  Object.fromEntries(Object.entries(object).filter(([, value]) => value !== undefined && value !== null && value !== ""));

const toBackendRegistryPayload = (registry = {}) => {
  const before = registry.addresses?.beforeEvent || {};
  const after = registry.addresses?.afterEvent || {};
  return compact({
    id: registry.id,
    eventType: registry.event?.type,
    eventDate: registry.event?.date,
    eventLocation: registry.event?.location,
    registrantName: registry.registrants?.primaryName,
    coRegistrantName: registry.registrants?.coRegistrantName,
    email: registry.registrants?.email,
    phone: registry.registrants?.phone,
    visibility: registry.privacy?.visibility,
    status: registry.status,
    giftCardPreference: registry.privacy?.giftCardPreference,
    messagePreference: registry.privacy?.emailSubscription,
    beforeEventAddressLine1: before.line1,
    beforeEventAddressLine2: before.line2,
    beforeEventCity: before.city,
    beforeEventRegion: before.region,
    beforeEventPostalCode: before.postalCode,
    beforeEventCountry: before.country,
    afterEventAddressLine1: after.line1,
    afterEventAddressLine2: after.line2,
    afterEventCity: after.city,
    afterEventRegion: after.region,
    afterEventPostalCode: after.postalCode,
    afterEventCountry: after.country,
  });
};

const toFrontendRegistry = (data = {}) =>
  createGiftRegistryDraft({
    id: data.id ?? "",
    publicCode: data.publicCode || "",
    status: data.status || REGISTRY_STATUS.draft,
    event: {
      type: data.eventType || "Wedding",
      date: data.eventDate || "",
      location: data.eventLocation || "",
    },
    registrants: {
      primaryName: data.registrantName || "",
      coRegistrantName: data.coRegistrantName || "",
      email: data.email || "",
      phone: data.phone || "",
    },
    addresses: {
      beforeEvent: normalizeRegistryAddress({
        label: "Before Event",
        line1: data.beforeEventAddressLine1,
        line2: data.beforeEventAddressLine2,
        city: data.beforeEventCity,
        region: data.beforeEventRegion,
        postalCode: data.beforeEventPostalCode,
        country: data.beforeEventCountry,
      }),
      afterEvent: normalizeRegistryAddress({
        label: "After Event",
        kind: "custom",
        line1: data.afterEventAddressLine1,
        line2: data.afterEventAddressLine2,
        city: data.afterEventCity,
        region: data.afterEventRegion,
        postalCode: data.afterEventPostalCode,
        country: data.afterEventCountry,
      }),
    },
    privacy: {
      visibility: data.visibility,
      emailSubscription: data.messagePreference !== false,
      giftCardPreference: data.giftCardPreference === true,
    },
    items: Array.isArray(data.items) ? data.items.map(normalizeGiftRegistryItem) : [],
  });

const toPage = (data = {}) => ({
  list: Array.isArray(data.list) ? data.list.map(toFrontendRegistry) : [],
  total: Number(data.total || 0),
});

export const createYudaoGiftRegistry = async (registry, options = {}) => {
  const data = await requestYudao("/member/gift-registry/create", {
    ...options,
    method: "POST",
    body: JSON.stringify(toBackendRegistryPayload(registry)),
  });
  return toFrontendRegistry(data);
};

export const getMyYudaoGiftRegistry = async (options = {}) => {
  const data = await requestYudao("/member/gift-registry/my", options);
  return data ? toFrontendRegistry(data) : null;
};

export const updateYudaoGiftRegistry = async (registry, options = {}) => {
  const data = await requestYudao("/member/gift-registry/update", {
    ...options,
    method: "PUT",
    body: JSON.stringify(toBackendRegistryPayload(registry)),
  });
  return toFrontendRegistry(data);
};

export const searchPublicYudaoGiftRegistries = async (params = {}, options = {}) => {
  const query = new URLSearchParams();
  if (params.keyword) query.set("keyword", params.keyword);
  if (params.eventMonth) query.set("eventMonth", params.eventMonth);
  query.set("pageNo", String(params.pageNo || 1));
  query.set("pageSize", String(params.pageSize || 10));
  const data = await requestYudao(`/member/gift-registry/search?${query.toString()}`, options);
  return toPage(data);
};

export const getPublicYudaoGiftRegistry = async (publicCode, options = {}) => {
  const data = await requestYudao(`/member/gift-registry/public/${encodeURIComponent(publicCode)}`, options);
  return toFrontendRegistry(data);
};

export const addYudaoGiftRegistryItem = async (item, options = {}) => {
  const data = await requestYudao("/member/gift-registry/item/add", {
    ...options,
    method: "POST",
    body: JSON.stringify(compact(normalizeGiftRegistryItem(item))),
  });
  return normalizeGiftRegistryItem(data);
};

export const updateYudaoGiftRegistryItem = async (item, options = {}) => {
  const data = await requestYudao("/member/gift-registry/item/update", {
    ...options,
    method: "PUT",
    body: JSON.stringify(compact(normalizeGiftRegistryItem(item))),
  });
  return normalizeGiftRegistryItem(data);
};

export const deleteYudaoGiftRegistryItem = async (id, options = {}) =>
  requestYudao(`/member/gift-registry/item/delete?id=${encodeURIComponent(id)}`, {
    ...options,
    method: "DELETE",
  });
