export type FurnitureLiteRoute = {
  path: string
  children?: FurnitureLiteRoute[]
  [key: string]: unknown
}

const allowedMenuPaths = new Set([
  '/index',
  '/mall/home',
  '/mall/product',
  '/mall/product/spu',
  '/mall/product/category',
  '/mall/product/brand',
  '/mall/product/property',
  '/mall/product/comment',
  '/mall/statistics',
  '/mall/statistics/product',
  '/mall/trade',
  '/mall/trade/order',
  '/mall/trade/after-sale',
  '/mall/trade/delivery',
  '/mall/trade/delivery/express',
  '/mall/trade/delivery/express/express-template',
  '/mall/trade/delivery/express-template',
  '/mall/trade/delivery/pick-up-store',
  '/member',
  '/member/user',
  '/member/level',
  '/member/tag',
  '/member/group',
  '/pay',
  '/pay/app',
  '/pay/order',
  '/pay/refund',
  '/ai',
  '/ai/console',
  '/ai/console/chat',
  '/ai/console/image',
  '/ai/console/knowledge',
  '/ai/console/mind-map',
  '/ai/console/model',
  '/ai/console/music',
  '/ai/console/workflow',
  '/ai/console/write',
  '/ai/chat',
  '/ai/chat/index',
  '/ai/chat/manager',
  '/ai/model',
  '/ai/model/model',
  '/ai/model/api-key',
  '/ai/model/apiKey',
  '/ai/model/chat-role',
  '/ai/model/chatRole',
  '/ai/model/tool',
  '/ai/knowledge',
  '/ai/knowledge/knowledge',
  '/ai/knowledge/document',
  '/ai/knowledge/segment',
  '/ai/workflow',
  '/ai/write',
  '/ai/write/index',
  '/ai/write/manager',
  '/ai/image',
  '/ai/image/index',
  '/ai/image/manager',
  '/ai/image/square',
  '/ai/music',
  '/ai/music/index',
  '/ai/music/manager',
  '/ai/mind-map',
  '/ai/mind-map/index',
  '/ai/mind-map/manager',
  '/ai/mindmap',
  '/ai/mindmap/index',
  '/ai/mindmap/manager',
  '/infra/file',
  '/infra/file-config',
  '/infra/file/file-config',
  '/system/user',
  '/system/role',
  '/system/menu'
])

const deniedFixedRoutePrefixes = [
  '/bpm',
  '/crm',
  '/iot',
  '/mes',
  '/diy',
  '/codegen',
  '/job'
]

const isFalseEnvValue = (value: unknown): boolean =>
  typeof value === 'string' && value.toLowerCase() === 'false'

const normalizeRoutePath = (path: string, parentPath = ''): string => {
  const rawPath = path || ''
  const joinedPath = parentPath
    ? `${parentPath.replace(/\/+$/, '')}/${rawPath.replace(/^\/+/, '')}`
    : `/${rawPath.replace(/^\/+/, '')}`
  const normalizedPath = joinedPath.replace(/\/+/g, '/')

  if (!normalizedPath || normalizedPath === '/') {
    return '/'
  }

  return normalizedPath.replace(/\/$/, '')
}

const isPathAllowed = (path: string): boolean => allowedMenuPaths.has(path)

const isPathDenied = (path: string): boolean =>
  deniedFixedRoutePrefixes.some((prefix) => path === prefix || path.startsWith(`${prefix}/`))

export const isFurnitureLiteMode = (): boolean =>
  import.meta.env.VITE_ADMIN_MODE === 'furniture-lite'

export const isDocAlertVisible = (): boolean =>
  !isFalseEnvValue(import.meta.env.VITE_SHOW_DOC_ALERT) &&
  !isFalseEnvValue(import.meta.env.VITE_APP_DOCALERT_ENABLE)

export const isDevLinksVisible = (): boolean =>
  !isFalseEnvValue(import.meta.env.VITE_SHOW_DEV_LINKS)

export const filterFurnitureLiteMenus = <T extends FurnitureLiteRoute>(routes: T[]): T[] => {
  if (!isFurnitureLiteMode()) {
    return routes
  }

  const filterRoutes = (items: T[], parentPath = ''): T[] =>
    items.reduce<T[]>((filteredRoutes, route) => {
      const fullPath = normalizeRoutePath(route.path, parentPath)
      const children = route.children ? filterRoutes(route.children as T[], fullPath) : undefined

      if (!isPathAllowed(fullPath) && (!children || children.length === 0)) {
        return filteredRoutes
      }

      const nextRoute = { ...route }
      if (route.children) {
        nextRoute.children = children
      }
      filteredRoutes.push(nextRoute)
      return filteredRoutes
    }, [])

  return filterRoutes(routes)
}

export const filterFurnitureLiteFixedRoutes = <T extends FurnitureLiteRoute>(routes: T[]): T[] => {
  if (!isFurnitureLiteMode()) {
    return routes
  }

  const filterRoutes = (items: T[], parentPath = ''): T[] =>
    items.reduce<T[]>((filteredRoutes, route) => {
      const fullPath = normalizeRoutePath(route.path, parentPath)

      if (isPathDenied(fullPath)) {
        return filteredRoutes
      }

      const nextRoute = { ...route }
      if (route.children) {
        nextRoute.children = filterRoutes(route.children as T[], fullPath)
      }
      filteredRoutes.push(nextRoute)
      return filteredRoutes
    }, [])

  return filterRoutes(routes)
}
