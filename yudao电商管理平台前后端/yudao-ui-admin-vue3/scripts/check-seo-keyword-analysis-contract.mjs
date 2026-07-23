import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'

const readRequired = (relativePath) => {
  const url = new URL(relativePath, import.meta.url)
  assert.ok(existsSync(url), `missing SEO keyword analysis file: ${relativePath}`)
  return readFileSync(url, 'utf8')
}

const api = readRequired('../src/api/seo/analysis/index.ts')
const page = readRequired('../src/views/seo/analysis/index.vue')
const keywordCard = readRequired('../src/views/seo/analysis/components/KeywordRelevanceCard.vue')
const dimensions = readRequired('../src/views/seo/analysis/components/KeywordDimensionBreakdown.vue')
const suggestions = readRequired('../src/views/seo/analysis/components/KeywordSuggestionList.vue')
const comparison = readRequired('../src/views/seo/analysis/components/AnalysisComparison.vue')
const metadataPage = readRequired('../src/views/seo/metadata/index.vue')
const metadataApi = readRequired('../src/api/seo/metadata/index.ts')
const packageJson = JSON.parse(readRequired('../package.json'))

for (const endpoint of [
  '/seo/analysis/run',
  '/seo/analysis/${id}',
  '/seo/analysis/${id}/keywords',
  '/seo/analysis/${id}/keywords/${keywordAnalysisId}',
  '/seo/analysis/${id}/rerun',
  '/seo/analysis/${id}/compare'
]) {
  assert.ok(api.includes(endpoint), `missing analysis API endpoint ${endpoint}`)
}

for (const token of [
  'keyPositionPercent',
  'lexicalMatchPercent',
  'semanticPercent',
  'distributionPercent',
  'intentCoveragePercent',
  'relevancePercent',
  'reason',
  'recommendation',
  'recoverableScore'
]) {
  assert.ok(api.includes(token), `missing typed SEO analysis field ${token}`)
}

assert.ok(page.includes("name: 'SeoAnalysis'"), 'analysis page must expose the dynamic-route component name')
assert.ok(page.includes('关联度是系统内部内容分析指标，不代表搜索引擎排名保证。'), 'analysis disclaimer is required')
assert.ok(page.includes('逐关键词分析结果'), 'analysis page must present one result per keyword')
assert.ok(page.includes('KeywordRelevanceCard'), 'analysis page must use the keyword card')
assert.ok(page.includes('AnalysisComparison'), 'analysis page must expose history comparison')
assert.ok(page.includes("sourceType: 'MANUAL'"), 'analysis page must support manual content input')

for (const source of [keywordCard, dimensions, page]) {
  const progressTags = [...source.matchAll(/<el-progress[\s\S]*?\/>/g)].map((match) => match[0])
  assert.ok(progressTags.length > 0, 'expected SEO progress bars')
  for (const tag of progressTags) {
    assert.ok(tag.includes('color="var(--el-color-primary)"'), 'all SEO progress bars must use the unified primary color')
    assert.doesNotMatch(tag, /success|warning|danger|#[0-9a-f]{3,8}/i, 'SEO progress color must not change by score')
  }
}

assert.ok(dimensions.includes("typeof props.keyword.semanticPercent !== 'number'"), 'semantic availability must be explicit')
assert.ok(dimensions.includes('未完成'), 'unavailable semantic scoring must render as not completed')
assert.ok(dimensions.includes('不会被当作 0 分'), 'unavailable semantic scoring must not be represented as zero')
assert.ok(!dimensions.includes('semanticPercent ?? 0'), 'semantic score must never silently fall back to zero')

assert.ok(keywordCard.includes('getSeoKeywordAnalysis'), 'keyword evidence must be loaded independently')
assert.ok(keywordCard.includes('if (detail.value || loading.value) return'), 'keyword details must be lazy loaded once')
for (const label of ['判断原因：', '修改建议：', '证据：']) {
  assert.ok(suggestions.includes(label), `missing suggestion explanation ${label}`)
}
for (const label of ['上次', '本次', '变化', '已解决：', '新增问题：']) {
  assert.ok(comparison.includes(label), `missing comparison label ${label}`)
}

assert.ok(metadataApi.includes('latestAnalysisId?: number'), 'metadata response must expose the latest analysis link')
assert.match(metadataPage, /v-hasPermi="\['seo:analysis:run'\]"[\s\S]*?@click="handleAnalyze\(scope\.row\)"/, 'metadata analyze action must require run permission')
assert.match(metadataPage, /v-hasPermi="\['seo:analysis:query'\]"[\s\S]*?@click="openAnalysis\(scope\.row\.latestAnalysisId\)"/, 'latest analysis action must require query permission')
assert.ok(metadataPage.includes("sourceType: 'ENTITY'"), 'metadata analysis must reload entity content on the server')

assert.equal(
  packageJson.scripts?.['check:seo-analysis'],
  'node scripts/check-seo-keyword-analysis-contract.mjs',
  'SEO analysis contract script must be runnable from package.json'
)

console.log('seo keyword analysis contract: OK')
