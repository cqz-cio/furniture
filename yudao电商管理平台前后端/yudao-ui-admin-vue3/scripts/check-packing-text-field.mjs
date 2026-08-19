import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { formatLegacyPacking } from '../src/views/mall/product/spu/form/packingDisplay.ts'

const furnitureDetail = await readFile(
  new URL('../src/views/mall/product/spu/form/FurnitureDetailForm.vue', import.meta.url),
  'utf8'
)

assert.match(furnitureDetail, /v-model="detailConfig\.packing"/)
assert.match(furnitureDetail, /placeholder="Ships in 2 cartons \/ 1 pc\/ctn \/ 2 packs"/)
assert.match(furnitureDetail, /资料未提供时可留空/)
assert.match(furnitureDetail, /typeof config\?\.packing === 'string'/)
assert.match(furnitureDetail, /config\?\.packingDisplay/)
assert.match(furnitureDetail, /formatLegacyPacking\(legacyPacking\)/)
assert.match(furnitureDetail, /normalized\.packing = normalized\.packing\.trim\(\)/)
assert.doesNotMatch(furnitureDetail, /v-model="detailConfig\.packingDisplay"/)

assert.equal(formatLegacyPacking(), '')
assert.equal(formatLegacyPacking(' Ships in 2 cartons '), 'Ships in 2 cartons')
assert.equal(
  formatLegacyPacking({ itemQuantity: 1, itemUnit: 'pc', cartonQuantity: 2 }),
  'Ships in 2 cartons'
)
assert.equal(
  formatLegacyPacking({ itemQuantity: 2, itemUnit: 'pc', cartonQuantity: 1 }),
  '2 pcs/ctn'
)
assert.equal(
  formatLegacyPacking({ itemQuantity: 1, itemUnit: 'pc', cartonQuantity: 1 }),
  '1 pc/ctn'
)

console.log('Packing canonical text-field contract passed.')
