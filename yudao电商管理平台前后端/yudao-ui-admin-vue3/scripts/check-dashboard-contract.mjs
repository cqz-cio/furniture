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
for (const token of [
  'coreMetricCards', 'trafficMetricCards', 'qualityStatus', 'periodHighlights',
  '经营结果', '流量与转化', '数据质量', '经营趋势', '周期经营摘要',
  '高流量低转化', '高退款', '低毛利或负毛利', '成本缺失',
  'trafficPreset', 'salesPreset', 'profitPreset', 'tablePreset',
  '排名', '详情 PV', '支付订单', '净销售额', '毛利润', '风险', '操作'
]) assert.ok(page.includes(token), `missing redesigned dashboard token: ${token}`)
assert.equal((page.match(/class="quality-alert"/g) || []).length, 0,
  'quality messages must be consolidated into one quality status panel')
assert.match(page, /const\s+coreMetricCards\s*=\s*computed[\s\S]*?canProfit\.value/,
  'profit result cards must be structurally permission guarded')
assert.match(page, /<el-table-column\s+type="index"\s+label="排名"\s+width="66"\s+fixed="left"/,
  'ranking must remain the first fixed product-table column')
assert.match(page, /<el-table[^>]*empty-text="当前筛选范围暂无商品经营数据"/,
  'product table must own one concise empty state')
assert.ok(!page.includes('<el-empty v-if="!products.length"'),
  'product table must not render a second empty state below the table')
assert.match(page, /canProfitExport\s*=\s*computed\(\(\)\s*=>\s*canProfit\.value\s*&&\s*checkPermi\(\['statistics:dashboard:profit-export'\]\)\)/, 'profit export UI must also require profit query permission')
assert.ok(!/from\s+['"](?:react|@radix|shadcn\/)/i.test(page), 'dashboard must use the existing Vue stack')
console.log('dashboard contract: OK')
