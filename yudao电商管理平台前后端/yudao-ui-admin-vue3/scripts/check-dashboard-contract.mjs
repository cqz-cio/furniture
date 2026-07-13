import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const config = readFileSync(new URL('../src/config/furnitureLite.ts', import.meta.url), 'utf8')
const api = readFileSync(new URL('../src/api/mall/statistics/dashboard.ts', import.meta.url), 'utf8')

assert.match(config, /['"]\/dashboard['"]/, 'furniture-lite must allow the dashboard root')
for (const token of [
  'DashboardScope', 'scope', 'startDate', 'endDate', 'getSummary', 'getTrend',
  'getStageOverview', 'getAttention', 'getProductPage', 'trafficDataStatus',
  'freshnessStatus', 'exactCostItemCount', 'knownCostAmount', 'profit-export'
]) {
  assert.ok(api.includes(token), `missing dashboard token: ${token}`)
}
console.log('dashboard contract: OK')
