import dayjs from 'dayjs'
import {
  InquiryPriority,
  InquiryProcessStatus,
  InquirySalesStage,
  type ClueVO
} from '@/api/crm/clue'

export type InquiryTagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'

export const inquiryPriorityOptions = [
  { value: InquiryPriority.HIGH, label: '高优先级', type: 'danger' as InquiryTagType },
  { value: InquiryPriority.NORMAL, label: '普通', type: 'warning' as InquiryTagType },
  { value: InquiryPriority.LOW, label: '低优先级', type: 'info' as InquiryTagType }
]

export const inquirySalesStageOptions = [
  { value: InquirySalesStage.NEW, label: '新询盘' },
  { value: InquirySalesStage.QUALIFYING, label: '需求确认' },
  { value: InquirySalesStage.QUOTING, label: '报价中' },
  { value: InquirySalesStage.SAMPLE, label: '打样中' },
  { value: InquirySalesStage.NEGOTIATION, label: '商务谈判' },
  { value: InquirySalesStage.WON, label: '已赢单' },
  { value: InquirySalesStage.LOST, label: '已丢单' }
]

export const priorityMeta = (value?: InquiryPriority) =>
  inquiryPriorityOptions.find((item) => item.value === value) || inquiryPriorityOptions[1]

export const salesStageLabel = (value?: InquirySalesStage) =>
  inquirySalesStageOptions.find((item) => item.value === value)?.label || '新询盘'

const durationLabel = (minutes: number) => {
  const safeMinutes = Math.max(0, Math.round(minutes))
  if (safeMinutes < 60) return `${safeMinutes} 分钟`
  const hours = Math.floor(safeMinutes / 60)
  const remainingMinutes = safeMinutes % 60
  if (hours < 24) return remainingMinutes ? `${hours} 小时 ${remainingMinutes} 分` : `${hours} 小时`
  const days = Math.floor(hours / 24)
  const remainingHours = hours % 24
  return remainingHours ? `${days} 天 ${remainingHours} 小时` : `${days} 天`
}

export const inquirySlaMeta = (clue: Partial<ClueVO>) => {
  const submittedAt = clue.submittedAt || clue.createTime
  if (!submittedAt || !dayjs(submittedAt).isValid()) {
    return { label: '提交时间缺失', hint: '无法计算首响 SLA', type: 'info' as InquiryTagType }
  }
  const start = dayjs(submittedAt)
  if (clue.firstResponseAt && dayjs(clue.firstResponseAt).isValid()) {
    const minutes = dayjs(clue.firstResponseAt).diff(start, 'minute')
    return {
      label: `首响 ${durationLabel(minutes)}`,
      hint: '已记录首次开始处理时间',
      type: minutes > 24 * 60 ? ('danger' as InquiryTagType) : ('success' as InquiryTagType)
    }
  }
  if (clue.processStatus !== InquiryProcessStatus.PENDING) {
    return { label: '历史记录', hint: '上线前记录无首响时间', type: 'info' as InquiryTagType }
  }
  const minutes = dayjs().diff(start, 'minute')
  if (minutes > 24 * 60) {
    return {
      label: `超时 ${durationLabel(minutes - 24 * 60)}`,
      hint: '已超过 24 小时首次响应目标',
      type: 'danger' as InquiryTagType
    }
  }
  if (minutes > 8 * 60) {
    return {
      label: `待回复 ${durationLabel(minutes)}`,
      hint: '接近 24 小时首次响应目标',
      type: 'warning' as InquiryTagType
    }
  }
  return {
    label: `待回复 ${durationLabel(minutes)}`,
    hint: '24 小时内完成首次响应',
    type: 'primary' as InquiryTagType
  }
}

export interface QuoteInquiryItem {
  index: number
  name: string
  productReference?: string
  productId?: string
  skuId?: string
  quantity?: number
  selections?: string
  note?: string
}

export interface QuoteInquiryDetails {
  items: QuoteInquiryItem[]
  projectName?: string
  country?: string
  buyingTimeframe?: string
  additionalRequirements?: string
}

export const parseQuoteInquiry = (value?: string): QuoteInquiryDetails | null => {
  const lines = (value || '').replace(/\r\n?/g, '\n').split('\n')
  if (!/^QUOTE LIST\s*[—-]/i.test(lines[0]?.trim() || '')) return null

  const items: QuoteInquiryItem[] = []
  let current: QuoteInquiryItem | undefined
  let inProjectDetails = false
  const details: Omit<QuoteInquiryDetails, 'items'> = {}

  for (const rawLine of lines.slice(1)) {
    const line = rawLine.trim()
    if (!line) continue
    if (/^PROJECT DETAILS$/i.test(line)) {
      if (current) items.push(current)
      current = undefined
      inProjectDetails = true
      continue
    }
    if (inProjectDetails) {
      const separator = line.indexOf(':')
      if (separator < 0) continue
      const key = line.slice(0, separator).trim().toLowerCase()
      const content = line.slice(separator + 1).trim()
      if (key === 'project') details.projectName = content
      if (key === 'country/region') details.country = content
      if (key === 'buying timeframe') details.buyingTimeframe = content
      if (key === 'additional requirements') details.additionalRequirements = content
      continue
    }

    const itemMatch = line.match(/^(\d+)\.\s+(.+)$/)
    if (itemMatch) {
      if (current) items.push(current)
      current = { index: Number(itemMatch[1]), name: itemMatch[2].trim() }
      continue
    }
    if (!current) continue
    const fieldMatch = line.match(/^([^:]+):\s*(.*)$/)
    if (!fieldMatch) continue
    const key = fieldMatch[1].trim().toLowerCase()
    const content = fieldMatch[2].trim()
    if (key === 'product reference') current.productReference = content
    if (key === 'product id (quote protocol)') current.productId = content
    if (key === 'sku id (quote protocol)') current.skuId = content
    if (key === 'quantity') {
      const quantity = Number(content)
      if (Number.isFinite(quantity)) current.quantity = quantity
    }
    if (key === 'selections') current.selections = content
    if (key === 'item note') current.note = content
  }
  if (current) items.push(current)
  return items.length ? { items, ...details } : null
}
