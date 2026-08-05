import request from '@/config/axios'

export type WebsiteNavigationTemplate = 'VANZ_B2B' | 'OAKVED_B2C'
export type WebsiteNavigationItemType = 'PAGE' | 'CATEGORY' | 'DIRECTORY' | 'ROUTE' | 'FILTER'

export interface WebsiteNavigationItemRespVO {
  itemKey: string
  parentItemKey: string
  itemType: WebsiteNavigationItemType
  pageKey?: string
  targetKey?: string
  categoryId?: number
  label: string
  sort: number
  visible: boolean
  openMode: '_self' | '_blank'
  styleVariant: 'DEFAULT' | 'SALE'
  available: boolean
  publishedProductCount?: number
  children?: WebsiteNavigationItemRespVO[]
}

export interface WebsiteNavigationTargetOptionRespVO {
  targetKey: string
  itemType: 'ROUTE' | 'FILTER'
  label: string
  href: string
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
  navigationTemplate: WebsiteNavigationTemplate
  revisionNo: number
  version: number
  status: 'DRAFT'
  publishedVersion?: number
  publishedRevisionNo?: number
  lastPublishedTime?: string
  lastPublishedBy?: string
  items: WebsiteNavigationItemRespVO[]
  publishedItems: WebsiteNavigationItemRespVO[]
  categoryOptions: WebsiteNavigationCategoryOptionRespVO[]
  targetOptions: WebsiteNavigationTargetOptionRespVO[]
}

export interface WebsiteNavigationItemSaveReqVO {
  itemKey: string
  parentItemKey: string
  itemType: WebsiteNavigationItemType
  pageKey?: string
  targetKey?: string
  categoryId?: number
  label: string
  sort: number
  visible: boolean
  openMode: '_self' | '_blank'
  styleVariant: 'DEFAULT' | 'SALE'
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

export interface WebsiteNavigationRevisionRespVO {
  revisionId: number
  revisionNo: number
  version: number
  status: 'PUBLISHED' | 'ARCHIVED'
  publishedTime?: string
  publishedBy?: string
  updateTime: string
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

export const getWebsiteNavigationHistory = (siteId: number, locale: string) =>
  request.get<WebsiteNavigationRevisionRespVO[]>({
    url: '/seo/navigation/history',
    params: { siteId, locale }
  })

export const restoreWebsiteNavigationDraft = (
  draftRevisionId: number,
  draftVersion: number,
  sourceRevisionId: number
) =>
  request.post<boolean>({
    url: '/seo/navigation/restore-draft',
    data: { draftRevisionId, draftVersion, sourceRevisionId }
  })

export const createWebsiteNavigationPreviewTicket = (revisionId: number, version: number) =>
  request.post<WebsiteNavigationPreviewTicketRespVO>({
    url: '/seo/navigation/preview-ticket',
    data: { revisionId, version }
  })
