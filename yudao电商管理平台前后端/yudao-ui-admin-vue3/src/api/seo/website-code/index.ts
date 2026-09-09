import request from '@/config/axios'

export type CodePosition = 'header' | 'body' | 'footer'
export interface CodeSection { enabled: boolean; code: string }
export type WebsiteCodeContent = Record<CodePosition, CodeSection>
export interface WebsiteCodeDraft {
  siteId: number
  siteName: string
  siteUrl: string
  version: number
  publishedVersion?: number
  publishedTime?: string
  updateTime?: string
  content: WebsiteCodeContent
}
export interface WebsiteCodeHistory {
  id: number
  version: number
  action: 'SAVE' | 'PUBLISH' | 'RESTORE'
  creator: string
  createTime: string
  content: WebsiteCodeContent
}
export const getDraft = (siteId: number) =>
  request.get<WebsiteCodeDraft>({ url: '/seo/website-code/draft', params: { siteId } })
export const saveDraft = (data: { siteId: number; version: number; content: WebsiteCodeContent }) =>
  request.put<WebsiteCodeDraft>({ url: '/seo/website-code/draft', data })
export const publish = (data: { siteId: number; version: number }) =>
  request.post<WebsiteCodeDraft>({ url: '/seo/website-code/publish', data })
export const getHistory = (siteId: number) =>
  request.get<WebsiteCodeHistory[]>({ url: '/seo/website-code/history', params: { siteId } })
export const restore = (data: { siteId: number; version: number; historyId: number }) =>
  request.post<WebsiteCodeDraft>({ url: '/seo/website-code/restore-draft', data })
