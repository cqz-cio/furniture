import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'

const readText = (path) => readFileSync(path, 'utf8')
const readRequired = (path, hint) => {
  assert.ok(existsSync(path), `${path} is missing. ${hint}`)
  return readFileSync(path, 'utf8')
}

const envLocal = readText('.env.local')
const packageJson = JSON.parse(readText('package.json'))
const furnitureLiteConfig = readRequired(
  'src/config/furnitureLite.ts',
  'Furniture-lite mode must consume the backend navigation catalog and keep its fixed-route deny list.'
)
const furnitureNavigationMenuPaths = JSON.parse(
  readRequired(
    '../yudao-cloud/yudao-module-system/yudao-module-system-server/src/main/resources/navigation/furniture-lite-menu-paths.json',
    'The backend-owned furniture navigation catalog must be available to both permission sync and the admin UI.'
  )
)
const furnitureB2cMenuPaths = JSON.parse(
  readRequired(
    '../yudao-cloud/yudao-module-system/yudao-module-system-server/src/main/resources/navigation/furniture-b2c-menu-paths.json',
    'B2C workers must have a backend-owned navigation and permission catalog.'
  )
)
const furnitureB2bMenuPaths = JSON.parse(
  readRequired(
    '../yudao-cloud/yudao-module-system/yudao-module-system-server/src/main/resources/navigation/furniture-b2b-menu-paths.json',
    'B2B workers must keep a separate navigation and permission catalog.'
  )
)
const permissionStore = readRequired(
  'src/store/modules/permission.ts',
  'Task 2 should wire furniture lite menu and fixed route filters into the permission store.'
)
const routerIndex = readRequired(
  'src/router/index.ts',
  'Task 3 should filter fixed routes before Vue Router registers them.'
)
const docAlert = readRequired(
  'src/components/DocAlert/index.vue',
  'Task 2 should gate the documentation alert with isDocAlertVisible.'
)
const userInfo = readRequired(
  'src/layout/components/UserInfo/src/UserInfo.vue',
  'Task 2 should gate developer links with isDevLinksVisible.'
)
const loginForm = readRequired(
  'src/views/Login/components/LoginForm.vue',
  'Task 2 should gate login developer links with showDevLinks.'
)
const productSpuList = readRequired(
  'src/views/mall/product/spu/index.vue',
  'The product list should expose a frontend product preview action for furniture operators.'
)
const homeView = readRequired(
  'src/views/Home/Index.vue',
  'The B2C homepage shortcuts must only link to routes available to B2C workers.'
)
const menuRenderer = readRequired(
  'src/layout/components/Menu/src/components/useRenderMenuItem.tsx',
  'The sidebar must support business grouping while preserving the real route target.'
)
const membershipApi = readRequired(
  'src/api/member/membership/index.ts',
  'Real-account launch readiness requires the Membership admin API wiring.'
)
const membershipView = readRequired(
  'src/views/member/membership/index.vue',
  'Real-account launch readiness requires the Membership admin page.'
)
const giftRegistryApi = readRequired(
  'src/api/member/giftRegistry/index.ts',
  'Real-account launch readiness requires the Gift Registry admin API wiring.'
)
const giftRegistryView = readRequired(
  'src/views/member/gift-registry/index.vue',
  'Real-account launch readiness requires the Gift Registry admin page.'
)
const tradeApplicationApi = readRequired(
  'src/api/member/trade/application/index.ts',
  'Real-account launch readiness requires the Trade Application admin API wiring.'
)
const tradeApplicationView = readRequired(
  'src/views/member/trade/application/index.vue',
  'Real-account launch readiness requires the Trade Application admin review page.'
)

const requiredEnvLines = [
  'VITE_ADMIN_MODE=furniture-lite',
  'VITE_SHOW_DOC_ALERT=false',
  'VITE_SHOW_DEV_LINKS=false',
  'VITE_APP_DOCALERT_ENABLE=false',
  'VITE_FURNITURE_WEB_URL=http://127.0.0.1:5173'
]

for (const line of requiredEnvLines) {
  assert.ok(envLocal.includes(line), `.env.local must include ${line}`)
}

assert.equal(
  packageJson.scripts['check:furniture-lite'],
  'node scripts/check-furniture-lite-config.mjs',
  'package.json must define check:furniture-lite'
)

const requiredFurnitureLiteExports = [
  'isFurnitureLiteMode',
  'isDocAlertVisible',
  'isDevLinksVisible',
  'filterFurnitureLiteMenus',
  'buildFurnitureLiteNavigationRoutes',
  'filterFurnitureLiteFixedRoutes'
]

for (const exportName of requiredFurnitureLiteExports) {
  assert.ok(
    furnitureLiteConfig.includes(`export const ${exportName}`) ||
      furnitureLiteConfig.includes(`export function ${exportName}`),
    `src/config/furnitureLite.ts must export ${exportName}`
  )
}

const requiredFurnitureLiteConfigTokens = [
  'synchronizedMenuPaths',
  'allowedMenuPaths',
  'deniedFixedRoutePrefixes',
  "'/mall/product/spu?tabType=3'",
  "'/pay/order'",
  "'/infra/file'"
]

for (const token of requiredFurnitureLiteConfigTokens) {
  assert.ok(
    furnitureLiteConfig.includes(token),
    `src/config/furnitureLite.ts must include ${token}`
  )
}

const deniedFixedRoutePrefixesBlock =
  furnitureLiteConfig.match(/const deniedFixedRoutePrefixes = \[[\s\S]*?\]/)?.[0] || ''

for (const route of [
  '/mall/product/category',
  '/mall/product/comment',
  '/mall/statistics',
  '/mall/statistics/product',
  '/mall/trade/order',
  '/member/membership',
  '/member/gift-registry',
  '/member/trade-application',
  '/system/role',
  '/system/messages/mail/mail-account',
  '/system/messages/mail/mail-template',
  '/system/messages/mail/mail-log',
  '/infra/file/file-config'
]) {
  assert.ok(
    furnitureNavigationMenuPaths.includes(route),
    `the backend furniture navigation catalog must include ${route}`
  )
}

const requiredAiRoutes = ['/ai', '/ai/chat', '/ai/model', '/ai/knowledge', '/ai/workflow']

for (const route of requiredAiRoutes) {
  assert.ok(
    furnitureNavigationMenuPaths.includes(route),
    `the backend furniture navigation catalog must allow AI menu route ${route}`
  )
}

assert.ok(
  !deniedFixedRoutePrefixesBlock.includes("'/ai'") &&
    !deniedFixedRoutePrefixesBlock.includes('"/ai"'),
  'src/config/furnitureLite.ts must not deny /ai fixed routes in furniture-lite mode'
)

const requiredCrmRoutes = ['/crm', '/crm/clue', '/crm/customer', '/crm/contact']

for (const route of requiredCrmRoutes) {
  assert.ok(
    furnitureNavigationMenuPaths.includes(route),
    `the backend furniture navigation catalog must allow CRM menu route ${route}`
  )
}

for (const route of [
  '/mall/product/spu',
  '/mall/trade/order',
  '/mall/trade/after-sale',
  '/member/user',
  '/member/membership',
  '/member/gift-registry',
  '/pay/order',
  '/pay/refund',
  '/infra/file',
  '/seo/navigation',
  '/seo/analysis',
  '/dashboard'
]) {
  assert.ok(furnitureB2cMenuPaths.includes(route), `the B2C catalog must include ${route}`)
}

for (const route of [
  '/crm',
  '/crm/clue',
  '/member/trade-application',
  '/pay/app',
  '/ai',
  '/system/user',
  '/system/role',
  '/infra/file-config'
]) {
  assert.ok(!furnitureB2cMenuPaths.includes(route), `the B2C catalog must not include ${route}`)
}

for (const route of ['/crm/clue', '/crm/customer', '/crm/contact', '/mall/product/spu']) {
  assert.ok(furnitureB2bMenuPaths.includes(route), `the B2B catalog must include ${route}`)
}

for (const route of ['/mall/trade/order', '/member/user', '/pay/order', '/system/role']) {
  assert.ok(!furnitureB2bMenuPaths.includes(route), `the B2B catalog must not include ${route}`)
}

for (const route of [
  '/crm/backlog',
  '/crm/customer/pool',
  '/crm/business',
  '/crm/contract',
  '/crm/receivable',
  '/crm/receivable-plan',
  '/crm/product',
  '/crm/statistics',
  '/crm/config'
]) {
  assert.ok(
    !furnitureNavigationMenuPaths.includes(route),
    `the inquiry-center navigation catalog must hide unused CRM route ${route}`
  )
}

assert.ok(
  !deniedFixedRoutePrefixesBlock.includes("'/crm'") &&
    !deniedFixedRoutePrefixesBlock.includes('"/crm"'),
  'src/config/furnitureLite.ts must not deny /crm fixed routes'
)

for (const route of ['/bpm', '/iot', '/mes', '/diy', '/codegen', '/job']) {
  assert.ok(
    deniedFixedRoutePrefixesBlock.includes(`'${route}'`) ||
      deniedFixedRoutePrefixesBlock.includes(`"${route}"`),
    `src/config/furnitureLite.ts must continue to deny ${route} fixed routes`
  )
}

for (const token of ['filterFurnitureLiteMenus', 'filterFurnitureLiteFixedRoutes']) {
  assert.ok(
    permissionStore.includes(token),
    `src/store/modules/permission.ts must reference ${token}`
  )
}

assert.ok(
  permissionStore.includes('userInfo?.furnitureNavigationMenuPaths'),
  'src/store/modules/permission.ts must filter menus with the backend-synchronized navigation paths'
)
assert.ok(
  permissionStore.includes('buildFurnitureLiteNavigationRoutes'),
  'src/store/modules/permission.ts must build the B2C business navigation tree'
)
assert.ok(
  menuRenderer.includes('meta.menuPath'),
  'the sidebar must use the real target of regrouped B2C menu items'
)
assert.ok(
  homeView.includes("path: '/mall/trade/after-sale'") &&
    !homeView.includes("path: '/system/role'") &&
    homeView.includes("path: '/mall/product/spu?tabType=3'"),
  'the B2C homepage must link to after-sales and low stock without exposing role management'
)
assert.ok(
  productSpuList.includes('route.query.tabType') && productSpuList.includes('resolveRouteTabType'),
  'the product page must open the requested inventory tab from the navigation query'
)

assert.ok(
  routerIndex.includes('filterFurnitureLiteFixedRoutes') &&
    routerIndex.includes('furnitureLiteRemainingRouter'),
  'src/router/index.ts must filter fixed routes before Vue Router registers them'
)

assert.ok(
  docAlert.includes('isDocAlertVisible'),
  'src/components/DocAlert/index.vue must reference isDocAlertVisible'
)
assert.ok(
  userInfo.includes('isDevLinksVisible'),
  'src/layout/components/UserInfo/src/UserInfo.vue must reference isDevLinksVisible'
)
assert.ok(
  loginForm.includes('showDevLinks'),
  'src/views/Login/components/LoginForm.vue must reference showDevLinks'
)
assert.ok(
  productSpuList.includes('openFrontendPreview') &&
    productSpuList.includes('VITE_FURNITURE_WEB_URL') &&
    productSpuList.includes('前台预览'),
  'src/views/mall/product/spu/index.vue must provide a furniture web preview action'
)

const requiredRealAccountAdminTokens = [
  [
    membershipApi,
    '/member/membership/page',
    'src/api/member/membership/index.ts must read membership pages'
  ],
  [
    membershipApi,
    '/member/membership/get',
    'src/api/member/membership/index.ts must read membership details'
  ],
  [
    membershipApi,
    '/member/membership/open',
    'src/api/member/membership/index.ts must keep Admin membership opening'
  ],
  [
    membershipApi,
    '/member/membership/update',
    'src/api/member/membership/index.ts must update membership status'
  ],
  [
    membershipView,
    'member:membership:query',
    'src/views/member/membership/index.vue must guard membership queries'
  ],
  [
    membershipView,
    'member:membership:update',
    'src/views/member/membership/index.vue must guard membership updates'
  ],
  [
    giftRegistryApi,
    '/member/gift-registry/page',
    'src/api/member/giftRegistry/index.ts must read registry pages'
  ],
  [
    giftRegistryApi,
    '/member/gift-registry/get',
    'src/api/member/giftRegistry/index.ts must read registry details'
  ],
  [
    giftRegistryApi,
    '/member/gift-registry/status',
    'src/api/member/giftRegistry/index.ts must update registry status'
  ],
  [
    giftRegistryView,
    'member:gift-registry:query',
    'src/views/member/gift-registry/index.vue must guard registry queries'
  ],
  [
    giftRegistryView,
    'member:gift-registry:update',
    'src/views/member/gift-registry/index.vue must guard registry updates'
  ],
  [
    tradeApplicationApi,
    '/member/trade-application/page',
    'src/api/member/trade/application/index.ts must read trade application pages'
  ],
  [
    tradeApplicationApi,
    '/member/trade-application/approve',
    'src/api/member/trade/application/index.ts must approve trade applications'
  ],
  [
    tradeApplicationApi,
    '/member/trade-application/reject',
    'src/api/member/trade/application/index.ts must reject trade applications'
  ],
  [
    tradeApplicationView,
    'member:trade-application:query',
    'src/views/member/trade/application/index.vue must guard trade application queries'
  ],
  [
    tradeApplicationView,
    'member:trade-application:review',
    'src/views/member/trade/application/index.vue must guard trade application reviews'
  ]
]

for (const [source, token, message] of requiredRealAccountAdminTokens) {
  assert.ok(source.includes(token), message)
}

console.log('Furniture lite config checks passed')
