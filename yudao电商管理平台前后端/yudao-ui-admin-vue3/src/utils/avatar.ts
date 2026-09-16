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
      !isLoopback ||
      !/^https?:$/.test(url.protocol) ||
      !/^\/admin-api\/infra\/file\/\d+\/get\//.test(url.pathname)
    )
      return value
    // Use this environment's public API origin, never the visitor's loopback host.
    const api = new URL(apiBaseUrl, globalThis.location?.origin)
    return `${api.origin}${url.pathname}${url.search}${url.hash}`
  } catch {
    return value
  }
}
