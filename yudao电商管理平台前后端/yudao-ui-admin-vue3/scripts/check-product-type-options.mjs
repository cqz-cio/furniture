import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const source = await readFile(
  new URL('../src/views/mall/product/spu/form/productTypeOptions.ts', import.meta.url),
  'utf8'
)

const transpiled = source
  .replace(/^import type .*\r?\n/gm, '')
  .replace(/^export type[\s\S]*?^}\r?\n/gm, '')
  .replace(/export const /g, 'const ')
  .replace(/: Record<ProductRoom, ProductTypeOption\[]>/g, '')
  .replace(/: Record<string, ProductRoom>/g, '')
  .replace(/: Record<string, string>/g, '')
  .replace(/\(value: string\)/g, '(value)')
  .replace(/categories: CategoryVO\[]/g, 'categories')
  .replace(/categoryId: number \| string \| null \| undefined/g, 'categoryId')
  .replace(/\): ProductRoom \| ''/g, ')')
  .replace(/const categoriesById = new Map<number, CategoryVO>\(\)/g, 'const categoriesById = new Map()')
  .replace(/const visited = new Set<number>\(\)/g, 'const visited = new Set()')
  .replace(/\(room: ProductRoom \| ''\)/g, '(room)')
  .replace(/: ProductTypeOption\[]/g, '')
  .replace(/\(room: ProductRoom \| '', productType: string\): boolean/g, '(room, productType)')
  .replace(/\(room: ProductRoom \| '', productType: string\): string/g, '(room, productType)')

const loadOptions = new Function(
  `${transpiled}; return { ROOM_PRODUCT_TYPE_OPTIONS, resolveProductRoom, getProductTypeOptions, isProductTypeValid, migrateProductType }`
)
const {
  ROOM_PRODUCT_TYPE_OPTIONS,
  resolveProductRoom,
  getProductTypeOptions,
  isProductTypeValid,
  migrateProductType
} = loadOptions()

assert.deepEqual(ROOM_PRODUCT_TYPE_OPTIONS, {
  diningRoom: [
    { label: 'DING CHAIRS', value: 'dining-chair' },
    { label: 'BAR STOOLS', value: 'bar-stool' },
    { label: 'DING TABLES', value: 'dining-table' }
  ],
  livingRoom: [
    { label: 'Sofa & Occasional Chair', value: 'sofa' },
    { label: 'Side Table & Coffee Table', value: 'coffee-table' },
    { label: 'Bookcase & Display Cabinet', value: 'bookcase' },
    { label: 'Console Table & Buffet', value: 'media-console' }
  ],
  bedroom: [
    { label: 'Bed & Headboard', value: 'bed' },
    { label: 'Bedside Table', value: 'nightstand' },
    { label: 'Chest of Drawer', value: 'dresser' },
    { label: 'Bench', value: 'bench' },
    { label: 'Dressing Table', value: 'dressing-table' },
    { label: 'Wadrobe', value: 'wardrobe' }
  ]
})

const categories = [
  { id: 10, parentId: 0, name: 'Dining Room Furniture' },
  { id: 20, parentId: 0, name: 'Living Room Furniture' },
  { id: 30, parentId: 0, name: 'Bedroom Furniture' },
  { id: 31, parentId: 30, name: 'Beds' }
]
assert.equal(resolveProductRoom(categories, 10), 'diningRoom')
assert.equal(resolveProductRoom(categories, '20'), 'livingRoom')
assert.equal(resolveProductRoom(categories, 31), 'bedroom')
assert.equal(resolveProductRoom(categories, 999), '')

assert.deepEqual(
  getProductTypeOptions('bedroom').map(({ value }) => value),
  ['bed', 'nightstand', 'dresser', 'bench', 'dressing-table', 'wardrobe']
)
assert.equal(isProductTypeValid('livingRoom', 'bookcase'), true)
assert.equal(isProductTypeValid('livingRoom', 'bed'), false)
assert.equal(migrateProductType('bedroom', 'Bedside Table'), 'nightstand')
assert.equal(migrateProductType('bedroom', 'bed-bench'), 'bench')
assert.equal(migrateProductType('livingRoom', 'side-table'), 'coffee-table')
assert.equal(migrateProductType('bedroom', 'Bedroom Furniture'), '')
assert.equal(migrateProductType('bedroom', 'bookcase'), '')

console.log('Product type room options contract passed.')
