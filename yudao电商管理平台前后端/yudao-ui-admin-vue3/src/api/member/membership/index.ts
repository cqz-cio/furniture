import request from '@/config/axios'

export interface MemberMembershipVO {
  id: number
  userId: number
  memberId?: string
  planCode?: string
  planName?: string
  status?: string
  startedAt?: string
  expiresAt?: string
  autoRenew?: boolean
  sourceOrderId?: number
  sourcePayOrderId?: number
  createTime?: string
}

export interface MemberMembershipPageReqVO {
  pageNo: number
  pageSize: number
  userId?: number
  memberId?: string
  planCode?: string
  status?: string
}

export interface MemberMembershipOpenReqVO {
  userId: number
  sourceOrderId?: number
  sourcePayOrderId?: number
}

export interface MemberMembershipUpdateReqVO {
  id: number
  status?: string
  expiresAt?: string
  autoRenew?: boolean
}

export const getMembershipPage = async (params: MemberMembershipPageReqVO) => {
  return await request.get({ url: '/member/membership/page', params })
}

export const getMembership = async (id: number) => {
  return await request.get({ url: `/member/membership/get?id=${id}` })
}

export const openMembership = async (data: MemberMembershipOpenReqVO) => {
  return await request.post({ url: '/member/membership/open', data })
}

export const updateMembership = async (data: MemberMembershipUpdateReqVO) => {
  return await request.put({ url: '/member/membership/update', data })
}
