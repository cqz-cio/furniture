<script lang="ts" setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import type { BarSeriesOption, EChartsOption, LineSeriesOption } from 'echarts'
import download from '@/utils/download'
import { checkPermi } from '@/utils/permission'
import ErpPageLoading from '@/layout/components/ErpPageLoading.vue'
import {
  DashboardApi,
  type DashboardAttention,
  type DashboardProduct,
  type DashboardProductPage,
  type DashboardQuery,
  type DashboardScope,
  type DashboardStageOverview,
  type DashboardSummary,
  type DashboardTrendItem
} from '@/api/mall/statistics/dashboard'

defineOptions({ name: 'FurnitureDashboard' })

const route = useRoute()
const router = useRouter()

const canQuery = computed(() => checkPermi(['statistics:dashboard:query']))
const canProfit = computed(() => checkPermi(['statistics:dashboard:profit-query']))
const canExport = computed(() => checkPermi(['statistics:dashboard:export']))
const canProfitExport = computed(() => canProfit.value && checkPermi(['statistics:dashboard:profit-export']))

const yesterday = dayjs().subtract(1, 'day')
const routeValue = (key: string) => {
  const value = route.query[key]
  return Array.isArray(value) ? value[0] : value
}
const validDate = (value: string | null | undefined) =>
  Boolean(value && /^\d{4}-\d{2}-\d{2}$/.test(value) && dayjs(value).format('YYYY-MM-DD') === value)
const positiveInteger = (value: string | null | undefined) => {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : undefined
}
const riskTypes = [
  { label: '高流量低转化', value: 'HIGH_TRAFFIC_LOW_CONVERSION' },
  { label: '高退款', value: 'HIGH_REFUND' },
  { label: '低毛利或负毛利', value: 'LOW_OR_NEGATIVE_MARGIN' },
  { label: '成本缺失', value: 'MISSING_COST' }
]
const sortFields = ['spuId', 'browseCount', 'orderCount', 'paidRevenue', 'refundAmount', 'conversion', 'grossProfit', 'grossMargin']
const restoredScope: DashboardScope = routeValue('scope') === 'PRODUCT' ? 'PRODUCT' : 'SITE'
const restoredStart = routeValue('startDate')
const restoredEnd = routeValue('endDate')
const restoredPageSize = positiveInteger(routeValue('pageSize'))
const query = reactive<DashboardQuery>({
  scope: restoredScope,
  startDate: validDate(restoredStart) ? restoredStart! : yesterday.subtract(29, 'day').format('YYYY-MM-DD'),
  endDate: validDate(restoredEnd) ? restoredEnd! : yesterday.format('YYYY-MM-DD'),
  compare: routeValue('compare') !== 'false',
  pageNo: positiveInteger(routeValue('pageNo')) || 1,
  pageSize: restoredPageSize && [10, 20, 50, 100].includes(restoredPageSize) ? restoredPageSize : 20,
  sortField: sortFields.includes(routeValue('sortField') || '') ? routeValue('sortField')! : 'paidRevenue',
  sortOrder: routeValue('sortOrder') === 'asc' ? 'asc' : 'desc',
  ...(restoredScope === 'PRODUCT' ? {
    categoryId: positiveInteger(routeValue('categoryId')),
    spuId: positiveInteger(routeValue('spuId')),
    riskType: riskTypes.some((item) => item.value === routeValue('riskType')) ? routeValue('riskType')! : undefined
  } : {})
})
const dateRange = ref<[string, string]>([query.startDate, query.endDate])
const loading = ref(false)
const initialLoading = ref(true)
const exporting = ref(false)
const error = ref('')
const summary = ref<DashboardSummary | null>(null)
const trend = ref<DashboardTrendItem[]>([])
const stage = ref<DashboardStageOverview | null>(null)
const attention = ref<DashboardAttention | null>(null)
const products = ref<DashboardProduct[]>([])
const productTotal = ref(0)
const productPanel = ref<HTMLElement | null>(null)
let loadSequence = 0

const syncUrl = async () => {
  const urlQuery: Record<string, string> = {
    scope: query.scope,
    startDate: query.startDate,
    endDate: query.endDate,
    compare: String(query.compare !== false),
    pageNo: String(query.pageNo || 1),
    pageSize: String(query.pageSize || 20),
    sortField: query.sortField || 'paidRevenue',
    sortOrder: query.sortOrder || 'desc'
  }
  if (query.scope === 'PRODUCT') {
    if (query.categoryId) urlQuery.categoryId = String(query.categoryId)
    if (query.spuId) urlQuery.spuId = String(query.spuId)
    if (query.riskType) urlQuery.riskType = query.riskType
  }
  const targetFullPath = router.resolve({ path: route.path, query: urlQuery, hash: route.hash }).fullPath
  if (targetFullPath === route.fullPath) return false
  await router.replace({ path: route.path, query: urlQuery, hash: route.hash })
  return route.fullPath === targetFullPath
}

const quickRanges = [
  { label: '今日', days: 0 },
  { label: '近 7 日', days: 7 },
  { label: '近 30 日', days: 30 },
  { label: '近 90 日', days: 90 }
]

const selectRange = (days: number) => {
  const end = days === 0 ? dayjs() : yesterday
  const start = days === 0 ? end : end.subtract(days - 1, 'day')
  dateRange.value = [start.format('YYYY-MM-DD'), end.format('YYYY-MM-DD')]
  query.startDate = dateRange.value[0]
  query.endDate = dateRange.value[1]
  query.compare = days !== 0
  void loadDashboard()
}

const applyDateRange = () => {
  if (!dateRange.value?.[0] || !dateRange.value?.[1]) return
  query.startDate = dateRange.value[0]
  query.endDate = dateRange.value[1]
  void loadDashboard()
}

const loadDashboard = async () => {
  if (!canQuery.value) {
    initialLoading.value = false
    return
  }
  // AppView keys routed components by fullPath. A query normalization therefore remounts this
  // page; only the normalized instance may issue the dashboard request batch.
  if (await syncUrl()) return
  const sequence = ++loadSequence
  loading.value = true
  error.value = ''
  const normalized = { ...query }
  try {
    const attentionQuery: DashboardQuery = query.scope === 'SITE'
      ? { ...normalized, scope: 'PRODUCT', categoryId: undefined, spuId: undefined, riskType: undefined }
      : normalized
    const requests: Promise<unknown>[] = [
      DashboardApi.getSummary(normalized),
      DashboardApi.getTrend(normalized),
      DashboardApi.getStageOverview(normalized),
      DashboardApi.getAttention(attentionQuery)
    ]
    if (query.scope === 'PRODUCT') requests.push(DashboardApi.getProductPage(normalized))
    const results = await Promise.all(requests)
    if (sequence !== loadSequence) return
    summary.value = results[0] as DashboardSummary
    trend.value = results[1] as DashboardTrendItem[]
    stage.value = results[2] as DashboardStageOverview
    attention.value = results[3] as DashboardAttention
    const productPage = query.scope === 'PRODUCT' ? results[4] as DashboardProductPage : null
    products.value = productPage?.list || []
    productTotal.value = productPage?.total || 0
  } catch (caught) {
    if (sequence !== loadSequence) return
    error.value = caught instanceof Error ? caught.message : '数据加载失败'
  } finally {
    if (sequence === loadSequence) {
      loading.value = false
      initialLoading.value = false
    }
  }
}

watch(() => query.scope, (scope: DashboardScope) => {
  if (scope === 'SITE') {
    delete query.categoryId
    delete query.spuId
    delete query.riskType
    productTotal.value = 0
  } else {
    query.pageNo = 1
  }
  void loadDashboard()
})

const money = (value: number | null | undefined) => value == null ? '—' : `$${(value / 100).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
const integer = (value: number | null | undefined) => value == null ? '—' : value.toLocaleString('zh-CN')
const percent = (value: number | null | undefined) => value == null ? '—' : `${value.toFixed(2)}%`

const comparisonLabel = computed(() => {
  const value = summary.value
  return value?.comparisonStartDate && value.comparisonEndDate
    ? `对比周期 ${value.comparisonStartDate} 至 ${value.comparisonEndDate}`
    : ''
})

const changeText = (key: string, format: 'count' | 'money' | 'rate' = 'count') => {
  const change = summary.value?.changes?.[key]
  if (!change) return query.compare ? '流量不完整，暂不比较' : ''
  if (format === 'rate') {
    const points = change.changePercentagePoints
    return points == null ? '基期不可用' : `${points > 0 ? '+' : ''}${points.toFixed(2)} 个百分点`
  }
  if (change.changeAmount == null) return '基期不可用'
  const amount = format === 'money' ? money(Math.abs(change.changeAmount)) : integer(Math.abs(change.changeAmount))
  const direction = change.changeAmount > 0 ? '增加' : change.changeAmount < 0 ? '减少' : '持平'
  const ratio = change.changePercent == null ? '（基期为 0，变化率不可计算）' : `（${change.changePercent > 0 ? '+' : ''}${change.changePercent.toFixed(2)}%）`
  return direction === '持平' ? '较基期持平' : `较基期${direction} ${amount}${ratio}`
}

const refundRate = computed(() => {
  const value = summary.value
  if (!value || value.paidRevenue == null || value.refundAmount == null || value.paidRevenue <= 0) return '—'
  return percent(value.refundAmount / value.paidRevenue * 100)
})

const coreMetricCards = computed(() => {
  const value = summary.value
  if (!value) return []
  return [
    { key: 'netRevenue', label: '净销售额', value: money(value.netRevenue), hint: '支付金额 - 退款金额', change: changeText('netRevenue', 'money'), profit: false },
    { key: 'paidOrderCount', label: '支付订单', value: integer(value.paidOrderCount), hint: '按支付成功日归属', change: changeText('paidOrderCount'), profit: false },
    ...(canProfit.value ? [
      { key: 'grossProfit', label: '毛利润', value: money(value.grossProfit), hint: value.missingCostItemCount ? '成本不完整，结果仅供参考' : '已扣除退款及成本', change: changeText('grossProfit', 'money'), profit: true },
      { key: 'grossMarginPercent', label: '毛利率', value: percent(value.grossMarginPercent), hint: value.missingCostItemCount ? '成本补齐后可准确计算' : '毛利润 / 净销售额', change: changeText('grossMarginPercent', 'rate'), profit: true }
    ] : []),
    { key: 'browseOrderConversionPercent', label: '浏览至下单转化率', value: percent(value.browseOrderConversionPercent), hint: '支付订单 / 商品详情 PV', change: changeText('browseOrderConversionPercent', 'rate'), profit: false },
    { key: 'refundRate', label: '退款率', value: refundRate.value, hint: '退款金额 / 支付金额', change: '', profit: false }
  ]
})

const trafficMetricCards = computed(() => {
  const value = summary.value
  if (!value) return []
  return [
    ...(query.scope === 'SITE' ? [
      { key: 'homePv', label: '首页浏览量', value: integer(value.homePv), hint: '首页 PV', change: changeText('homePv') },
      { key: 'homeUv', label: '首页访客数', value: integer(value.homeUv), hint: '首页 UV', change: '' }
    ] : []),
    { key: 'productDetailPv', label: '商品详情浏览量', value: integer(value.productDetailPv), hint: '详情页 PV', change: changeText('productDetailPv') },
    { key: 'productDetailUv', label: '商品详情访客数', value: integer(value.productDetailUv), hint: '详情页 UV', change: '' },
    { key: 'addCartUserCount', label: '加购用户', value: integer(value.addCartUserCount), hint: '按访客去重', change: '' },
    { key: 'paidBuyerCount', label: '支付买家', value: integer(value.paidBuyerCount), hint: '按买家去重', change: '' }
  ]
})

const formatAsOf = (value: string | null | undefined) => {
  if (!value || !dayjs(value).isValid()) return '—'
  return dayjs(value).format('YYYY-MM-DD HH:mm')
}

const qualityStatus = computed(() => {
  const value = summary.value
  if (!value) return null
  const trafficLabels = { COMPLETE: '完整', PARTIAL: '部分可用', UNAVAILABLE: '不可用' } as const
  const freshnessLabels = { FRESH: '正常', DELAYED: '有延迟', STALE: '已过期' } as const
  const severity = value.freshnessStatus === 'STALE' || value.trafficDataStatus === 'UNAVAILABLE'
    ? 'danger'
    : value.freshnessStatus === 'DELAYED' || value.trafficDataStatus === 'PARTIAL' ? 'warning' : 'success'
  const summaryText = severity === 'success'
    ? '数据覆盖和更新状态正常，可用于日常经营判断。'
    : '部分指标可能不完整，请结合覆盖范围和更新时间谨慎判断。'
  return {
    severity,
    summaryText,
    trafficLabel: trafficLabels[value.trafficDataStatus],
    freshnessLabel: freshnessLabels[value.freshnessStatus],
    asOf: formatAsOf(value.asOf),
    availableFrom: value.trafficDataAvailableFrom || '—',
    affected: value.trafficDataStatus === 'COMPLETE' ? '无' : '首页及商品详情流量、浏览转化率',
    snapshotId: value.snapshotId
  }
})

const periodHighlights = computed(() => [
  { label: '净销售额变化', value: changeText('netRevenue', 'money') || '未启用对比' },
  { label: '支付订单变化', value: changeText('paidOrderCount') || '未启用对比' },
  { label: '数据质量', value: qualityStatus.value ? `${qualityStatus.value.trafficLabel} · ${qualityStatus.value.freshnessLabel}` : '—' },
  { label: '待处理商品', value: attention.value ? `${attention.value.items.length} 个` : '—' }
])

const riskMeta: Record<string, { title: string; action: string }> = {
  HIGH_TRAFFIC_LOW_CONVERSION: { title: '高流量低转化', action: '检查详情页卖点、价格和购买路径' },
  HIGH_REFUND: { title: '高退款', action: '检查退款原因、商品描述和履约质量' },
  LOW_OR_NEGATIVE_MARGIN: { title: '低毛利或负毛利', action: '核对售价、促销和采购成本' },
  MISSING_COST: { title: '成本缺失', action: '补齐商品成本后重新评估利润' }
}

const riskTitle = (riskType: string) => riskMeta[riskType]?.title || riskType
const riskAction = (riskType: string) => riskMeta[riskType]?.action || '查看商品经营明细'
const riskBySpu = computed(() => {
  const result = new Map<number, string[]>()
  for (const item of attention.value?.items || []) {
    const risks = result.get(item.spuId) || []
    risks.push(item.riskType)
    result.set(item.spuId, risks)
  }
  return result
})
const productRisks = (spuId: number) => riskBySpu.value.get(spuId) || []
const productRank = (index: number) => ((query.pageNo || 1) - 1) * (query.pageSize || 20) + index + 1

const trafficPreset = 'traffic'
const salesPreset = 'sales'
const profitPreset = 'profit'
const tablePreset = ref<typeof trafficPreset | typeof salesPreset | typeof profitPreset>(salesPreset)

const chartOptions = computed<EChartsOption>(() => {
  const series: Array<LineSeriesOption | BarSeriesOption> = [
    { name: '浏览量', type: 'line', smooth: true, connectNulls: false, data: trend.value.map((item) => query.scope === 'SITE' ? item.homePv : item.productDetailPv), itemStyle: { color: '#111827' } },
    { name: '净销售额', type: 'bar', yAxisIndex: 1, data: trend.value.map((item) => item.netRevenue == null ? null : item.netRevenue / 100), itemStyle: { color: '#2563eb' } }
  ]
  if (query.compare) {
    series.push(
      { name: '对比期浏览量', type: 'line', smooth: true, connectNulls: false, lineStyle: { type: 'dashed' }, data: trend.value.map((item) => query.scope === 'SITE' ? item.reference?.homePv : item.reference?.productDetailPv), itemStyle: { color: '#6b7280' } },
      { name: '对比期净销售额', type: 'line', yAxisIndex: 1, connectNulls: false, lineStyle: { type: 'dotted' }, data: trend.value.map((item) => item.reference?.netRevenue == null ? null : item.reference.netRevenue / 100), itemStyle: { color: '#9a7b45' } }
    )
  }
  return {
    aria: { enabled: true, description: '按日展示浏览量和净销售额趋势' },
    tooltip: { trigger: 'axis' },
    legend: { data: query.compare ? ['浏览量', '净销售额', '对比期浏览量', '对比期净销售额'] : ['浏览量', '净销售额'] },
    grid: { left: 20, right: 24, top: 46, bottom: 20, containLabel: true },
    xAxis: { type: 'category', data: trend.value.map((item) => item.day), boundaryGap: false },
    yAxis: [{ type: 'value', name: 'PV' }, { type: 'value', name: 'USD', axisLabel: { formatter: (v: number) => `$${v}` } }],
    series
  }
})

const openAttention = (item: { spuId: number; riskType: string }) => {
  query.scope = 'PRODUCT'
  query.spuId = item.spuId
  query.riskType = item.riskType
  query.pageNo = 1
  void nextTick(() => productPanel.value?.focus())
}

const attentionEvaluatedEmpty = computed(() => Boolean(
  attention.value && attention.value.items.length === 0 && attention.value.notEvaluated.length === 0
))

const stageLabels: Record<string, string> = {
  HOME_UV: '首页访客', PRODUCT_DETAIL_UV: '商品详情访客', ADD_CART_USER: '加购用户',
  CHECKOUT_SESSION: '开始结算', PAID_BUYER: '支付买家'
}

const applyProductFilters = () => {
  query.pageNo = 1
  void loadDashboard()
}

const changeCategory = () => {
  if (query.spuId) {
    query.spuId = undefined
    ElMessage.info('分类已变化，原商品筛选已清除，请重新选择商品')
  }
  applyProductFilters()
}

const clearProductFilters = () => {
  query.categoryId = undefined
  query.spuId = undefined
  query.riskType = undefined
  applyProductFilters()
}

const changeProductPage = (pageNo: number) => {
  query.pageNo = pageNo
  void loadDashboard()
}

const changeProductPageSize = (pageSize: number) => {
  query.pageSize = pageSize
  query.pageNo = 1
  void loadDashboard()
}

const changeProductSort = ({ prop, order }: { prop: string; order: string | null }) => {
  const fields: Record<string, string> = {
    spuId: 'spuId', browseCount: 'browseCount', orderCount: 'orderCount',
    orderPayPrice: 'paidRevenue', afterSaleRefundPrice: 'refundAmount',
    browseConvertPercent: 'conversion', grossProfit: 'grossProfit', grossMarginPercent: 'grossMargin'
  }
  if (!fields[prop]) return
  query.sortField = fields[prop]
  query.sortOrder = order === 'ascending' ? 'asc' : 'desc'
  query.pageNo = 1
  void loadDashboard()
}

const focusProduct = (row: DashboardProduct) => {
  query.scope = 'PRODUCT'
  query.spuId = row.spuId
  query.pageNo = 1
  void loadDashboard()
  void nextTick(() => productPanel.value?.focus())
}

const exportRows = async (profitExport: boolean) => {
  exporting.value = true
  try {
    const data = profitExport ? await DashboardApi.exportProfitExcel(query) : await DashboardApi.exportProductExcel(query)
    download.excel(data, profitExport ? '数据看板-利润.xls' : '数据看板-商品经营.xls')
  } catch {
    ElMessage.error('导出失败，请缩小日期或筛选范围')
  } finally {
    exporting.value = false
  }
}

onMounted(loadDashboard)
</script>

<template>
  <div class="furniture-dashboard" v-loading="loading && !initialLoading" aria-live="polite">
    <ErpPageLoading v-if="initialLoading" title="数据看板" />
    <el-result v-else-if="!canQuery" icon="warning" title="暂无数据看板查询权限" sub-title="请联系管理员授予 statistics:dashboard:query" />
    <template v-else>
      <header class="dashboard-header">
        <div class="header-copy">
          <div class="title-row">
            <h1>数据看板</h1>
            <el-tag effect="plain" round>{{ query.scope === 'SITE' ? '全站经营' : '商品分析' }}</el-tag>
          </div>
          <p>先看经营结果，再定位流量、转化和商品风险。</p>
          <div v-if="summary" class="header-meta">
            <span>数据截至 {{ formatAsOf(summary.asOf) }}</span>
            <span>{{ summary.freshnessStatus === 'FRESH' ? '更新正常' : '更新需关注' }}</span>
            <span>{{ query.startDate }} 至 {{ query.endDate }}</span>
          </div>
        </div>
        <div class="header-actions">
          <el-button :loading="loading" @click="loadDashboard">刷新数据</el-button>
          <el-dropdown v-if="canExport || canProfitExport" trigger="click">
            <el-button type="primary" :loading="exporting">导出数据</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-if="canExport" @click="exportRows(false)">导出经营数据</el-dropdown-item>
                <el-dropdown-item v-if="canProfitExport" @click="exportRows(true)">导出利润数据</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <section class="filter-bar" aria-label="数据筛选">
        <div class="filter-group scope-filter">
          <span class="filter-label">数据范围</span>
          <el-segmented v-model="query.scope" :options="[{ label: '全站经营', value: 'SITE' }, { label: '商品分析', value: 'PRODUCT' }]" />
        </div>
        <div class="filter-group date-filter">
          <span class="filter-label">统计周期</span>
          <div class="quick-ranges">
            <el-button v-for="item in quickRanges" :key="item.label" text @click="selectRange(item.days)">{{ item.label }}</el-button>
          </div>
          <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" :clearable="false" @change="applyDateRange" />
        </div>
        <div class="filter-group compare-filter">
          <span class="filter-label">周期对比</span>
          <el-switch v-model="query.compare" inline-prompt active-text="开" inactive-text="关" @change="loadDashboard" />
        </div>
        <div v-if="query.scope === 'PRODUCT'" class="product-filters">
          <el-input-number v-model="query.categoryId" :min="1" :controls="false" placeholder="分类 ID" aria-label="分类 ID" @change="changeCategory" />
          <el-input-number v-model="query.spuId" :min="1" :controls="false" placeholder="商品 SPU" aria-label="商品 SPU" @change="applyProductFilters" />
          <el-select v-model="query.riskType" clearable placeholder="经营风险" aria-label="经营风险" @change="applyProductFilters">
            <el-option v-for="item in riskTypes" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-button v-if="query.categoryId || query.spuId || query.riskType" text @click="clearProductFilters">重置商品筛选</el-button>
        </div>
      </section>

      <el-alert v-if="error" type="error" show-icon :closable="false" :title="error"><template #default><el-button text @click="loadDashboard">重试</el-button></template></el-alert>
      <section v-if="qualityStatus" class="data-quality" :class="`is-${qualityStatus.severity}`" aria-label="数据质量">
        <div class="quality-summary">
          <div>
            <span class="section-kicker">数据质量</span>
            <strong>{{ qualityStatus.summaryText }}</strong>
          </div>
          <div class="quality-tags">
            <el-tag effect="plain">流量 {{ qualityStatus.trafficLabel }}</el-tag>
            <el-tag effect="plain">更新 {{ qualityStatus.freshnessLabel }}</el-tag>
          </div>
        </div>
        <details>
          <summary>查看覆盖范围与更新时间</summary>
          <div class="quality-details">
            <span>数据截至 <strong>{{ qualityStatus.asOf }}</strong></span>
            <span>流量最早可用 <strong>{{ qualityStatus.availableFrom }}</strong></span>
            <span>受影响指标 <strong>{{ qualityStatus.affected }}</strong></span>
            <span>未知值规则 <strong>显示为—，不会作为 0</strong></span>
            <p class="quality-notice">流量指标仅代表已同意分析的可测量访问，可能存在覆盖偏差。</p>
          </div>
        </details>
      </section>

      <section class="section-block" aria-labelledby="result-heading">
        <div class="section-heading">
          <div><span class="section-kicker">经营结果</span><h2 id="result-heading">先判断本周期经营是否健康</h2></div>
          <span v-if="comparisonLabel">{{ comparisonLabel }}</span>
        </div>
        <div class="core-metric-grid">
          <article v-for="card in coreMetricCards" :key="card.key" class="metric-card" :class="{ 'profit-metric': card.profit }">
            <span>{{ card.label }}</span><strong>{{ card.value }}</strong><small>{{ card.hint }}</small><small v-if="card.change" class="metric-change">{{ card.change }}</small>
          </article>
        </div>
      </section>

      <section class="section-block traffic-section" aria-labelledby="traffic-heading">
        <div class="section-heading compact-heading">
          <div><span class="section-kicker">流量与转化</span><h2 id="traffic-heading">访问质量与关键行为</h2></div>
          <span v-if="query.scope === 'PRODUCT'">商品范围不展示无法归因的首页指标</span>
        </div>
        <div class="traffic-metric-grid">
          <article v-for="card in trafficMetricCards" :key="card.key" class="metric-card traffic-card">
            <span>{{ card.label }}</span><strong>{{ card.value }}</strong><small>{{ card.hint }}</small><small v-if="card.change" class="metric-change">{{ card.change }}</small>
          </article>
        </div>
      </section>

      <section class="dashboard-grid">
        <article class="panel trend-panel">
          <div class="panel-heading"><div><span class="section-kicker">经营趋势</span><h2>浏览量与净销售额趋势</h2><p>时区 Asia/Shanghai · 金额 USD</p></div><span v-if="summary">更新于 {{ formatAsOf(summary.asOf) }}</span></div>
          <Echart v-if="trend.length" :height="300" :options="chartOptions" />
          <el-empty v-else description="当前范围暂无趋势数据" />
          <details v-if="trend.length" class="data-alternative"><summary>查看趋势数据表</summary><table><thead><tr><th>日期</th><th>浏览量</th><th>净销售额</th><th v-if="query.compare">对比日期</th><th v-if="query.compare">对比期浏览量</th><th v-if="query.compare">对比期净销售额</th></tr></thead><tbody><tr v-for="item in trend" :key="item.day"><td>{{ item.day }}</td><td>{{ integer(query.scope === 'SITE' ? item.homePv : item.productDetailPv) }}</td><td>{{ money(item.netRevenue) }}</td><td v-if="query.compare">{{ item.referenceDay || '—' }}</td><td v-if="query.compare">{{ integer(query.scope === 'SITE' ? item.reference?.homePv : item.reference?.productDetailPv) }}</td><td v-if="query.compare">{{ money(item.reference?.netRevenue) }}</td></tr></tbody></table></details>
        </article>

        <article class="panel summary-panel">
          <div class="panel-heading"><div><span class="section-kicker">周期经营摘要</span><h2>本周期值得关注的变化</h2></div></div>
          <div class="highlight-list">
            <div v-for="item in periodHighlights" :key="item.label"><span>{{ item.label }}</span><strong>{{ item.value }}</strong></div>
          </div>
          <div v-if="stage" class="stage-list">
            <div v-for="item in stage.items" :key="item.stage" :class="{ muted: item.applicability === 'NOT_APPLICABLE' }"><span>{{ stageLabels[item.stage] || item.stage }}</span><strong>{{ integer(item.value) }}</strong><small>{{ item.unit }} · {{ item.dedupeScope }}</small></div>
          </div>
        </article>
      </section>

      <section class="panel attention-panel">
        <div class="panel-heading"><div><span class="section-kicker">运营任务</span><h2>优先处理这些商品问题</h2><p>任务来源于运营关注规则，处理前仍需结合商品详情核实。</p></div><el-tag v-if="attention?.items.length" type="warning" effect="plain">{{ attention.items.length }} 项待处理</el-tag></div>
        <div v-if="attention?.items.length" class="attention-list">
          <button v-for="item in attention.items" :key="`${item.spuId}-${item.riskType}`" @click="openAttention(item)">
            <span class="risk-title">{{ riskTitle(item.riskType) }}</span>
            <strong>SPU {{ item.spuId }}</strong>
            <small>{{ item.copy }}</small>
            <small class="suggested-action">建议：{{ riskAction(item.riskType) }}</small>
            <span class="task-action">查看商品 →</span>
          </button>
        </div>
        <el-empty v-if="attentionEvaluatedEmpty" description="已评估规则中暂无需要立即处理的商品" />
        <el-alert v-else-if="attention && !attention.items.length && attention.notEvaluated.length" type="warning" :closable="false" title="部分规则因数据不完整尚未评估，不能视为没有经营风险。" />
        <el-alert v-for="item in attention?.notEvaluated || []" :key="`${item.spuId}-${item.riskType}`" type="warning" :closable="false" :title="`${riskTitle(item.riskType)}：${item.copy}`" />
      </section>

      <section v-if="query.scope === 'PRODUCT'" ref="productPanel" class="panel product-panel" tabindex="-1">
        <div class="panel-heading table-heading">
          <div><span class="section-kicker">商品经营明细</span><h2>按商品定位流量、销售和利润问题</h2><p>默认只显示运营判断最常用的九列，可切换查看其他指标。</p></div>
          <el-segmented v-model="tablePreset" :options="[
            { label: '销售视图', value: salesPreset },
            { label: '流量视图', value: trafficPreset },
            ...(canProfit ? [{ label: '利润视图', value: profitPreset }] : [])
          ]" />
        </div>
        <el-table :data="products" row-key="spuId" empty-text="当前筛选范围暂无商品经营数据" @sort-change="changeProductSort">
          <el-table-column type="index" label="排名" width="66" fixed="left" :index="productRank" />
          <el-table-column prop="productName" label="商品名称" fixed min-width="220">
            <template #default="{ row }"><div class="product-identity"><el-image v-if="row.picUrl" :src="row.picUrl" fit="cover" /><span><strong>{{ row.productName || `SPU ${row.spuId}` }}</strong><small>SPU {{ row.spuId }} · 分类 {{ row.categoryId || '—' }}</small></span></div></template>
          </el-table-column>
          <el-table-column prop="browseCount" label="详情 PV" min-width="100" sortable="custom"><template #default="{ row }">{{ integer(row.browseCount) }}</template></el-table-column>
          <el-table-column v-if="tablePreset === trafficPreset" prop="browseUserCount" label="详情 UV" min-width="100"><template #default="{ row }">{{ integer(row.browseUserCount) }}</template></el-table-column>
          <el-table-column v-if="tablePreset === trafficPreset" prop="cartCount" label="加购次数" min-width="100"><template #default="{ row }">{{ integer(row.cartCount) }}</template></el-table-column>
          <el-table-column v-if="tablePreset !== trafficPreset" prop="orderCount" label="支付订单" min-width="110" sortable="custom"><template #default="{ row }">{{ integer(row.orderCount) }}</template></el-table-column>
          <el-table-column prop="browseConvertPercent" label="转化率" min-width="100" sortable="custom"><template #default="{ row }">{{ percent(row.browseConvertPercent) }}</template></el-table-column>
          <el-table-column v-if="tablePreset !== trafficPreset" prop="netRevenue" label="净销售额" min-width="130"><template #default="{ row }">{{ money(row.netRevenue) }}</template></el-table-column>
          <el-table-column v-if="canProfit && tablePreset !== trafficPreset" prop="grossProfit" label="毛利润" min-width="130" sortable="custom"><template #default="{ row }">{{ money(row.grossProfit) }}</template></el-table-column>
          <el-table-column v-if="canProfit && tablePreset === profitPreset" prop="grossMarginPercent" label="毛利率" min-width="100" sortable="custom"><template #default="{ row }">{{ percent(row.grossMarginPercent) }}</template></el-table-column>
          <el-table-column v-if="canProfit && tablePreset === profitPreset" prop="missingCostItemCount" label="缺失成本" min-width="110"><template #default="{ row }">{{ integer(row.missingCostItemCount) }}</template></el-table-column>
          <el-table-column v-if="tablePreset === trafficPreset" prop="trafficDataStatus" label="流量状态" min-width="110"><template #default="{ row }">{{ row.trafficDataStatus === 'COMPLETE' ? '完整' : row.trafficDataStatus === 'PARTIAL' ? '部分可用' : '不可用' }}</template></el-table-column>
          <el-table-column label="风险" min-width="150">
            <template #default="{ row }"><span v-if="productRisks(row.spuId).length" class="table-risk">{{ riskTitle(productRisks(row.spuId)[0]) }}<small v-if="productRisks(row.spuId).length > 1">+{{ productRisks(row.spuId).length - 1 }}</small></span><span v-else class="muted-text">暂无命中</span></template>
          </el-table-column>
          <el-table-column label="操作" fixed="right" width="100"><template #default="{ row }"><el-button link type="primary" @click="focusProduct(row)">聚焦分析</el-button></template></el-table-column>
        </el-table>
        <el-pagination v-if="productTotal" class="product-pagination" :current-page="query.pageNo" :page-size="query.pageSize" :page-sizes="[10, 20, 50, 100]" :total="productTotal" layout="total, sizes, prev, pager, next" @current-change="changeProductPage" @size-change="changeProductPageSize" />
      </section>
    </template>
  </div>
</template>

<style lang="scss" scoped>
.furniture-dashboard {
  min-height: 100%;
  padding: 20px 24px 40px;
  color: #18181b;
  background: #f8fafc;
}

.dashboard-header,
.filter-bar,
.section-block,
.panel,
.metric-card,
.data-quality {
  border: 1px solid #e4e4e7;
  background: #fff;
}

.dashboard-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  padding: 18px 20px;
  border-radius: 12px;
}

.title-row,
.header-meta,
.header-actions,
.quick-ranges,
.quality-tags {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dashboard-header h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -.02em;
}

.dashboard-header p {
  margin: 5px 0 0;
  color: #71717a;
  font-size: 14px;
}

.header-meta {
  flex-wrap: wrap;
  margin-top: 10px;
  color: #71717a;
  font-size: 12px;
}

.header-meta span + span::before {
  margin-right: 8px;
  color: #d4d4d8;
  content: '•';
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 14px 18px;
  margin: 12px 0;
  padding: 14px 16px;
  border-radius: 12px;
}

.filter-group {
  display: flex;
  align-items: center;
  gap: 10px;
}

.filter-label,
.section-kicker {
  color: #71717a;
  font-size: 12px;
  font-weight: 600;
}

.date-filter {
  flex-wrap: wrap;
}

.product-filters {
  display: flex;
  flex: 1 1 100%;
  align-items: center;
  gap: 10px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f2;
}

.product-filters .el-input-number,
.product-filters .el-select {
  width: 180px;
}

.data-quality {
  margin-bottom: 12px;
  padding: 13px 16px;
  border-left-width: 3px;
  border-radius: 10px;
}

.data-quality.is-success { border-left-color: #16a34a; }
.data-quality.is-warning { border-left-color: #d97706; }
.data-quality.is-danger { border-left-color: #dc2626; }

.quality-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.quality-summary > div:first-child {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.data-quality details {
  margin-top: 8px;
  color: #52525b;
  font-size: 12px;
}

.data-quality summary,
.data-alternative summary {
  width: max-content;
  color: #2563eb;
  cursor: pointer;
}

.quality-details {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #f0f0f2;
}

.quality-details span {
  display: flex;
  flex-direction: column;
  gap: 3px;
  color: #71717a;
}

.quality-details strong { color: #27272a; }

.section-block,
.panel {
  margin-bottom: 12px;
  padding: 18px;
  border-radius: 12px;
}

.section-heading,
.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 14px;
}

.section-heading h2,
.panel-heading h2 {
  margin: 3px 0 0;
  font-size: 17px;
  font-weight: 650;
}

.section-heading > span,
.panel-heading p,
.panel-heading > span {
  margin: 4px 0 0;
  color: #71717a;
  font-size: 12px;
}

.core-metric-grid,
.traffic-metric-grid {
  display: grid;
  gap: 10px;
}

.core-metric-grid { grid-template-columns: repeat(6, minmax(0, 1fr)); }
.traffic-metric-grid { grid-template-columns: repeat(6, minmax(0, 1fr)); }

.metric-card {
  display: flex;
  min-width: 0;
  min-height: 116px;
  flex-direction: column;
  padding: 15px;
  border-radius: 10px;
  box-shadow: 0 1px 2px rgb(24 24 27 / 3%);
}

.metric-card > span,
.metric-card small { color: #71717a; }

.metric-card > span {
  overflow: hidden;
  font-size: 13px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-card > strong {
  margin: 9px 0 4px;
  font-size: clamp(20px, 1.8vw, 27px);
  font-weight: 700;
  letter-spacing: -.03em;
}

.metric-card small {
  font-size: 11px;
  line-height: 1.45;
}

.metric-card .metric-change {
  margin-top: auto;
  padding-top: 7px;
  color: #3f3f46;
}

.profit-metric { border-color: #dbeafe; }
.traffic-section { background: #fcfcfd; }
.traffic-card { min-height: 102px; }

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(300px, 1fr);
  gap: 12px;
}

.dashboard-grid > .panel { min-width: 0; }

.highlight-list {
  display: grid;
  gap: 1px;
  overflow: hidden;
  margin-bottom: 14px;
  border: 1px solid #e4e4e7;
  border-radius: 10px;
  background: #e4e4e7;
}

.highlight-list > div {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 11px 12px;
  background: #fff;
}

.highlight-list span { color: #71717a; font-size: 11px; }
.highlight-list strong { font-size: 13px; font-weight: 600; }

.stage-list {
  display: grid;
  gap: 6px;
}

.stage-list > div {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 3px 16px;
  padding: 9px 11px;
  border-radius: 8px;
  background: #f4f4f5;
  font-size: 12px;
}

.stage-list small { grid-column: 1 / -1; color: #71717a; }
.stage-list .muted { opacity: .5; }

.attention-list {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.attention-list button {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 7px;
  padding: 14px;
  color: #27272a;
  text-align: left;
  border: 1px solid #fed7aa;
  border-radius: 10px;
  background: #fffaf5;
  cursor: pointer;
  transition: border-color .15s ease, box-shadow .15s ease;
}

.attention-list button:hover {
  border-color: #fb923c;
  box-shadow: 0 3px 10px rgb(194 65 12 / 8%);
}

.attention-list .risk-title { color: #c2410c; font-size: 12px; font-weight: 700; }
.attention-list small { color: #71717a; line-height: 1.45; }
.attention-list .suggested-action { color: #3f3f46; }
.attention-list .task-action { margin-top: auto; color: #2563eb; font-size: 12px; font-weight: 600; }

.table-heading { align-items: center; }
.data-alternative { margin-top: 8px; font-size: 12px; }
.data-alternative table { width: 100%; margin-top: 8px; border-collapse: collapse; }
.data-alternative th,
.data-alternative td { padding: 7px; text-align: left; border-bottom: 1px solid #eee; }

.product-identity { display: flex; align-items: center; gap: 10px; }
.product-identity .el-image { width: 40px; height: 40px; border-radius: 8px; background: #f4f4f5; }
.product-identity span { display: flex; min-width: 0; flex-direction: column; gap: 3px; }
.product-identity strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.product-identity small,
.muted-text { color: #a1a1aa; }
.table-risk { display: inline-flex; align-items: center; gap: 5px; color: #c2410c; font-size: 12px; font-weight: 600; }
.table-risk small { padding: 1px 5px; border-radius: 999px; background: #ffedd5; }
.product-pagination { justify-content: flex-end; margin-top: 16px; }

@media (max-width: 1280px) {
  .core-metric-grid,
  .traffic-metric-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}

@media (max-width: 1100px) {
  .dashboard-grid { grid-template-columns: 1fr; }
  .attention-list { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .quality-details { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 700px) {
  .furniture-dashboard { padding: 12px; }
  .dashboard-header,
  .quality-summary,
  .section-heading,
  .panel-heading { align-items: stretch; flex-direction: column; }
  .header-actions,
  .filter-bar,
  .filter-group,
  .product-filters { align-items: stretch; flex-direction: column; }
  .core-metric-grid,
  .traffic-metric-grid,
  .attention-list,
  .quality-details { grid-template-columns: 1fr; }
  .quick-ranges { flex-wrap: wrap; }
  .date-filter :deep(.el-date-editor) { width: 100%; }
}
</style>
