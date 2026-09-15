import request from '@/config/axios'
import type { DashboardQuery, DashboardSummary, DashboardTrendItem } from './dashboard'

export const WebsiteTrafficApi = {
  getSummary: (params: DashboardQuery): Promise<Partial<DashboardSummary>> =>
    request.get({ url: '/statistics/website/summary', params }),
  getTrend: (params: DashboardQuery): Promise<DashboardTrendItem[]> =>
    request.get({ url: '/statistics/website/trend', params })
}
