import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync('src/config/furnitureLite.ts', 'utf8')
const allowedMenuBlock = source.match(/const allowedMenuPaths = new Set\(\[[\s\S]*?\]\)/)?.[0] || ''
const deniedFixedRoutePrefixesBlock =
  source.match(/const deniedFixedRoutePrefixes = \[[\s\S]*?\]/)?.[0] || ''

const hiddenAiRoutes = ['/ai', '/ai/chat', '/ai/model', '/ai/knowledge', '/ai/workflow']

for (const route of hiddenAiRoutes) {
  assert.ok(
    !allowedMenuBlock.includes(`'${route}'`) && !allowedMenuBlock.includes(`"${route}"`),
    `furniture-lite mode must hide generic AI menu route ${route}`
  )
}

assert.ok(
  deniedFixedRoutePrefixesBlock.includes("'/ai'") ||
    deniedFixedRoutePrefixesBlock.includes('"/ai"'),
  'furniture-lite mode must deny /ai fixed routes'
)

console.log('Furniture lite AI hidden checks passed')
