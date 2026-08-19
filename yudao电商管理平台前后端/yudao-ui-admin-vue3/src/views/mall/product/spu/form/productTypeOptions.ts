import type { CategoryVO } from '@/api/mall/product/category'

export type ProductCategoryOption = {
  label: string
  value: number
  code: string
  parentId: number
}

type CategoryNode = CategoryVO & { children?: CategoryNode[] }

const PRODUCT_ROOM_CODES = new Set(['dining-room', 'living-room', 'bedroom'])
const LEGACY_PRODUCT_TYPE_ALIASES: Record<string, string> = {
  'bed-bench': 'bench',
  vanity: 'dressing-table',
  'round-table': 'dining-table',
  'single-sofa': 'sofa'
}

const flattenCategories = (categories: CategoryNode[]): CategoryVO[] => {
  const flattened: CategoryVO[] = []
  const visit = (category: CategoryNode) => {
    flattened.push(category)
    category.children?.forEach(visit)
  }
  categories.forEach(visit)
  return flattened
}

const isEnabled = (category: CategoryVO) => category.status === undefined || category.status === 0
const categoryOrder = (left: CategoryVO, right: CategoryVO) =>
  Number(left.sort || 0) - Number(right.sort || 0) || Number(left.id || 0) - Number(right.id || 0)

export const getProductRoomOptions = (categories: CategoryNode[]): ProductCategoryOption[] =>
  flattenCategories(categories)
    .filter(
      (category) =>
        Number(category.parentId) === 0 &&
        PRODUCT_ROOM_CODES.has(category.code || '') &&
        isEnabled(category)
    )
    .sort(categoryOrder)
    .map((category) => ({
      label: category.name,
      value: Number(category.id),
      code: category.code || '',
      parentId: 0
    }))

/** Resolve a selected P1/P2 category to its stable-code P1 Room without name matching. */
export const resolveProductRoom = (
  categories: CategoryNode[],
  categoryId: number | string | null | undefined
): CategoryVO | undefined => {
  const normalizedId = Number(categoryId)
  if (!Number.isFinite(normalizedId)) return undefined

  const flat = flattenCategories(categories)
  const byId = new Map(flat.map((category) => [Number(category.id), category]))
  const visited = new Set<number>()
  let category = byId.get(normalizedId)
  while (category) {
    const id = Number(category.id)
    if (visited.has(id)) return undefined
    visited.add(id)
    if (Number(category.parentId) === 0 && PRODUCT_ROOM_CODES.has(category.code || '')) {
      return category
    }
    const parentId = Number(category.parentId)
    if (!Number.isFinite(parentId) || parentId <= 0) return undefined
    category = byId.get(parentId)
  }
  return undefined
}

export const getProductTypeOptions = (
  categories: CategoryNode[],
  roomCategoryId: number | string | null | undefined
): ProductCategoryOption[] => {
  const roomId = Number(roomCategoryId)
  if (!Number.isFinite(roomId)) return []
  return flattenCategories(categories)
    .filter(
      (category) =>
        Number(category.parentId) === roomId &&
        Boolean(category.code) &&
        isEnabled(category)
    )
    .sort(categoryOrder)
    .map((category) => ({
      label: category.name,
      value: Number(category.id),
      code: category.code || '',
      parentId: roomId
    }))
}

export const isProductTypeSelectionValid = (
  categories: CategoryNode[],
  roomCategoryId: number | string | null | undefined,
  categoryId: number | string | null | undefined
): boolean => {
  const selectedId = Number(categoryId)
  return Number.isFinite(selectedId) &&
    getProductTypeOptions(categories, roomCategoryId).some((option) => option.value === selectedId)
}

/** One-release read compatibility for deterministic legacy JSON values. */
export const migrateProductType = (
  categories: CategoryNode[],
  roomCategoryId: number | string | null | undefined,
  legacyProductType: string
): number | undefined => {
  const normalized = String(legacyProductType || '').trim().toLocaleLowerCase()
  if (!normalized) return undefined
  const targetCode = LEGACY_PRODUCT_TYPE_ALIASES[normalized] || normalized
  return getProductTypeOptions(categories, roomCategoryId).find(
    (option) => option.code === targetCode
  )?.value
}
