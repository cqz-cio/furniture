import { getYudaoAppApiBase } from "./yudaoRequest.js";

const CONSENT_KEY = "oakved_analytics_consent";
const VISITOR_KEY = "oakved_analytics_visitor";
const SESSION_KEY = "oakved_session_id";
const SESSION_ACTIVE_KEY = "oakved_analytics_last_active";
const SESSION_IDLE_MS = 30 * 60 * 1000;
const EVENT_TYPES = { HOME_VIEW: 1, PRODUCT_DETAIL_VIEW: 2, CHECKOUT_START: 4 };

const truthy = (value) => String(value || "false").toLowerCase() === "true";

export const isBehaviorTrackingEnabled = () => truthy(import.meta.env.VITE_BEHAVIOR_TRACKING_ENABLED);
export const isAnalyticsConsentRequired = () =>
  String(import.meta.env.VITE_ANALYTICS_CONSENT_REQUIRED ?? "true").toLowerCase() !== "false";

const visitorTtlMs = () => {
  const configured = Number(import.meta.env.VITE_ANALYTICS_VISITOR_TTL_DAYS || 180);
  const days = Number.isFinite(configured) && configured > 0 ? Math.min(configured, 180) : 180;
  return days * 24 * 60 * 60 * 1000;
};

const readConsent = () => {
  if (!isAnalyticsConsentRequired()) return { granted: true, evidence: "" };
  try {
    const value = JSON.parse(localStorage.getItem(CONSENT_KEY) || "null");
    return value?.granted === true && typeof value.evidence === "string" && value.evidence.trim()
      ? value
      : null;
  } catch {
    return null;
  }
};

const clearIdentifiers = () => {
  localStorage.removeItem(VISITOR_KEY);
  sessionStorage.removeItem(SESSION_KEY);
  sessionStorage.removeItem(SESSION_ACTIVE_KEY);
};

export const setAnalyticsConsent = ({ granted, evidence = "" }) => {
  if (granted) {
    localStorage.setItem(CONSENT_KEY, JSON.stringify({ granted: true, evidence: String(evidence) }));
  } else {
    localStorage.removeItem(CONSENT_KEY);
    clearIdentifiers();
  }
};

const isAllowed = () => isBehaviorTrackingEnabled() && Boolean(readConsent());

export const getVisitorId = (now = Date.now()) => {
  let visitor;
  try { visitor = JSON.parse(localStorage.getItem(VISITOR_KEY) || "null"); } catch { visitor = null; }
  if (!visitor?.id || !Number.isFinite(Number(visitor.createdAt)) || now - Number(visitor.createdAt) >= visitorTtlMs()) {
    visitor = { id: crypto.randomUUID(), createdAt: now };
    localStorage.setItem(VISITOR_KEY, JSON.stringify(visitor));
  }
  return visitor.id;
};

export const getSessionId = (now = Date.now()) => {
  const lastActive = Number(sessionStorage.getItem(SESSION_ACTIVE_KEY) || 0);
  if (!sessionStorage.getItem(SESSION_KEY) || now - lastActive >= SESSION_IDLE_MS) {
    sessionStorage.setItem(SESSION_KEY, crypto.randomUUID());
  }
  sessionStorage.setItem(SESSION_ACTIVE_KEY, String(now));
  return sessionStorage.getItem(SESSION_KEY);
};

export const analyticsIdentityHeaders = (now = Date.now()) => {
  if (!isAllowed()) return {};
  const consent = readConsent();
  return {
    "x-analytics-visitor-id": getVisitorId(now),
    "x-analytics-session-id": getSessionId(now),
    ...(consent?.evidence ? { "x-analytics-consent-evidence": consent.evidence } : {}),
  };
};

const safeReferrerHost = () => {
  try { return document.referrer ? new URL(document.referrer).host : undefined; } catch { return undefined; }
};

const sendOnce = async (body, headers) => {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 2000);
  try {
    const response = await fetch(`${getYudaoAppApiBase()}/statistics/behavior/track`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...headers },
      body,
      keepalive: true,
      signal: controller.signal,
    });
    if (!response.ok) throw new Error(`analytics HTTP ${response.status || "error"}`);
  } finally {
    clearTimeout(timeout);
  }
};

const track = async (eventType, { spuId } = {}) => {
  if (!isAllowed()) return false;
  const headers = analyticsIdentityHeaders();
  const payload = {
    eventId: crypto.randomUUID(),
    eventType: EVENT_TYPES[eventType],
    ...(spuId ? { spuId: Number(spuId) } : {}),
    pagePath: eventType === "HOME_VIEW" ? "/" : eventType === "PRODUCT_DETAIL_VIEW" ? "/product" : "/checkout",
    ...(safeReferrerHost() ? { referrerHost: safeReferrerHost() } : {}),
  };
  const body = JSON.stringify(payload);
  for (let attempt = 0; attempt < 2; attempt += 1) {
    try { await sendOnce(body, headers); return true; } catch { /* one bounded retry */ }
  }
  return false;
};

export const trackHomeView = () => track("HOME_VIEW");
export const trackProductDetailView = (spuId) => track("PRODUCT_DETAIL_VIEW", { spuId });
export const trackCheckoutStart = () => track("CHECKOUT_START");
