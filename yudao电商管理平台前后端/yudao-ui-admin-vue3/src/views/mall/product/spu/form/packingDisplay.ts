export type LegacyPacking = {
  method?: string
  itemQuantity?: number | null
  itemUnit?: 'pc' | 'set'
  cartonQuantity?: number | null
}

const positiveIntegerOrNull = (value: unknown): number | null => {
  const number = Number(value)
  return Number.isInteger(number) && number > 0 ? number : null
}

export const formatLegacyPacking = (value?: LegacyPacking | string): string => {
  if (typeof value === 'string') return value.trim()
  if (!value) return ''

  const method = typeof value.method === 'string' ? value.method.trim() : ''
  const itemQuantity = positiveIntegerOrNull(value.itemQuantity)
  const cartonQuantity = positiveIntegerOrNull(value.cartonQuantity)
  if (itemQuantity === null || cartonQuantity === null) return method

  const singularItemUnit = value.itemUnit === 'set' ? 'set' : 'pc'
  const itemUnit =
    itemQuantity === 1 ? singularItemUnit : singularItemUnit === 'set' ? 'sets' : 'pcs'
  if (singularItemUnit === 'pc' && itemQuantity === 1 && cartonQuantity > 1) {
    return `Ships in ${cartonQuantity} cartons`
  }
  if (cartonQuantity === 1) return `${itemQuantity} ${itemUnit}/ctn`

  return `${itemQuantity} ${itemUnit} / ${cartonQuantity} cartons`
}
