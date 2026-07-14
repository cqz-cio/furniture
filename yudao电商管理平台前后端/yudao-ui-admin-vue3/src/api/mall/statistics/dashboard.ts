import request from '@/config/axios'

export type DashboardScope = 'SITE' | 'PRODUCT'
export type TrafficDataStatus = 'COMPLETE' | 'PARTIAL' | 'UNAVAILABLE'
export type FreshnessStatus = 'FRESH' | 'DELAYED' | 'STALE'

export interface DashboardQuery {
  scope: DashboardScope
  startDate: string
  endDate: string
  compare?: boolean
  categoryId?: number
  spuId?: number
  riskType?: string
  pageNo?: number
  pageSize?: number
  sortField?: string
  sortOrder?: 'asc' | 'desc'
}

export interface DashboardSummary {
  homePv: number | null
  homeUv: number | null
  productDetailPv: number | null
  productDetailUv: number | null
  addCartCount: number | null
  addCartUserCount: number | null
  checkoutStartCount: number | null
  paidOrderCount: number | null
  paidBuyerCount: number | null
  paidItemCount: number | null
  paidRevenue: number | null
  refundAmount: number | null
  netRevenue: number | null
  browseOrderConversionPercent: number | null
  knownCostAmount: number | null
  costAmount: number | null
  grossProfit: number | null
  grossMarginPercent: number | null
  exactCostItemCount: number | null
  estimatedCostItemCount: number | null
  missingCostItemCount: number | null
  trafficDataStatus: TrafficDataStatus
  freshnessStatus: FreshnessStatus
  trafficWatermark: string | null
  tradeWatermark: string | null
  refundWatermark: string | null
  trafficDataAvailableFrom: string | null
  comparisonStartDate: string | null
  comparisonEndDate: string | null
  asOf: string | null
  snapshotId: string
  profitVisible: boolean
  reference: DashboardSummary | null
  changes: Record<string, DashboardMetricChange> | null
}

export interface DashboardMetricChange {
  referenceValue: number | null
  changeAmount: number | null
  changePercent: number | null
  referenceRate: number | null
  changePercentagePoints: number | null
}

export interface DashboardTrendItem extends Partial<DashboardSummary> { day: string; referenceDay: string | null }
export interface DashboardStageItem { stage: string; value: number | null; unit: string; dedupeScope: string; applicability: 'APPLICABLE' | 'NOT_APPLICABLE' }
export interface DashboardStageOverview { cohortAligned: false; explanation: string; items: DashboardStageItem[] }
export interface DashboardAttentionItem { spuId: number; riskType: string; copy: string }
export interface DashboardNotEvaluated { spuId: number; riskType: string; reasonCode: string; copy: string }
export interface DashboardAttention { items: DashboardAttentionItem[]; notEvaluated: DashboardNotEvaluated[]; disclaimer: string }
export interface DashboardProduct {
  spuId: number
  categoryId: number | null
  productName: string | null
  picUrl: string | null
  browseCount: number | null
  browseUserCount: number | null
  cartCount: number | null
  orderCount: number | null
  orderPayCount: number | null
  orderPayPrice: number | null
  afterSaleCount: number | null
  afterSaleRefundPrice: number | null
  netRevenue: number | null
  browseConvertPercent: number | null
  knownCostAmount: number | null
  costAmount: number | null
  grossProfit: number | null
  grossMarginPercent: number | null
  missingCostItemCount: number | null
  trafficDataStatus: TrafficDataStatus
  profitDataQuality: string
}
export interface DashboardProductPage { list: DashboardProduct[]; total: number }

const normalize = (query: DashboardQuery) => {
  const params = { ...query }
  if (params.scope === 'SITE') {
    delete params.categoryId
    delete params.spuId
    delete params.riskType
  }
  return params
}

export const DashboardApi = {
  getSummary: (query: DashboardQuery) => request.get<DashboardSummary>({ url: '/statistics/dashboard/summary', params: normalize(query) }),
  getTrend: (query: DashboardQuery) => request.get<DashboardTrendItem[]>({ url: '/statistics/dashboard/trend', params: normalize(query) }),
  getStageOverview: (query: DashboardQuery) => request.get<DashboardStageOverview>({ url: '/statistics/dashboard/stage-overview', params: normalize(query) }),
  getAttention: (query: DashboardQuery) => request.get<DashboardAttention>({ url: '/statistics/dashboard/attention', params: normalize(query) }),
  getProductPage: (query: DashboardQuery) => request.get<DashboardProductPage>({ url: '/statistics/dashboard/product-page', params: normalize(query) }),
  exportProductExcel: (query: DashboardQuery) => request.download({ url: '/statistics/dashboard/export', params: normalize(query) }),
  exportProfitExcel: (query: DashboardQuery) => request.download({ url: '/statistics/dashboard/profit-export', params: normalize(query) })
}
