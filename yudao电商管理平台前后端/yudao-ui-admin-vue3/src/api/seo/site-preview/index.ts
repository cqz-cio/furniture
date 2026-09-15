import request from '@/config/axios'
export const createSitePreview = (data: { siteId: number; locale: string; pageVersion?: number; navigationVersion?: number; articleId?: number; articleVersion?: number }) =>
  request.post<{ previewUrl: string; expiresIn: number }>({ url: '/seo/site-preview/ticket', data: { ...data, pageKey: 'home' } })
