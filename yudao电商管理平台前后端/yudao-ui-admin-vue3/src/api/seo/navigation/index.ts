import request from '@/config/axios'

export type WebsiteNavigationItemType = 'PAGE' | 'CATEGORY'

export interface WebsiteNavigationItemRespVO {
  itemKey: string
  itemType: WebsiteNavigationItemType
  pageKey?: string
  categoryId?: number
  label: string
  sort: number
  visible: boolean
  available: boolean
  publishedProductCount?: number
}

export interface WebsiteNavigationCategoryOptionRespVO {
  id: number
  name: string
  sort: number
  publishedProductCount: number
  selected: boolean
}

export interface WebsiteNavigationDraftRespVO {
  revisionId: number
  siteId: number
  locale: string
  revisionNo: number
  version: number
  status: 'DRAFT'
  publishedVersion?: number
  lastPublishedTime?: string
  items: WebsiteNavigationItemRespVO[]
  categoryOptions: WebsiteNavigationCategoryOptionRespVO[]
}

export interface WebsiteNavigationItemSaveReqVO {
  itemType: WebsiteNavigationItemType
  pageKey?: string
  categoryId?: number
  label: string
  sort: number
  visible: boolean
}

export interface WebsiteNavigationDraftSaveReqVO {
  revisionId: number
  siteId: number
  locale: string
  version: number
  items: WebsiteNavigationItemSaveReqVO[]
}

export interface WebsiteNavigationPreviewTicketRespVO {
  previewUrl: string
  expiresInSeconds: number
}

export const getWebsiteNavigationDraft = (siteId: number, locale: string) =>
  request.get<WebsiteNavigationDraftRespVO>({
    url: '/seo/navigation/draft',
    params: { siteId, locale }
  })

export const getWebsiteNavigationCategoryOptions = (siteId: number, locale: string) =>
  request.get<WebsiteNavigationCategoryOptionRespVO[]>({
    url: '/seo/navigation/category-options',
    params: { siteId, locale }
  })

export const saveWebsiteNavigationDraft = (data: WebsiteNavigationDraftSaveReqVO) =>
  request.put<boolean>({ url: '/seo/navigation/draft', data })

export const publishWebsiteNavigation = (revisionId: number, version: number) =>
  request.post<boolean>({
    url: '/seo/navigation/publish',
    data: { revisionId, version }
  })

export const createWebsiteNavigationPreviewTicket = (revisionId: number, version: number) =>
  request.post<WebsiteNavigationPreviewTicketRespVO>({
    url: '/seo/navigation/preview-ticket',
    data: { revisionId, version }
  })
