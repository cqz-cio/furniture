import request from '@/config/axios'
import { TransferReqVO } from '@/api/crm/permission'

export interface ClueVO {
  id: number // 编号
  name: string // 线索名称
  followUpStatus: boolean // 跟进状态
  contactLastTime: Date // 最后跟进时间
  contactLastContent: string // 最后跟进内容
  contactNextTime: Date // 下次联系时间
  ownerUserId: number // 负责人的用户编号
  ownerUserName?: string // 负责人的用户名称
  ownerUserDept?: string // 负责人的部门名称
  transformStatus: boolean // 转化状态
  customerId: number // 客户编号
  customerName?: string // 客户名称
  contactId?: number // 联系人编号
  externalInquiryId?: string // 外部询盘编号
  contactName: string // 网页联系人姓名
  companyName: string // 网页公司名称
  countryCode: string // 国家或地区电话区号
  inquirySubject: string // 询盘主题
  inquiryMessage: string // 询盘原始内容
  sourcePage: string // 提交页面
  locale: string // 浏览器语言
  utmSource: string // UTM 来源
  utmMedium: string // UTM 媒介
  utmCampaign: string // UTM 活动
  submittedAt: Date // 网页提交时间
  processStatus: InquiryProcessStatus // 处理状态
  processedAt?: Date // 处理完成时间
  mobile: string // 手机号
  telephone: string // 电话
  qq: string // QQ
  wechat: string // wechat
  email: string // email
  areaId: number // 所在地
  areaName?: string // 所在地名称
  detailAddress: string // 详细地址
  industryId: number // 所属行业
  level: number // 客户等级
  source: number // 客户来源
  remark: string // 备注
  creator: string // 创建人
  creatorName?: string // 创建人名称
  createTime: Date // 创建时间
  updateTime: Date // 更新时间
}

export enum InquiryProcessStatus {
  PENDING = 0,
  PROCESSING = 10,
  PROCESSED = 20,
  INVALID = 30
}

export interface InquirySummaryVO {
  totalCount: number
  pendingCount: number
  processingCount: number
  processedCount: number
  invalidCount: number
}

export interface InquiryProcessStatusUpdateReqVO {
  id: number
  processStatus: InquiryProcessStatus
  remark?: string
}

export interface ClueTransformResultVO {
  customerId: number
  contactId: number
  customerCreated: boolean
  contactCreated: boolean
}

// 查询线索列表
export const getCluePage = async (params: any) => {
  return await request.get({ url: `/crm/clue/page`, params })
}

// 查询线索详情
export const getClue = async (id: number, options: { hideErrorMessage?: boolean } = {}) => {
  return await request.get({
    url: `/crm/clue/get?id=` + id,
    headers: options.hideErrorMessage ? { hideErrorMessage: true } : undefined
  })
}

// 新增线索
export const createClue = async (data: ClueVO) => {
  return await request.post({ url: `/crm/clue/create`, data })
}

// 修改线索
export const updateClue = async (data: ClueVO) => {
  return await request.put({ url: `/crm/clue/update`, data })
}

// 删除线索
export const deleteClue = async (id: number) => {
  return await request.delete({ url: `/crm/clue/delete?id=` + id })
}

// 导出线索 Excel
export const exportClue = async (params) => {
  return await request.download({ url: `/crm/clue/export-excel`, params })
}

// 查询询盘处理状态汇总
export const getInquirySummary = async (): Promise<InquirySummaryVO> => {
  return await request.get({ url: '/crm/clue/summary' })
}

// 更新询盘处理状态
export const updateInquiryProcessStatus = async (data: InquiryProcessStatusUpdateReqVO) => {
  return await request.put({ url: '/crm/clue/process-status', data })
}

// 线索转移
export const transferClue = async (data: TransferReqVO) => {
  return await request.put({ url: '/crm/clue/transfer', data })
}

// 线索转化为客户
export const transformClue = async (id: number): Promise<ClueTransformResultVO> => {
  return await request.put({ url: '/crm/clue/transform', params: { id } })
}

// 获得分配给我的、待跟进的线索数量
export const getFollowClueCount = async () => {
  return await request.get({ url: '/crm/clue/follow-count' })
}
