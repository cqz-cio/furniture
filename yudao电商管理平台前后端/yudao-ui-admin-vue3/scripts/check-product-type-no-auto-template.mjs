import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const furnitureDetail = await readFile(
  new URL('../src/views/mall/product/spu/form/FurnitureDetailForm.vue', import.meta.url),
  'utf8'
)
const infoForm = await readFile(
  new URL('../src/views/mall/product/spu/form/InfoForm.vue', import.meta.url),
  'utf8'
)
const productTypeOptions = await readFile(
  new URL('../src/views/mall/product/spu/form/productTypeOptions.ts', import.meta.url),
  'utf8'
)

assert.match(furnitureDetail, /<el-select[\s\S]*?v-model="detailConfig\.productType"/)
assert.match(furnitureDetail, /v-for="option in productTypeOptions"/)
assert.match(furnitureDetail, /:label="option\.label"/)
assert.match(furnitureDetail, /:value="option\.value"/)
assert.doesNotMatch(furnitureDetail, /:model-value="selectedCategoryName"/)

const roomOptions = {
  diningRoom: [
    ['DING CHAIRS', 'dining-chair'],
    ['BAR STOOLS', 'bar-stool'],
    ['DING TABLES', 'dining-table']
  ],
  livingRoom: [
    ['Sofa & Occasional Chair', 'sofa'],
    ['Side Table & Coffee Table', 'coffee-table'],
    ['Bookcase & Display Cabinet', 'bookcase'],
    ['Console Table & Buffet', 'media-console']
  ],
  bedroom: [
    ['Bed & Headboard', 'bed'],
    ['Bedside Table', 'nightstand'],
    ['Chest of Drawer', 'dresser'],
    ['Bench', 'bench'],
    ['Dressing Table', 'dressing-table'],
    ['Wadrobe', 'wardrobe']
  ]
}
for (const [room, options] of Object.entries(roomOptions)) {
  assert.match(productTypeOptions, new RegExp(`${room}: \\[`))
  for (const [label, value] of options) {
    const option = `{ label: '${label}', value: '${value}' }`
    assert.ok(productTypeOptions.includes(option), `Missing P1 Product type option: ${option}`)
  }
}
assert.match(productTypeOptions, /resolveProductRoom/)
assert.match(productTypeOptions, /isProductTypeValid/)
assert.match(productTypeOptions, /migrateProductType/)
assert.match(furnitureDetail, /detailConfig\.productType = migrateProductType\(room, productType\)/)
assert.match(furnitureDetail, /normalized\.productType = normalized\.productType\.trim\(\)/)
assert.match(infoForm, /@change="syncCategorySelection"/)
assert.doesNotMatch(furnitureDetail, /@change="applyTemplate"/)
assert.doesNotMatch(furnitureDetail, /\bconst templates\b/)
assert.doesNotMatch(furnitureDetail, /\bapplyTemplate\b/)
assert.match(furnitureDetail, /不会自动填充或修改其他内容。/)

for (const exampleContent of [
  'MARBLE DINING COLLECTION',
  'White Carrara',
  'CLOUD MODULAR COLLECTION',
  'Sand Performance Linen',
  'LUXE BED COLLECTION',
  'ARCHITECTURAL LIGHTING COLLECTION'
]) {
  assert.doesNotMatch(furnitureDetail, new RegExp(exampleContent, 'i'))
}

assert.match(furnitureDetail, /collection: ''/)
assert.match(furnitureDetail, /heroNote: ''/)
assert.match(furnitureDetail, /stockedCount: 0/)
assert.match(furnitureDetail, /specialOrderCount: 0/)
assert.match(furnitureDetail, /highlights: \[\]/)
assert.match(furnitureDetail, /optionGroups: \[\]/)
assert.match(furnitureDetail, /accordions: \[\]/)
assert.match(furnitureDetail, /relatedLinks: \[\]/)

console.log('Product type no-auto-template contract passed.')
