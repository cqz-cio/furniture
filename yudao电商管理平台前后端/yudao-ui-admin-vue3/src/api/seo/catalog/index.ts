import request from '@/config/axios'

export interface CatalogSpec {
  code: string
  description: string
  capacityOz?: number
  rimMm?: number
  dimensions?: string
  packaging?: string
  cartonCbm?: string
}
export interface CatalogProduct {
  slug: string
  name: string
  category: string
  subcategory: string
  material: string
  type: string
  image: string
  description: string
  sourceUrl: string
  sourceDate: string
  specs: CatalogSpec[]
  notes: string[]
}
export interface CatalogContent {
  schemaVersion: number
  products: CatalogProduct[]
}
export interface CatalogDraft {
  siteId: number
  pageKey: 'catalog'
  locale: 'en'
  version: number
  publishedVersion?: number
  revisionId?: number
  content: CatalogContent
}
const key = { siteId: 1, pageKey: 'catalog', locale: 'en' }
export const getCatalog = () => request.get<CatalogDraft>({ url: '/seo/page/draft', params: key })
export const saveCatalog = (version: number, content: CatalogContent) =>
  request.put<CatalogDraft>({
    url: '/seo/page/draft',
    data: { ...key, expectedVersion: version, content }
  })
export const publishCatalog = (version: number) =>
  request.post<CatalogDraft>({
    url: '/seo/page/publish',
    data: { ...key, expectedVersion: version }
  })
export const previewCatalog = (version: number) =>
  request.post<{ previewUrl: string; expiresInSeconds: number }>({
    url: '/seo/page/preview-ticket',
    data: { ...key, expectedVersion: version }
  })
export const getCatalogHistory = () =>
  request.get<CatalogDraft[]>({ url: '/seo/page/history', params: key })
export const restoreCatalog = (version: number, revisionId: number) =>
  request.post<CatalogDraft>({
    url: '/seo/page/restore-draft',
    data: { ...key, expectedVersion: version, revisionId }
  })
