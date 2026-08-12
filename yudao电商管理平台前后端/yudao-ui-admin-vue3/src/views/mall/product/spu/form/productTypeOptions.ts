import type { CategoryVO } from '@/api/mall/product/category'

export type ProductRoom = 'diningRoom' | 'livingRoom' | 'bedroom'

export type ProductTypeOption = {
  label: string
  value: string
}

// Labels are the P1 catalogue tabs verbatim. Values are the stable productType
// keys already understood by the public website's product presentation layer.
export const ROOM_PRODUCT_TYPE_OPTIONS: Record<ProductRoom, ProductTypeOption[]> = {
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
}

const ROOM_CATEGORY_NAMES: Record<string, ProductRoom> = {
  'dining room': 'diningRoom',
  'dining room furniture': 'diningRoom',
  'living room': 'livingRoom',
  'living room furniture': 'livingRoom',
  bedroom: 'bedroom',
  'bedroom furniture': 'bedroom'
}

const normalizeCategoryName = (value: string) => value.trim().toLocaleLowerCase().replace(/\s+/g, ' ')

/** Resolve the P1 room from the selected category or one of its ancestors. */
export const resolveProductRoom = (
  categories: CategoryVO[],
  categoryId: number | string | null | undefined
): ProductRoom | '' => {
  const normalizedCategoryId = Number(categoryId)
  if (!Number.isFinite(normalizedCategoryId)) return ''

  const categoriesById = new Map<number, CategoryVO>()
  categories.forEach((category) => {
    const id = Number(category.id)
    if (Number.isFinite(id)) categoriesById.set(id, category)
  })

  const visited = new Set<number>()
  let category = categoriesById.get(normalizedCategoryId)
  while (category) {
    const room = ROOM_CATEGORY_NAMES[normalizeCategoryName(category.name)]
    if (room) return room

    const currentId = Number(category.id)
    if (Number.isFinite(currentId)) {
      if (visited.has(currentId)) return ''
      visited.add(currentId)
    }

    const parentId = Number(category.parentId)
    if (!Number.isFinite(parentId) || parentId <= 0 || visited.has(parentId)) return ''
    category = categoriesById.get(parentId)
  }

  return ''
}

export const getProductTypeOptions = (room: ProductRoom | ''): ProductTypeOption[] =>
  room ? ROOM_PRODUCT_TYPE_OPTIONS[room] : []

export const isProductTypeValid = (room: ProductRoom | '', productType: string): boolean =>
  getProductTypeOptions(room).some((option) => option.value === productType)

const LEGACY_PRODUCT_TYPE_ALIASES: Record<string, string> = {
  chair: 'dining-chair',
  'dining chairs': 'dining-chair',
  'bar stools': 'bar-stool',
  'bar counter stools': 'bar-stool',
  'bar and counter stools': 'bar-stool',
  'dining tables': 'dining-table',
  'round table': 'dining-table',
  'round tables': 'dining-table',
  'single sofa': 'sofa',
  'single sofas': 'sofa',
  'lounge chair': 'sofa',
  'lounge chairs': 'sofa',
  armchair: 'sofa',
  'occasional chair': 'sofa',
  'side table': 'coffee-table',
  'side tables': 'coffee-table',
  'coffee tables': 'coffee-table',
  cabinet: 'bookcase',
  'display cabinet': 'bookcase',
  console: 'media-console',
  'console table': 'media-console',
  buffet: 'media-console',
  sideboard: 'media-console',
  beds: 'bed',
  headboard: 'bed',
  benches: 'bench',
  'bed bench': 'bench',
  ottoman: 'bench',
  nightstands: 'nightstand',
  'bedside table': 'nightstand',
  dressers: 'dresser',
  'chest of drawer': 'dresser',
  'chest of drawers': 'dresser',
  vanity: 'dressing-table',
  vanities: 'dressing-table',
  wardrobe: 'wardrobe',
  wardrobes: 'wardrobe',
  wadrobe: 'wardrobe'
}

const normalizeProductType = (value: string) =>
  value.trim().toLocaleLowerCase().replace(/[_-]+/g, ' ').replace(/\s+/g, ' ')

/** Convert an older granular value to the matching P1 tab without guessing a default. */
export const migrateProductType = (room: ProductRoom | '', productType: string): string => {
  const value = productType.trim()
  if (!value || !room) return ''
  if (isProductTypeValid(room, value)) return value

  const normalized = normalizeProductType(value)
  const directOption = getProductTypeOptions(room).find(
    (option) => normalizeProductType(option.label) === normalized
  )
  const migrated = directOption?.value || LEGACY_PRODUCT_TYPE_ALIASES[normalized] || ''
  return isProductTypeValid(room, migrated) ? migrated : ''
}
