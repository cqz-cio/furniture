import request from '@/config/axios'

export interface SeoSiteConfigSaveReqVO {
  siteId: number
  siteName: string
  siteUrl: string
  defaultTitleSuffix: string
  defaultDescription: string
  defaultRobots: string
  defaultOgImage: string
  defaultLocale: string
}

export interface SeoSiteConfigRespVO extends SeoSiteConfigSaveReqVO {
  id: number
  createTime: string
  updateTime: string
}

export const getSeoSiteConfig = async (siteId: number) => {
  return request.get<SeoSiteConfigRespVO | null>({
    url: '/seo/site-config/get',
    params: { siteId }
  })
}

export const saveSeoSiteConfig = async (data: SeoSiteConfigSaveReqVO) => {
  return request.put<boolean>({ url: '/seo/site-config/save', data })
}
