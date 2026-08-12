import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { formatLegacyPacking } from '../src/views/mall/product/spu/form/packingDisplay.ts'

const furnitureDetail = await readFile(
  new URL('../src/views/mall/product/spu/form/FurnitureDetailForm.vue', import.meta.url),
  'utf8'
)

assert.match(furnitureDetail, /v-model="detailConfig\.packingDisplay"/)
assert.match(furnitureDetail, /placeholder="Ships in 2 cartons \/ 1 pc\/ctn \/ 2 packs"/)
assert.match(furnitureDetail, /资料未提供时可留空/)
assert.match(furnitureDetail, /formatLegacyPacking\(legacyPacking\)/)
assert.match(furnitureDetail, /normalized\.packingDisplay = normalized\.packingDisplay\.trim\(\)/)

for (const removedControl of [
  'detailConfig.packing.method',
  'detailConfig.packing.itemQuantity',
  'detailConfig.packing.itemUnit',
  'detailConfig.packing.cartonQuantity',
  'Carton packing',
  'Knock-down carton',
  'Fully assembled carton',
  'Protective export carton',
  'Mail-order carton',
  'product-packing-grid',
  'validatePacking'
]) {
  assert.ok(
    !furnitureDetail.includes(removedControl),
    `Packing must not retain the removed structured control: ${removedControl}`
  )
}

assert.equal(formatLegacyPacking(), '')
assert.equal(formatLegacyPacking(' Ships in 2 cartons '), 'Ships in 2 cartons')
assert.equal(
  formatLegacyPacking({ itemQuantity: 1, itemUnit: 'pc', cartonQuantity: 2 }),
  'Ships in 2 cartons'
)
assert.equal(
  formatLegacyPacking({
    method: 'Knock-down carton',
    itemQuantity: 1,
    itemUnit: 'pc',
    cartonQuantity: 2
  }),
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

console.log('Packing text-field contract passed.')
