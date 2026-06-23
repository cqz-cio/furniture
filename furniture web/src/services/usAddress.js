import { US_POSTAL_REGIONS } from "../data/usPostalRegions.js";

const configuredYudaoUsDefaultAreaId = Number(import.meta.env.VITE_YUDAO_US_DEFAULT_AREA_ID || 1);

export const YUDAO_US_DEFAULT_AREA_ID =
  Number.isFinite(configuredYudaoUsDefaultAreaId) && configuredYudaoUsDefaultAreaId > 0 ? configuredYudaoUsDefaultAreaId : 1;
export const LOCAL_ADDRESS_VERIFICATION_SOURCE = "local-postal-region";

export const US_STATE_OPTIONS = [
  { code: "AL", name: "Alabama" },
  { code: "AK", name: "Alaska" },
  { code: "AZ", name: "Arizona" },
  { code: "AR", name: "Arkansas" },
  { code: "CA", name: "California" },
  { code: "CO", name: "Colorado" },
  { code: "CT", name: "Connecticut" },
  { code: "DE", name: "Delaware" },
  { code: "DC", name: "District of Columbia" },
  { code: "FL", name: "Florida" },
  { code: "GA", name: "Georgia" },
  { code: "HI", name: "Hawaii" },
  { code: "ID", name: "Idaho" },
  { code: "IL", name: "Illinois" },
  { code: "IN", name: "Indiana" },
  { code: "IA", name: "Iowa" },
  { code: "KS", name: "Kansas" },
  { code: "KY", name: "Kentucky" },
  { code: "LA", name: "Louisiana" },
  { code: "ME", name: "Maine" },
  { code: "MD", name: "Maryland" },
  { code: "MA", name: "Massachusetts" },
  { code: "MI", name: "Michigan" },
  { code: "MN", name: "Minnesota" },
  { code: "MS", name: "Mississippi" },
  { code: "MO", name: "Missouri" },
  { code: "MT", name: "Montana" },
  { code: "NE", name: "Nebraska" },
  { code: "NV", name: "Nevada" },
  { code: "NH", name: "New Hampshire" },
  { code: "NJ", name: "New Jersey" },
  { code: "NM", name: "New Mexico" },
  { code: "NY", name: "New York" },
  { code: "NC", name: "North Carolina" },
  { code: "ND", name: "North Dakota" },
  { code: "OH", name: "Ohio" },
  { code: "OK", name: "Oklahoma" },
  { code: "OR", name: "Oregon" },
  { code: "PA", name: "Pennsylvania" },
  { code: "RI", name: "Rhode Island" },
  { code: "SC", name: "South Carolina" },
  { code: "SD", name: "South Dakota" },
  { code: "TN", name: "Tennessee" },
  { code: "TX", name: "Texas" },
  { code: "UT", name: "Utah" },
  { code: "VT", name: "Vermont" },
  { code: "VA", name: "Virginia" },
  { code: "WA", name: "Washington" },
  { code: "WV", name: "West Virginia" },
  { code: "WI", name: "Wisconsin" },
  { code: "WY", name: "Wyoming" },
];

const STREET_SUFFIXES = [
  [/\bSTREET\b/g, "ST"],
  [/\bAVENUE\b/g, "AVE"],
  [/\bROAD\b/g, "RD"],
  [/\bPARKWAY\b/g, "PKWY"],
  [/\bDRIVE\b/g, "DR"],
  [/\bLANE\b/g, "LN"],
  [/\bBOULEVARD\b/g, "BLVD"],
];

const clean = (value) => String(value || "").trim();
const normalizeState = (value) => clean(value).toUpperCase();
const normalizeZip = (value) => clean(value).match(/\d{5}/)?.[0] || "";
const titleizeCity = (value) =>
  clean(value)
    .toLowerCase()
    .replace(/\b[a-z]/g, (letter) => letter.toUpperCase());

const firstValue = (row, keys) => keys.map((key) => row?.[key]).find((value) => clean(value));

const normalizeStreet = (value) => {
  let street = clean(value).replace(/\s+/g, " ").toUpperCase();
  STREET_SUFFIXES.forEach(([pattern, replacement]) => {
    street = street.replace(pattern, replacement);
  });
  return street;
};

const normalizeAddress = (address = {}) => ({
  firstName: clean(address.firstName),
  lastName: clean(address.lastName),
  street: normalizeStreet(address.street),
  apartment: clean(address.apartment),
  city: clean(address.city),
  state: normalizeState(address.state),
  postalCode: normalizeZip(address.postalCode),
  phone: clean(address.phone),
  country: clean(address.country) || "United States",
  areaId: Number(address.areaId || YUDAO_US_DEFAULT_AREA_ID),
});

const hasRequiredAddressFields = (address) =>
  Boolean(address.firstName && address.lastName && address.street && address.city && address.state && address.postalCode && address.phone);

export const getUsStateOptions = () => US_STATE_OPTIONS.map((state) => ({ ...state }));

export const createUsPostalRegionIndex = (rows = []) =>
  rows.reduce((index, row) => {
    const postalCode = normalizeZip(firstValue(row, ["postalCode", "postal_code", "zip", "ZIP", "Zip"]));
    const city = titleizeCity(firstValue(row, ["city", "primary_city", "placeName", "place_name"]));
    const state = normalizeState(firstValue(row, ["state", "state_id", "stateCode", "state_code"]));

    if (postalCode && city && state && !index[postalCode]) {
      index[postalCode] = { city, state, postalCode };
    }

    return index;
  }, {});

const US_POSTAL_REGION_INDEX = createUsPostalRegionIndex(US_POSTAL_REGIONS);

export const resolveUsPostalRegion = (postalCode, index = US_POSTAL_REGION_INDEX) => {
  const zip = normalizeZip(postalCode);
  return index[zip] ? { ...index[zip] } : null;
};

export const verifyUsCheckoutAddress = (inputAddress = {}) => {
  const originalAddress = normalizeAddress(inputAddress);
  const postalRegion = resolveUsPostalRegion(originalAddress.postalCode);

  if (!hasRequiredAddressFields(originalAddress) || !postalRegion) {
    return {
      status: "unverified",
      reason: !hasRequiredAddressFields(originalAddress) ? "missing-required-fields" : "unknown-postal-code",
      requiresConfirmation: true,
      originalAddress,
      suggestedAddress: null,
    };
  }

  const suggestedAddress = {
    ...originalAddress,
    city: postalRegion.city,
    state: postalRegion.state,
    postalCode: postalRegion.postalCode,
  };

  const matchesPostalRegion =
    originalAddress.city.toLowerCase() === postalRegion.city.toLowerCase() && originalAddress.state === postalRegion.state;

  if (!matchesPostalRegion) {
    return {
      status: "suggested",
      reason: "postal-region-mismatch",
      requiresConfirmation: true,
      originalAddress,
      suggestedAddress,
    };
  }

  return {
    status: "verified",
    reason: "",
    requiresConfirmation: false,
    originalAddress,
    suggestedAddress,
  };
};

export const verifyUsCheckoutAddressWithProvider = async (inputAddress = {}, provider = null) => {
  const localVerification = {
    ...verifyUsCheckoutAddress(inputAddress),
    source: LOCAL_ADDRESS_VERIFICATION_SOURCE,
  };
  const verifyAddress = provider?.verifyAddress;

  if (typeof verifyAddress !== "function") {
    return localVerification;
  }

  try {
    const providerVerification = await verifyAddress(inputAddress, localVerification);
    if (!providerVerification) return localVerification;

    return {
      ...localVerification,
      ...providerVerification,
      source: providerVerification.source || provider.name || "external-address-verifier",
      originalAddress: providerVerification.originalAddress || localVerification.originalAddress,
      suggestedAddress:
        providerVerification.suggestedAddress === undefined
          ? localVerification.suggestedAddress
          : providerVerification.suggestedAddress,
      requiresConfirmation:
        providerVerification.requiresConfirmation === undefined
          ? localVerification.requiresConfirmation
          : providerVerification.requiresConfirmation,
    };
  } catch (_caught) {
    return {
      ...localVerification,
      providerStatus: "fallback",
    };
  }
};

export const buildAddressConfirmationRecord = (verification, choice = "original", options = {}) => {
  const safeVerification = verification || verifyUsCheckoutAddress({});
  const selectedAddress =
    choice === "suggested" && safeVerification.suggestedAddress
      ? safeVerification.suggestedAddress
      : safeVerification.originalAddress;

  return {
    source: safeVerification.source || LOCAL_ADDRESS_VERIFICATION_SOURCE,
    addressSource: options.addressSource || "new",
    status: safeVerification.status,
    reason: safeVerification.reason,
    choice,
    deliverable: safeVerification.deliverable,
    providerResponseId: safeVerification.providerResponseId || safeVerification.metadata?.responseId || "",
    providerStatus: safeVerification.providerStatus || "",
    metadata: safeVerification.metadata,
    originalAddress: safeVerification.originalAddress,
    suggestedAddress: safeVerification.suggestedAddress,
    selectedAddress,
    confirmedAt: new Date().toISOString(),
  };
};
