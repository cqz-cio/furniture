export type FurnitureLiteRoute = {
  path: string
  children?: FurnitureLiteRoute[]
}

const deniedFixedRoutePrefixes = ['/bpm', '/iot', '/mes', '/diy', '/codegen', '/job']

const menuTitleOverrides: Record<string, string> = {
  '/crm': '询盘中心',
  '/crm/clue': '询盘汇总',
  '/crm/customer': '客户档案',
  '/crm/contact': '联系人管理',
  '/seo': '官网运营',
  '/seo/metadata': 'SEO 待办',
  '/seo/site-config': '站点设置',
  '/seo/navigation': '导航管理',
  '/member/trade-application': '交易申请',
  '/member/membership': '会员权益',
  '/member/gift-registry': '礼品登记'
}

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

const isPathDenied = (path: string): boolean =>
  deniedFixedRoutePrefixes.some((prefix) => path === prefix || path.startsWith(`${prefix}/`))

const isProductOnlyMallScope = (allowedMenuPaths: Set<string>): boolean => {
  const mallMenuPaths = Array.from(allowedMenuPaths).filter((path) => path.startsWith('/mall/'))

  return (
    mallMenuPaths.length > 0 &&
    mallMenuPaths.every((path) => path === '/mall/product' || path.startsWith('/mall/product/'))
  )
}

const collapseProductCenterMenu = <T extends FurnitureLiteRoute>(routes: T[]): T[] =>
  routes.map((route) => {
    const mallPath = normalizeRoutePath(route.path)
    if (mallPath !== '/mall' || route.children?.length !== 1) {
      return route
    }

    const productCenterRoute = route.children[0] as T
    const productCenterPath = normalizeRoutePath(productCenterRoute.path, mallPath)
    if (productCenterPath !== '/mall/product' || !productCenterRoute.children?.length) {
      return route
    }

    return {
      ...route,
      children: (productCenterRoute.children as T[]).map((child) => ({
        ...child,
        // Keep the existing /mall/product/* URLs while removing the redundant menu level.
        path: normalizeRoutePath(child.path, productCenterPath).slice(`${mallPath}/`.length)
      }))
    }
  })

const applyFurnitureLiteTitles = <T extends FurnitureLiteRoute>(route: T, fullPath: string): T => {
  const nextRoute = { ...route }
  const overrideTitle = menuTitleOverrides[fullPath]

  if (overrideTitle && 'name' in nextRoute) {
    ;(nextRoute as T & { name?: string }).name = overrideTitle
  }

  return nextRoute
}

export const isFurnitureLiteMode = (): boolean =>
  import.meta.env.VITE_ADMIN_MODE === 'furniture-lite'

export const isDocAlertVisible = (): boolean =>
  !isFalseEnvValue(import.meta.env.VITE_SHOW_DOC_ALERT) &&
  !isFalseEnvValue(import.meta.env.VITE_APP_DOCALERT_ENABLE)

export const isDevLinksVisible = (): boolean =>
  !isFalseEnvValue(import.meta.env.VITE_SHOW_DEV_LINKS)

export const filterFurnitureLiteMenus = <T extends FurnitureLiteRoute>(
  routes: T[],
  synchronizedMenuPaths: Iterable<string> = []
): T[] => {
  if (!isFurnitureLiteMode()) {
    return routes
  }

  const allowedMenuPaths = new Set(
    Array.from(synchronizedMenuPaths, (path) => normalizeRoutePath(path))
  )
  const filterRoutes = (items: T[], parentPath = ''): T[] =>
    items.reduce<T[]>((filteredRoutes, route) => {
      const fullPath = normalizeRoutePath(route.path, parentPath)
      const children = route.children ? filterRoutes(route.children as T[], fullPath) : undefined

      if (!allowedMenuPaths.has(fullPath) && (!children || children.length === 0)) {
        return filteredRoutes
      }

      const nextRoute = applyFurnitureLiteTitles(route, fullPath)
      if (route.children) {
        nextRoute.children = children
      }
      filteredRoutes.push(nextRoute)
      return filteredRoutes
    }, [])

  const filteredRoutes = filterRoutes(routes)
  return isProductOnlyMallScope(allowedMenuPaths)
    ? collapseProductCenterMenu(filteredRoutes)
    : filteredRoutes
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
