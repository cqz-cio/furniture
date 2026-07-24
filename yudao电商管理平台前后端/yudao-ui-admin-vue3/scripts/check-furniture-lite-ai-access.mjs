import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync('src/config/furnitureLite.ts', 'utf8')
const furnitureNavigationMenuPaths = JSON.parse(
  readFileSync(
    '../yudao-cloud/yudao-module-system/yudao-module-system-server/src/main/resources/navigation/furniture-lite-menu-paths.json',
    'utf8'
  )
)
const deniedFixedRoutePrefixesBlock =
  source.match(/const deniedFixedRoutePrefixes = \[[\s\S]*?\]/)?.[0] || ''

const requiredAiRoutes = [
  '/ai',
  '/ai/chat',
  '/ai/model',
  '/ai/knowledge',
  '/ai/workflow'
]

for (const route of requiredAiRoutes) {
  assert.ok(
    furnitureNavigationMenuPaths.includes(route),
    `the backend furniture navigation catalog must allow AI menu route ${route}`
  )
}

assert.ok(
  !deniedFixedRoutePrefixesBlock.includes("'/ai'") &&
    !deniedFixedRoutePrefixesBlock.includes('"/ai"'),
  'furniture-lite mode must not deny /ai fixed routes'
)

console.log('Furniture lite AI access checks passed')
