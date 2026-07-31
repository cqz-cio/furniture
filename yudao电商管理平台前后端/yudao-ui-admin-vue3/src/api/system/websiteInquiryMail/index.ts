import request from '@/config/axios'

export enum WebsiteInquiryMailDeliveryStatus {
  PENDING = 0,
  SENDING = 10,
  SUCCESS = 20,
  FAILURE = 30,
  CONFIG_REQUIRED = 40
}

export interface WebsiteInquiryMailConfigVO {
  configured: boolean
  enabled: boolean
  recipientEmail?: string
  mailAccountId?: number
  senderEmail?: string
  senderName: string
  subjectTemplate: string
  contentTemplate: string
  erpBaseUrl?: string
  availableVariables: string[]
  updateTime?: Date
}

export interface WebsiteInquiryMailConfigSaveVO {
  enabled: boolean
  recipientEmail?: string
  mailAccountId?: number
  senderName: string
  subjectTemplate: string
  contentTemplate: string
  erpBaseUrl?: string
}

export interface WebsiteInquiryMailDeliveryVO {
  id: number
  inquiryId: number
  recipientEmail?: string
  customerEmail?: string
  status: WebsiteInquiryMailDeliveryStatus
  attemptCount: number
  mailLogId?: number
  nextRetryTime?: Date
  sentTime?: Date
  lastError?: string
  updateTime?: Date
}

export const getWebsiteInquiryMailConfig = async () => {
  return await request.get<WebsiteInquiryMailConfigVO>({
    url: '/system/website-inquiry-mail/config'
  })
}

export const saveWebsiteInquiryMailConfig = async (data: WebsiteInquiryMailConfigSaveVO) => {
  return await request.put({ url: '/system/website-inquiry-mail/config', data })
}

export const sendWebsiteInquiryTestMail = async () => {
  return await request.post<number>({ url: '/system/website-inquiry-mail/test' })
}

export const getWebsiteInquiryMailDelivery = async (inquiryId: number) => {
  return await request.get<WebsiteInquiryMailDeliveryVO | null>({
    url: '/system/website-inquiry-mail/delivery',
    params: { inquiryId }
  })
}

export const resendWebsiteInquiryMail = async (inquiryId: number) => {
  return await request.post({
    url: '/system/website-inquiry-mail/resend',
    params: { inquiryId }
  })
}
