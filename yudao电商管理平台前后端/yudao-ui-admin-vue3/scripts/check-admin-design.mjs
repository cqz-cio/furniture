import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const readText = (path) => readFileSync(path, 'utf8')

const styleIndex = readText('src/styles/index.scss')
const adminStyle = readText('src/styles/furniture-admin.scss')
const homePage = readText('src/views/Home/Index.vue')
const furnitureLiteConfig = readText('src/config/furnitureLite.ts')

const allowedMenuBlock =
  furnitureLiteConfig.match(/const allowedMenuPaths = new Set\(\[[\s\S]*?\]\)/)?.[0] || ''
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

for (const token of [
  'furniture-admin-page',
  '今日订单',
  '待发货',
  '售后待处理',
  '低库存',
  'Payment exceptions',
  'Address verification'
]) {
  assert.ok(homePage.includes(token), `home dashboard must include ${token}`)
}

for (const route of ['/ai', '/ai/chat', '/ai/model', '/ai/knowledge', '/ai/workflow']) {
  assert.ok(!allowedMenuBlock.includes(`'${route}'`), `furniture-lite menu must not allow ${route}`)
}

assert.ok(
  deniedFixedRoutePrefixesBlock.includes("'/ai'"),
  'furniture-lite fixed routes must deny /ai'
)

console.log('Furniture admin design checks passed')
