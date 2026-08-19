import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

import {
  getProductRoomOptions,
  getProductTypeOptions,
  isProductTypeSelectionValid,
  migrateProductType,
  resolveProductRoom
} from '../src/views/mall/product/spu/form/productTypeOptions.ts'

const source = await readFile(
  new URL('../src/views/mall/product/spu/form/productTypeOptions.ts', import.meta.url),
  'utf8'
)

const categories = [
  { id: 10, parentId: 0, code: 'dining-room', name: 'Dining Room Furniture', sort: 10, status: 0 },
  { id: 20, parentId: 0, code: 'living-room', name: 'Living Room Furniture', sort: 20, status: 0 },
  { id: 30, parentId: 0, code: 'bedroom', name: 'Bedroom Furniture', sort: 30, status: 0 },
  { id: 101, parentId: 10, code: 'dining-chair', name: 'DINING CHAIRS', sort: 10, status: 0 },
  { id: 102, parentId: 10, code: 'bar-stool', name: 'BAR STOOLS', sort: 20, status: 0 },
  { id: 103, parentId: 10, code: 'dining-table', name: 'DINING TABLES', sort: 30, status: 0 },
  { id: 201, parentId: 20, code: 'sofa', name: 'SOFA & OCCASIONAL CHAIR', sort: 10, status: 0 },
  { id: 202, parentId: 20, code: 'coffee-table', name: 'SIDE TABLE & COFFEE TABLE', sort: 20, status: 0 },
  { id: 203, parentId: 20, code: 'bookcase', name: 'BOOKCASE & DISPLAY CABINET', sort: 30, status: 0 },
  { id: 204, parentId: 20, code: 'media-console', name: 'CONSOLE TABLE & BUFFET', sort: 40, status: 0 },
  { id: 301, parentId: 30, code: 'bed', name: 'BED & HEADBOARD', sort: 10, status: 0 },
  { id: 302, parentId: 30, code: 'nightstand', name: 'BEDSIDE TABLE', sort: 20, status: 0 },
  { id: 303, parentId: 30, code: 'dresser', name: 'CHEST OF DRAWERS', sort: 30, status: 0 },
  { id: 304, parentId: 30, code: 'bench', name: 'BENCH', sort: 40, status: 0 },
  { id: 305, parentId: 30, code: 'dressing-table', name: 'DRESSING TABLE', sort: 50, status: 0 },
  { id: 306, parentId: 30, code: 'wardrobe', name: 'WARDROBE', sort: 60, status: 0 }
]

assert.doesNotMatch(source, /ROOM_PRODUCT_TYPE_OPTIONS/)
assert.deepEqual(
  getProductRoomOptions(categories).map(({ code }) => code),
  ['dining-room', 'living-room', 'bedroom']
)
assert.deepEqual(
  getProductTypeOptions(categories, 30).map(({ code }) => code),
  ['bed', 'nightstand', 'dresser', 'bench', 'dressing-table', 'wardrobe']
)
assert.equal(resolveProductRoom(categories, 101)?.id, 10)
assert.equal(resolveProductRoom(categories, 20)?.id, 20)
assert.equal(resolveProductRoom(categories, 999), undefined)
assert.equal(isProductTypeSelectionValid(categories, 20, 203), true)
assert.equal(isProductTypeSelectionValid(categories, 20, 301), false)
assert.equal(migrateProductType(categories, 30, 'bed-bench'), 304)
assert.equal(migrateProductType(categories, 30, 'vanity'), 305)
assert.equal(migrateProductType(categories, 10, 'round-table'), 103)
assert.equal(migrateProductType(categories, 20, 'single-sofa'), 201)
assert.equal(migrateProductType(categories, 30, 'chair'), undefined)

console.log('Product type category API contract passed.')
