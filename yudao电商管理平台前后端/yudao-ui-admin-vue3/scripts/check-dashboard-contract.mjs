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
]) assert.ok(page.includes(token), `missing dashboard page token: ${token}`)
assert.ok(!/from\s+['"](?:react|@radix|shadcn\/)/i.test(page), 'dashboard must use the existing Vue stack')
console.log('dashboard contract: OK')
