import request from '@/config/axios'

export interface PageKey { siteId: number; pageKey: 'home'; locale: 'zh-CN' | 'en' }
export interface PageVersion extends PageKey { expectedVersion: number }
export interface HomeContent {
  schemaVersion: 1
  modules: { hero: { title: string; subtitle: string; body: string; image: { url: string; alt: string } } }
}
export interface PageDraft extends PageKey { version: number; publishedVersion?: number; content: HomeContent }
export interface PageSchema {
  schemaVersion: number
  name: string
  fields: { path: string; label: string; type: string; maxLength: number; required?: boolean }[]
}
const base = '/seo/page'
export const getSchema = (params: PageKey) => request.get<PageSchema>({ url: base + '/schema', params })
export const getDraft = (params: PageKey) => request.get<PageDraft>({ url: base + '/draft', params })
export const initialize = (data: PageKey) => request.post<PageDraft>({ url: base + '/initialize', data })
export const saveDraft = (data: PageVersion & { content: HomeContent }) => request.put<PageDraft>({ url: base + '/draft', data })
export const publish = (data: PageVersion) => request.post<PageDraft>({ url: base + '/publish', data })
export const preview = (data: PageVersion) => request.post<{ previewUrl: string; expiresIn: number }>({ url: base + '/preview-ticket', data })
export const getHistory = (params: PageKey) => request.get<PageDraft[]>({ url: base + '/history', params })
