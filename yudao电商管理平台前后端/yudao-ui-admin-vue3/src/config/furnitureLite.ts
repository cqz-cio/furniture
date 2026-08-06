export type FurnitureLiteRoute = {
  path: string
  children?: FurnitureLiteRoute[]
}

const deniedFixedRoutePrefixes = ['/bpm', '/iot', '/mes', '/diy', '/codegen', '/job']

const menuTitleOverrides: Record<string, string> = {
  '/dashboard': '数据看板',
  '/mall': '商城系统',
  '/mall/product': '商品中心',
  '/mall/product/spu': '商品管理',
  '/mall/product/category': '商品分类',
  '/mall/product/brand': '品牌管理',
  '/mall/product/property': '商品规格',
  '/mall/product/comment': '商品评价',
  '/mall/trade': '订单中心',
  '/mall/trade/order': '订单管理',
  '/mall/trade/after-sale': '售后管理',
  '/mall/trade/delivery': '配送管理',
  '/mall/trade/delivery/express': '快递公司',
  '/mall/trade/delivery/express/express-template': '运费模板',
  '/mall/trade/delivery/express-template': '运费模板',
  '/mall/trade/delivery/pick-up-store': '自提门店',
  '/crm': '询盘中心',
  '/crm/clue': '询盘汇总',
  '/crm/customer': '客户档案',
  '/crm/contact': '联系人管理',
  '/seo': '官网运营',
  '/seo/metadata': 'SEO 待办',
  '/seo/site-config': '站点设置',
  '/seo/navigation': '导航管理',
  '/seo/analysis': '关键词分析',
  '/infra/file': '文件素材',
  '/pay/order': '支付订单',
  '/pay/refund': '退款订单',
  '/member': '会员中心',
  '/member/user': '会员用户',
  '/member/trade-application': '交易申请',
  '/member/membership': '会员权益',
  '/member/gift-registry': '礼品登记',
  '/member/level': '会员等级',
  '/member/tag': '会员标签',
  '/member/group': '会员分组'
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

const isB2CMenuScope = (allowedMenuPaths: Set<string>): boolean =>
  allowedMenuPaths.has('/member/user') &&
  allowedMenuPaths.has('/mall/trade/order') &&
  !allowedMenuPaths.has('/crm/clue') &&
  !allowedMenuPaths.has('/system/role')

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

const cloneNavigationRoutes = (routes: AppRouteRecordRaw[]): AppRouteRecordRaw[] =>
  routes.map((route) => ({
    ...route,
    meta: { ...route.meta },
    children: route.children ? cloneNavigationRoutes(route.children) : undefined
  }))

const findNavigationRoute = (
  routes: AppRouteRecordRaw[],
  targetPath: string,
  parentPath = ''
): AppRouteRecordRaw | undefined => {
  for (const route of routes) {
    const fullPath = normalizeRoutePath(route.path, parentPath)
    if (fullPath === targetPath) {
      return route
    }
    const child = route.children
      ? findNavigationRoute(route.children, targetPath, fullPath)
      : undefined
    if (child) {
      return child
    }
  }
}

const detachNavigationRoute = (
  routes: AppRouteRecordRaw[],
  targetPath: string,
  parentPath = ''
): AppRouteRecordRaw | undefined => {
  for (let index = 0; index < routes.length; index += 1) {
    const route = routes[index]
    const fullPath = normalizeRoutePath(route.path, parentPath)
    if (fullPath === targetPath) {
      return routes.splice(index, 1)[0]
    }
    const child = route.children
      ? detachNavigationRoute(route.children, targetPath, fullPath)
      : undefined
    if (child) {
      return child
    }
  }
}

const removeTopLevelNavigationRoute = (routes: AppRouteRecordRaw[], targetPath: string) => {
  const index = routes.findIndex((route) => normalizeRoutePath(route.path) === targetPath)
  if (index >= 0) {
    routes.splice(index, 1)
  }
}

const asMenuTarget = (
  route: AppRouteRecordRaw,
  path: string,
  menuPath: string,
  title: string
): AppRouteRecordRaw => ({
  ...route,
  path,
  meta: {
    ...route.meta,
    title,
    hidden: false,
    alwaysShow: false,
    menuPath
  }
})

/**
 * 构造 B2C 工作人员看到的业务导航树。
 *
 * 实际路由仍按后端原树注册；这里只重组侧边栏展示，因此支付、文件页面继续使用原 URL，
 * 不会破坏接口权限、面包屑或已有外部链接。
 */
export const buildFurnitureLiteNavigationRoutes = (
  routes: AppRouteRecordRaw[],
  synchronizedMenuPaths: Iterable<string> = []
): AppRouteRecordRaw[] => {
  if (!isFurnitureLiteMode()) {
    return routes
  }

  const allowedMenuPaths = new Set(
    Array.from(synchronizedMenuPaths, (path) => normalizeRoutePath(path))
  )
  if (!isB2CMenuScope(allowedMenuPaths)) {
    return routes
  }

  const navigationRoutes = cloneNavigationRoutes(routes)
  const orderCenter = findNavigationRoute(navigationRoutes, '/mall/trade')
  if (orderCenter) {
    const paymentOrder = detachNavigationRoute(navigationRoutes, '/pay/order')
    const paymentRefund = detachNavigationRoute(navigationRoutes, '/pay/refund')
    const paymentMenus = [
      paymentOrder && asMenuTarget(paymentOrder, 'payment-order', '/pay/order', '支付订单'),
      paymentRefund && asMenuTarget(paymentRefund, 'payment-refund', '/pay/refund', '退款订单')
    ].filter(Boolean) as AppRouteRecordRaw[]
    if (paymentMenus.length > 0) {
      orderCenter.children = [...(orderCenter.children || []), ...paymentMenus]
      removeTopLevelNavigationRoute(navigationRoutes, '/pay')
    }
  }

  const websiteOperations = findNavigationRoute(navigationRoutes, '/seo')
  if (websiteOperations) {
    const fileMaterial = detachNavigationRoute(navigationRoutes, '/infra/file')
    if (fileMaterial) {
      websiteOperations.children = [
        ...(websiteOperations.children || []),
        asMenuTarget(fileMaterial, 'file-material', '/infra/file', '文件素材')
      ]
      removeTopLevelNavigationRoute(navigationRoutes, '/infra')
    }
  }

  const productCenter = findNavigationRoute(navigationRoutes, '/mall/product')
  if (
    productCenter &&
    !(productCenter.children || []).some(
      (route) => route.meta?.menuPath === '/mall/product/spu?tabType=3'
    )
  ) {
    productCenter.children = [
      ...(productCenter.children || []),
      {
        path: 'low-stock',
        name: 'MallProductLowStockMenu',
        meta: {
          title: '库存预警',
          icon: 'ep:warning-filled',
          hidden: false,
          alwaysShow: false,
          noCache: true,
          menuPath: '/mall/product/spu?tabType=3'
        }
      }
    ]
  }

  const navigationRank: Record<string, number> = {
    '/index': 0,
    '/mall': 10,
    '/member': 20,
    '/seo': 30,
    '/dashboard': 40
  }
  return navigationRoutes.sort(
    (left, right) =>
      (navigationRank[normalizeRoutePath(left.path)] ?? 100) -
      (navigationRank[normalizeRoutePath(right.path)] ?? 100)
  )
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
