import { requestYudao } from "./yudaoRequest.js";

const PREVIEW_TICKET_PATTERN = /^pv_[A-Za-z0-9_-]{43}$/;
const PREVIEW_SESSION_PATTERN = /^ps_[A-Za-z0-9_-]{43}$/;
const TENANT_ID_PATTERN = /^[1-9]\d*$/;
const PREVIEW_SESSION_HEADER = "X-Website-Preview-Session";
const DEFAULT_SITE_ID = 1;
const DEFAULT_LOCALE = "en";

const readNavigationRuntimeConfig = () => {
  const runtimeConfig = globalThis.__OAKVED_SITE_CONFIG__;
  return runtimeConfig && typeof runtimeConfig === "object" ? runtimeConfig : {};
};

export const getWebsiteNavigationTenantId = () => {
  const runtimeTenantId = readNavigationRuntimeConfig().tenantId;
  const metaTenantId =
    typeof document === "undefined"
      ? ""
      : document.querySelector('meta[name="yudao-tenant-id"]')?.getAttribute("content");
  return String(
    runtimeTenantId ||
      metaTenantId ||
      import.meta.env.VITE_YUDAO_APP_TENANT_ID ||
      import.meta.env.VITE_YUDAO_TENANT_ID ||
      "",
  ).trim();
};

export const getWebsiteNavigationPublished = ({
  siteId = DEFAULT_SITE_ID,
  locale = DEFAULT_LOCALE,
  tenantId = getWebsiteNavigationTenantId(),
} = {}) =>
  requestYudao(
    `/seo/navigation/public?siteId=${encodeURIComponent(siteId)}&locale=${encodeURIComponent(locale)}`,
    {
      method: "GET",
      tenantId,
      token: "",
      skipAuthRetry: true,
    },
  );

export const loadWebsiteNavigationPublished = async (options = {}) => {
  const navigation = await getWebsiteNavigationPublished(options);
  return navigation?.items?.length ? navigation : null;
};

export const parseWebsiteNavigationPreviewHash = (hash) => {
  const params = new URLSearchParams(String(hash || "").replace(/^#/, ""));
  const ticket = String(params.get("ticket") || "").trim();
  const tenantId = String(params.get("tenantId") || "").trim();
  if (!PREVIEW_TICKET_PATTERN.test(ticket) || !TENANT_ID_PATTERN.test(tenantId)) {
    throw new Error("Website navigation preview link is invalid or incomplete.");
  }
  return { ticket, tenantId };
};

export const exchangeWebsiteNavigationPreviewTicket = (ticket, tenantId) =>
  requestYudao("/seo/navigation/preview/exchange", {
    method: "POST",
    body: JSON.stringify({ ticket }),
    tenantId,
    token: "",
    skipAuthRetry: true,
  });

export const getWebsiteNavigationPreview = (session, tenantId) => {
  if (!PREVIEW_SESSION_PATTERN.test(String(session || ""))) {
    throw new Error("Website navigation preview session is invalid.");
  }
  return requestYudao("/seo/navigation/preview", {
    method: "GET",
    headers: { [PREVIEW_SESSION_HEADER]: session },
    tenantId,
    token: "",
    skipAuthRetry: true,
  });
};

export const loadWebsiteNavigationPreview = async ({
  hash = window.location.hash,
  location = window.location,
  history = window.history,
} = {}) => {
  const { ticket, tenantId } = parseWebsiteNavigationPreviewHash(hash);
  const grant = await exchangeWebsiteNavigationPreviewTicket(ticket, tenantId);
  const navigation = await getWebsiteNavigationPreview(grant?.session, tenantId);
  history.replaceState(history.state, "", `${location.pathname}${location.search}`);
  return { navigation, tenantId };
};
