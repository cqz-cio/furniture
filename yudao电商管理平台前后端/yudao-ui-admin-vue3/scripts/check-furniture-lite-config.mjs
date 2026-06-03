import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const readText = (path) => readFileSync(path, 'utf8')

const envLocal = readText('.env.local')
const packageJson = JSON.parse(readText('package.json'))
const furnitureLiteConfig = readText('src/config/furnitureLite.ts')
const permissionStore = readText('src/store/modules/permission.ts')
const docAlert = readText('src/components/DocAlert/index.vue')
const userInfo = readText('src/layout/components/UserInfo/src/UserInfo.vue')
const loginForm = readText('src/views/Login/components/LoginForm.vue')

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
  'deniedFixedRoutePrefixes',
  '/ai',
  '/crm',
  '/wms'
]

for (const token of requiredFurnitureLiteConfigTokens) {
  assert.ok(furnitureLiteConfig.includes(token), `src/config/furnitureLite.ts must include ${token}`)
}

for (const token of ['filterFurnitureLiteMenus', 'filterFurnitureLiteFixedRoutes']) {
  assert.ok(permissionStore.includes(token), `src/store/modules/permission.ts must reference ${token}`)
}

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
