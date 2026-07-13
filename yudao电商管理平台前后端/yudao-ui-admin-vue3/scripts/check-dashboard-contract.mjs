import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const config = readFileSync(new URL('../src/config/furnitureLite.ts', import.meta.url), 'utf8')
const api = readFileSync(new URL('../src/api/mall/statistics/dashboard.ts', import.meta.url), 'utf8')
const page = readFileSync(new URL('../src/views/dashboard/index.vue', import.meta.url), 'utf8')

assert.match(config, /['"]\/dashboard['"]/, 'furniture-lite must allow the dashboard root')
for (const token of [
  'DashboardScope', 'scope', 'startDate', 'endDate', 'getSummary', 'getTrend',
  'getStageOverview', 'getAttention', 'getProductPage', 'trafficDataStatus',
  'freshnessStatus', 'exactCostItemCount', 'knownCostAmount', 'profit-export',
  'DashboardProductPage', 'productName', 'categoryId', 'total'
  , 'compare', 'trafficDataAvailableFrom', 'comparisonStartDate', 'comparisonEndDate', 'referenceDay',
  'DashboardMetricChange', 'reference', 'changes'
]) {
  assert.ok(api.includes(token), `missing dashboard token: ${token}`)
}
for (const token of [
  "name: 'FurnitureDashboard'", 'statistics:dashboard:query', 'statistics:dashboard:profit-query',
  'statistics:dashboard:export', 'statistics:dashboard:profit-export', 'DashboardApi.getSummary',
  'DashboardApi.getTrend', 'DashboardApi.getStageOverview', 'DashboardApi.getAttention',
  'DashboardApi.getProductPage', '<Echart', 'trafficDataStatus', 'freshnessStatus',
  '近 7 日', '近 30 日', '运营关注', '商品经营明细', 'productTotal',
  '@sort-change', '<el-pagination', '商品名称', '分类 ID', '商品 SPU'
  , 'useRoute', 'useRouter', 'syncUrl', 'attentionQuery', 'attentionEvaluatedEmpty',
  'HIGH_TRAFFIC_LOW_CONVERSION', 'HIGH_REFUND', 'LOW_OR_NEGATIVE_MARGIN', 'MISSING_COST',
  '首页访客', '商品详情访客', '加购用户', '开始结算', '支付买家',
  '流量指标仅代表已同意分析的可测量访问，可能存在覆盖偏差', 'comparisonLabel',
  'trafficDataAvailableFrom', 'changeText', '对比期浏览量', '对比期净销售额'
]) assert.ok(page.includes(token), `missing dashboard page token: ${token}`)
assert.match(page, /canProfitExport\s*=\s*computed\(\(\)\s*=>\s*canProfit\.value\s*&&\s*checkPermi\(\['statistics:dashboard:profit-export'\]\)\)/, 'profit export UI must also require profit query permission')
assert.ok(!/from\s+['"](?:react|@radix|shadcn\/)/i.test(page), 'dashboard must use the existing Vue stack')
console.log('dashboard contract: OK')
