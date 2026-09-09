import { requestYudao, getYudaoAppTenantId } from "./yudaoRequest.js";
import { getWebsiteNavigationTenantId } from "./yudaoNavigationApi.js";
import { createWebsiteCodeRuntime } from "./websiteCodeRuntime.js";

export function readWebsiteCodeConsent(win = window) {
  if (win.Cookiebot?.consent) return {
    analytics: win.Cookiebot.consent.statistics === true,
    marketing: win.Cookiebot.consent.marketing === true,
    preferences: win.Cookiebot.consent.preferences === true,
  };
  try {
    const stored = JSON.parse(win.localStorage.getItem("oakved_analytics_consent") || "null");
    return {
      analytics: stored?.granted === true && Boolean(stored?.evidence),
      marketing: stored?.marketing === true && Boolean(stored?.evidence),
    };
  } catch { return {}; }
}

let runtime;
export function startWebsiteCode() {
  if (runtime) return runtime;
  runtime = createWebsiteCodeRuntime({
    loadPublished: async () => {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 5000);
      try {
        return await requestYudao("/seo/website-code/public?siteId=1", {
          method: "GET", token: "", skipAuthRetry: true, cache: "no-store",
          tenantId: getWebsiteNavigationTenantId() || getYudaoAppTenantId(), signal: controller.signal,
        });
      } finally { clearTimeout(timer); }
    },
  });
  const sync = () => runtime.updateConsent(readWebsiteCodeConsent());
  for (const event of ["storage", "oakved:analytics-consent-changed", "CookiebotOnConsentReady", "CookiebotOnAccept", "CookiebotOnDecline"]) {
    window.addEventListener(event, sync);
  }
  // A configured CMP may deliver separate categories through this same-page event.
  window.addEventListener("cms:consent", (event) => runtime.updateConsent(event.detail || {}));
  sync();
  return runtime;
}
