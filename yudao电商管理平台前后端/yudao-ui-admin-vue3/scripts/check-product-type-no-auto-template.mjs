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

assert.doesNotMatch(furnitureDetail, /v-model="detailConfig\.productType"/)
assert.match(furnitureDetail, /:model-value="selectedCategoryName"/)
for (const mapping of [
  "'dining-chairs': 'dining-chair'",
  "'bar-stools': 'bar-stool'",
  "'dining-tables': 'dining-table'"
]) {
  assert.ok(furnitureDetail.includes(mapping), `Missing website category mapping: ${mapping}`)
}
assert.match(furnitureDetail, /normalized\.productType = selectedProductType\.value/)
assert.match(infoForm, /@change="syncCategorySelection"/)
assert.doesNotMatch(furnitureDetail, /@change="applyTemplate"/)
assert.doesNotMatch(furnitureDetail, /\bconst templates\b/)
assert.doesNotMatch(furnitureDetail, /\bapplyTemplate\b/)
assert.match(furnitureDetail, /自动跟随“基础设置 → 商品分类”/)
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
