import request from '@/config/axios'

export type SeoEntityType = 'PRODUCT' | 'CATEGORY' | 'ARTICLE' | 'PAGE'
export type SeoPublishStatus = 'DRAFT' | 'PUBLISHED'

export interface SeoMetadataCreateReqVO {
  siteId: number
  entityType: SeoEntityType
  entityId: number
  locale: string
  seoTitle: string
  metaDescription: string
  focusKeyphrase: string
  relatedKeyphrases: string[]
  canonicalUrl: string
  robotsIndex: boolean
  robotsFollow: boolean
  ogTitle: string
  ogDescription: string
  ogImage: string
  schemaType: string
}

export interface SeoMetadataUpdateReqVO extends SeoMetadataCreateReqVO {
  id: number
  version: number
}

export interface SeoMetadataRespVO extends SeoMetadataUpdateReqVO {
  publishStatus: SeoPublishStatus
  publishedTime?: string
  latestAnalysisId?: number
  createTime: string
  updateTime: string
}

export interface SeoMetadataPageReqVO {
  pageNo: number
  pageSize: number
  siteId?: number
  entityType?: SeoEntityType
  entityId?: number
  locale?: string
  publishStatus?: SeoPublishStatus
  keyword?: string
}

export interface SeoMetadataPageResult {
  list: SeoMetadataRespVO[]
  total: number
}

export const getSeoMetadataPage = async (params: SeoMetadataPageReqVO) => {
  return request.get<SeoMetadataPageResult>({ url: '/seo/metadata/page', params })
}

export const getSeoMetadata = async (id: number) => {
  return request.get<SeoMetadataRespVO>({ url: '/seo/metadata/get', params: { id } })
}

export const createSeoMetadata = async (data: SeoMetadataCreateReqVO) => {
  return request.post<number>({ url: '/seo/metadata/create', data })
}

export const updateSeoMetadata = async (data: SeoMetadataUpdateReqVO) => {
  return request.put<boolean>({ url: '/seo/metadata/update', data })
}

export const deleteSeoMetadata = async (id: number) => {
  return request.delete<boolean>({ url: '/seo/metadata/delete', params: { id } })
}

export const publishSeoMetadata = async (id: number, version: number) => {
  return request.put<boolean>({ url: '/seo/metadata/publish', params: { id, version } })
}
