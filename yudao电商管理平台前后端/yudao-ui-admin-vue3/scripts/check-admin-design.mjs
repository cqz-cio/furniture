import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const readText = (path) => readFileSync(path, 'utf8')

const styleIndex = readText('src/styles/index.scss')
const adminStyle = readText('src/styles/furniture-admin.scss')
const homePage = readText('src/views/Home/Index.vue')
const furnitureLiteConfig = readText('src/config/furnitureLite.ts')
const furnitureNavigationMenuPaths = JSON.parse(
  readText(
    '../yudao-cloud/yudao-module-system/yudao-module-system-server/src/main/resources/navigation/furniture-lite-menu-paths.json'
  )
)

const deniedFixedRoutePrefixesBlock =
  furnitureLiteConfig.match(/const deniedFixedRoutePrefixes = \[[\s\S]*?\]/)?.[0] || ''

assert.ok(
  styleIndex.includes("@use './furniture-admin.scss';"),
  'src/styles/index.scss must import furniture-admin.scss'
)

for (const token of [
  '--furniture-admin-canvas',
  '.furniture-admin-page',
  '.furniture-admin-table-panel',
  '.el-table',
  '.el-tag'
]) {
  assert.ok(adminStyle.includes(token), `furniture admin style must include ${token}`)
}

const topLevelLeafLayout =
  adminStyle.match(/\.erp-shell #v-menu \.el-menu \.el-menu-item \{([\s\S]*?)\}/)?.[1] || ''
const nestedNavigationLayout =
  adminStyle.match(
    /\.erp-shell #v-menu \.el-menu \.el-menu \.el-menu-item,\s*\.erp-shell #v-menu \.el-menu \.el-menu \.el-sub-menu__title \{([\s\S]*?)\}/
  )?.[1] || ''

assert.ok(
  !topLevelLeafLayout.includes('padding-left: 46px'),
  'top-level leaf routes must align with top-level submenu titles'
)
assert.ok(
  nestedNavigationLayout.includes('padding-left: 46px !important'),
  'nested leaf routes and expandable directories must share child-menu indentation'
)
assert.ok(
  !adminStyle.includes('left: -10px;'),
  'active navigation must not add an off-row selection marker'
)

for (const token of [
  'furniture-admin-page',
  '今日订单',
  '待发货',
  '售后待处理',
  '低库存',
  '支付异常',
  '地址核验'
]) {
  assert.ok(homePage.includes(token), `home dashboard must include ${token}`)
}

for (const route of ['/ai', '/ai/chat', '/ai/model', '/ai/knowledge', '/ai/workflow']) {
  assert.ok(
    furnitureNavigationMenuPaths.includes(route),
    `the backend furniture navigation catalog must allow ${route}`
  )
}

assert.ok(
  !deniedFixedRoutePrefixesBlock.includes("'/ai'") &&
    !deniedFixedRoutePrefixesBlock.includes('"/ai"'),
  'furniture-lite fixed routes must not deny /ai'
)

for (const route of ['/bpm', '/iot', '/mes', '/diy', '/codegen', '/job']) {
  assert.ok(
    deniedFixedRoutePrefixesBlock.includes(`'${route}'`) ||
      deniedFixedRoutePrefixesBlock.includes(`"${route}"`),
    `furniture-lite fixed routes must continue to deny ${route}`
  )
}

console.log('Furniture admin design checks passed')
