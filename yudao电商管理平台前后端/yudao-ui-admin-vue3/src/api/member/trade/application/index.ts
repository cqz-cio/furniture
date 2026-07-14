import request from '@/config/axios'

export interface TradeApplicationUserVO {
  firstName?: string
  lastName?: string
  title?: string
  phone?: string
  email?: string
  confirmEmail?: string
}

export interface TradeApplicationAttachmentVO {
  name?: string
  url?: string
}

export interface TradeApplicationVO {
  id: number
  businessName?: string
  country?: string
  street?: string
  address2?: string
  city?: string
  state?: string
  postalCode?: string
  businessDescription?: string
  website?: string
  portfolio?: string
  instagram?: string
  pinterest?: string
  houzz?: string
  linkedin?: string
  primaryEmail?: string
  authorizedUsers?: TradeApplicationUserVO[]
  businessDocuments?: TradeApplicationAttachmentVO[]
  taxDocuments?: TradeApplicationAttachmentVO[]
  emailOptIn?: boolean
  status: number
  tradeId?: string
  reviewReason?: string
  reviewTime?: string
  reviewerId?: number
  createTime?: string
}

export interface TradeApplicationPageReqVO {
  pageNo: number
  pageSize: number
  status?: number
  primaryEmail?: string
  businessName?: string
}

export interface TradeApplicationReviewReqVO {
  id: number
  tradeId?: string
  reviewReason?: string
}

export const getTradeApplicationPage = async (params: TradeApplicationPageReqVO) => {
  return await request.get({ url: '/member/trade-application/page', params })
}

export const getTradeApplication = async (id: number) => {
  return await request.get({ url: `/member/trade-application/get?id=${id}` })
}

export const approveTradeApplication = async (data: TradeApplicationReviewReqVO) => {
  return await request.put({ url: '/member/trade-application/approve', data })
}

export const rejectTradeApplication = async (data: TradeApplicationReviewReqVO) => {
  return await request.put({ url: '/member/trade-application/reject', data })
}
