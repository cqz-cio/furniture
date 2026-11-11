import type { CatalogContent } from '../api/seo/catalog/index'

/** An empty numeric control means “not provided”, not a null or guessed measurement. */
export function prepareCatalogContent(value: CatalogContent): CatalogContent {
  const copy: CatalogContent = JSON.parse(JSON.stringify(value))
  for (const product of copy.products)
    for (const spec of product.specs)
      for (const key of ['capacityOz', 'rimMm'] as const) {
        if (spec[key] === null) delete spec[key]
      }
  return copy
}
