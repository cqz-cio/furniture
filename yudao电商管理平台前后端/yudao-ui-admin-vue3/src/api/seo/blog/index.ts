import request from '@/config/axios'
import { getSeoSiteConfig } from '@/api/seo/siteConfig'
import { createSitePreview } from '@/api/seo/site-preview'
import { hasPermission } from '@/directives/permission/hasPermi'

export type WebsiteBlogStatus = 'DRAFT' | 'PUBLISHED' | 'OFFLINE'

export interface WebsiteBlogSection {
  id?: string
  number?: string
  title: string
  paragraphs: string[]
}

export interface WebsiteBlogArticle {
  id?: number
  siteId: number
  locale: string
  slug: string
  legacyPath: string
  title: string
  titleLines: string[]
  category: string
  label: string
  summary: string
  coverImageUrl: string
  coverImageAlt: string
  heroImageUrl: string
  sections: WebsiteBlogSection[]
  status: WebsiteBlogStatus
  visible: boolean
  publishedAt?: string
  sortOrder: number
  seoTitle: string
  seoDescription: string
  version?: number
  publishedVersion?: number
  hasUnpublishedChanges?: boolean
  readTime?: string
  lastPublishedTime?: string
  publishedBy?: string
  createTime?: string
  updateTime?: string
}

export interface WebsiteBlogArticleSaveReqVO {
  id?: number
  version?: number
  siteId: number
  locale: string
  slug: string
  legacyPath: string
  title: string
  titleLines: string[]
  category: string
  label: string
  summary: string
  coverImageUrl: string
  coverImageAlt: string
  heroImageUrl: string
  sections: WebsiteBlogSection[]
  visible: boolean
  publishedAt?: string
  sortOrder: number
  seoTitle: string
  seoDescription: string
}

export interface WebsiteBlogPageReqVO {
  pageNo: number
  pageSize: number
  siteId: number
  locale: string
  keyword?: string
  status?: WebsiteBlogStatus
}

export interface WebsiteBlogPageResult {
  list: WebsiteBlogArticle[]
  total: number
}

export interface WebsiteBlogSummary {
  total: number
  draft: number
  published: number
  offline: number
}

export interface WebsiteBlogPublishRecord {
  id: number
  publishedVersion: number
  slug: string
  title: string
  publishedAt: string
  publishedBy: string
  createTime: string
}

export interface WebsiteBlogPreviewTicket {
  previewUrl: string
  expiresInSeconds: number
}

export const getWebsiteBlogPage = (params: WebsiteBlogPageReqVO) =>
  request.get<WebsiteBlogPageResult>({ url: '/seo/blog/page', params })

export const getWebsiteBlogSummary = (siteId: number, locale: string) =>
  request.get<WebsiteBlogSummary>({ url: '/seo/blog/summary', params: { siteId, locale } })

export const getWebsiteBlogArticle = (id: number) =>
  request.get<WebsiteBlogArticle>({ url: '/seo/blog/get', params: { id } })

export const createWebsiteBlogArticle = (data: WebsiteBlogArticleSaveReqVO) =>
  request.post<number>({ url: '/seo/blog/create', data })

export const updateWebsiteBlogArticle = (data: WebsiteBlogArticleSaveReqVO) =>
  request.put<boolean>({ url: '/seo/blog/update', data })

export const deleteWebsiteBlogArticle = (id: number) =>
  request.delete<boolean>({ url: '/seo/blog/delete', params: { id } })

export const publishWebsiteBlogArticle = (id: number, version: number) =>
  request.post<boolean>({ url: '/seo/blog/publish', data: { id, version } })

export const offlineWebsiteBlogArticle = (id: number, version: number) =>
  request.post<boolean>({ url: '/seo/blog/offline', data: { id, version } })

export const getWebsiteBlogPublishHistory = (articleId: number) =>
  request.get<WebsiteBlogPublishRecord[]>({ url: '/seo/blog/history', params: { articleId } })

export const createWebsiteBlogPreviewTicket = async (id: number, version: number) => {
  if (hasPermission(['seo:page:preview'])) {
    const article = await getWebsiteBlogArticle(id)
    const config = await getSeoSiteConfig(article.siteId)
    if (config?.navigationTemplate === 'TRIPEER_CORPORATE') {
      const result = await createSitePreview({ siteId: article.siteId, locale: article.locale, articleId: id, articleVersion: version })
      return { previewUrl: result.previewUrl, expiresInSeconds: result.expiresIn }
    }
  }
  return request.post<WebsiteBlogPreviewTicket>({
    url: '/seo/blog/preview-ticket',
    data: { id, version }
  })

}
export const restoreWebsiteBlogDraft = (id: number, version: number, recordId: number) => request.post<boolean>({ url: '/seo/blog/restore-draft', data: { id, version, recordId } })
