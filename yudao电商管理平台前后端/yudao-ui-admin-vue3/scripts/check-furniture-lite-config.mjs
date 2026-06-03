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
  'Task 2 should add the furniture lite config exports and route allow/deny lists.'
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

const requiredEnvLines = [
  'VITE_ADMIN_MODE=furniture-lite',
  'VITE_SHOW_DOC_ALERT=false',
  'VITE_SHOW_DEV_LINKS=false',
  'VITE_APP_DOCALERT_ENABLE=false'
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
  '/mall/product/category',
  '/mall/trade/order',
  '/system/role',
  '/infra/file/file-config',
  'deniedFixedRoutePrefixes',
  '/ai',
  '/crm'
]

for (const token of requiredFurnitureLiteConfigTokens) {
  assert.ok(furnitureLiteConfig.includes(token), `src/config/furnitureLite.ts must include ${token}`)
}

for (const token of ['filterFurnitureLiteMenus', 'filterFurnitureLiteFixedRoutes']) {
  assert.ok(permissionStore.includes(token), `src/store/modules/permission.ts must reference ${token}`)
}

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

console.log('Furniture lite config checks passed')
