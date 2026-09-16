// Old uploads overwrote avatar.png despite its seven-day cache lifetime.
// Refresh those legacy files once per page load; uniquely named uploads stay cacheable.
const legacyAvatarVersion = Date.now().toString(36)

/** Repair legacy local-storage file URLs without changing external image hosts. */
export function resolveAvatarUrl(
  value: string | undefined,
  apiBaseUrl = import.meta.env.VITE_BASE_URL || window.location.origin
): string {
  if (!value) return ''
  try {
    const url = new URL(value)
    const isLoopback =
      url.hostname === 'localhost' ||
      url.hostname === '[::1]' ||
      /^127\.\d+\.\d+\.\d+$/.test(url.hostname)
    if (
      !/^https?:$/.test(url.protocol) ||
      !/^\/admin-api\/infra\/file\/\d+\/get\//.test(url.pathname)
    )
      return value
    // Use this environment's public API origin, never the visitor's loopback host.
    const api = new URL(apiBaseUrl, globalThis.location?.origin)
    if (!isLoopback && url.origin !== api.origin) return value
    if (url.pathname.endsWith('/avatar.png')) {
      url.searchParams.set('avatarVersion', legacyAvatarVersion)
    }
    return `${isLoopback ? api.origin : url.origin}${url.pathname}${url.search}${url.hash}`
  } catch {
    return value
  }
}
