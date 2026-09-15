import request from '@/config/axios'
export interface WebsiteMedia {
  id: number; name: string; alt: string; kind: 'image' | 'document'; mimeType: string
  size: number; url: string; width?: number; height?: number; archived: boolean; createTime: string
}
export interface MediaQuery { pageNo: number; pageSize: number; name?: string; kind?: string; archived?: boolean }
export const getMediaPage = (params: MediaQuery) => request.get<{ list: WebsiteMedia[]; total: number }>({ url: '/seo/media/page', params })
export async function uploadMedia(file: File) {
  const data = new FormData(); data.append('file', file)
  const result = await request.upload<{ data: WebsiteMedia }>({ url: '/seo/media/upload', data, timeout: 60000 })
  return result.data
}
export const updateMedia = (data: Pick<WebsiteMedia, 'id' | 'name' | 'alt'>) => request.put({ url: '/seo/media/update', data })
export const archiveMedia = (id: number, archived: boolean) => request.put({ url: '/seo/media/archive', params: { id, archived } })
