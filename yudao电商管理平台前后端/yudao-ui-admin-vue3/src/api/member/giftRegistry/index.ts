import request from '@/config/axios'

export interface GiftRegistryItemVO {
  id: number
  registryId: number
  spuId: number
  skuId: number
  productName: string
  picUrl?: string
  price?: number
  quantityRequested: number
  quantityPurchased: number
  priority?: string
  note?: string
}

export interface GiftRegistryVO {
  id: number
  userId: number
  publicCode: string
  registrantName: string
  coRegistrantName?: string
  email: string
  phone?: string
  eventType: string
  eventDate: string
  eventLocation?: string
  visibility: string
  status: string
  giftCardPreference?: boolean
  messagePreference?: boolean
  createTime?: string
  items?: GiftRegistryItemVO[]
}

export interface GiftRegistryPageReqVO {
  pageNo: number
  pageSize: number
  userId?: number
  registrantName?: string
  eventType?: string
  status?: string
  publicCode?: string
}

export interface GiftRegistryStatusUpdateReqVO {
  id: number
  status: string
}

export const getGiftRegistryPage = async (params: GiftRegistryPageReqVO) => {
  return await request.get({ url: '/member/gift-registry/page', params })
}

export const getGiftRegistry = async (id: number) => {
  return await request.get({ url: `/member/gift-registry/get?id=${id}` })
}

export const updateGiftRegistryStatus = async (data: GiftRegistryStatusUpdateReqVO) => {
  return await request.put({ url: '/member/gift-registry/status', data })
}
