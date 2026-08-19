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

assert.match(infoForm, /v-model="formData\.roomCategoryId"/)
assert.match(infoForm, /v-for="room in productRoomOptions"/)
assert.match(infoForm, /v-model="formData\.categoryId"/)
assert.match(infoForm, /v-for="option in productTypeOptions"/)
assert.match(infoForm, /getProductRoomOptions\(categoryList\.value\)/)
assert.match(infoForm, /getProductTypeOptions\(categoryList\.value, formData\.roomCategoryId\)/)
assert.match(infoForm, /isProductTypeSelectionValid/)
assert.doesNotMatch(productTypeOptions, /ROOM_PRODUCT_TYPE_OPTIONS/)
assert.doesNotMatch(furnitureDetail, /detailConfig\.productType/)
assert.doesNotMatch(furnitureDetail, /normalized\.productType/)
assert.doesNotMatch(furnitureDetail, /@change="applyTemplate"/)
assert.doesNotMatch(furnitureDetail, /\bconst templates\b/)
assert.doesNotMatch(furnitureDetail, /\bapplyTemplate\b/)

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
