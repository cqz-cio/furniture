<script lang="ts" setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import download from '@/utils/download'
import { checkPermi } from '@/utils/permission'
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

const canQuery = computed(() => checkPermi(['statistics:dashboard:query']))
const canProfit = computed(() => checkPermi(['statistics:dashboard:profit-query']))
const canExport = computed(() => checkPermi(['statistics:dashboard:export']))
const canProfitExport = computed(() => checkPermi(['statistics:dashboard:profit-export']))

const yesterday = dayjs().subtract(1, 'day')
const query = reactive<DashboardQuery>({
  scope: 'SITE',
  startDate: yesterday.subtract(29, 'day').format('YYYY-MM-DD'),
  endDate: yesterday.format('YYYY-MM-DD'),
  pageNo: 1,
  pageSize: 20,
  sortField: 'paidRevenue',
  sortOrder: 'desc'
})
const dateRange = ref<[string, string]>([query.startDate, query.endDate])
const loading = ref(false)
const exporting = ref(false)
const error = ref('')
const summary = ref<DashboardSummary | null>(null)
const trend = ref<DashboardTrendItem[]>([])
const stage = ref<DashboardStageOverview | null>(null)
const attention = ref<DashboardAttention | null>(null)
const products = ref<DashboardProduct[]>([])
const productTotal = ref(0)
let loadSequence = 0

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
  void loadDashboard()
}

const applyDateRange = () => {
  if (!dateRange.value?.[0] || !dateRange.value?.[1]) return
  query.startDate = dateRange.value[0]
  query.endDate = dateRange.value[1]
  void loadDashboard()
}

const loadDashboard = async () => {
  if (!canQuery.value) return
  const sequence = ++loadSequence
  loading.value = true
  error.value = ''
  const normalized = { ...query }
  try {
    const requests: Promise<unknown>[] = [
      DashboardApi.getSummary(normalized),
      DashboardApi.getTrend(normalized),
      DashboardApi.getStageOverview(normalized),
      DashboardApi.getAttention(normalized)
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
    if (sequence === loadSequence) loading.value = false
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

const metricCards = computed(() => {
  const value = summary.value
  if (!value) return []
  const cards = [
    { label: query.scope === 'SITE' ? '首页浏览量' : '商品详情浏览量', value: integer(query.scope === 'SITE' ? value.homePv : value.productDetailPv), hint: 'PV' },
    { label: '支付订单', value: integer(value.paidOrderCount), hint: '按支付日归属' },
    { label: '净销售额', value: money(value.netRevenue), hint: '支付金额 - 退款金额' },
    { label: '浏览至订单转化率', value: percent(value.browseOrderConversionPercent), hint: '订单量 / 商品详情 PV' }
  ]
  if (canProfit.value) {
    cards.push(
      { label: '毛利润', value: money(value.grossProfit), hint: value.missingCostItemCount ? '成本不完整' : '已扣除退款及成本' },
      { label: '毛利率', value: percent(value.grossMarginPercent), hint: value.missingCostItemCount ? '暂不可计算' : '毛利润 / 净销售额' }
    )
  }
  return cards
})

const chartOptions = computed(() => ({
  aria: { enabled: true, description: '按日展示浏览量和净销售额趋势' },
  tooltip: { trigger: 'axis' },
  legend: { data: ['浏览量', '净销售额'] },
  grid: { left: 20, right: 24, top: 46, bottom: 20, containLabel: true },
  xAxis: { type: 'category', data: trend.value.map((item) => item.day), boundaryGap: false },
  yAxis: [{ type: 'value', name: 'PV' }, { type: 'value', name: 'USD', axisLabel: { formatter: (v: number) => `$${v}` } }],
  series: [
    { name: '浏览量', type: 'line', smooth: true, connectNulls: false, data: trend.value.map((item) => query.scope === 'SITE' ? item.homePv : item.productDetailPv), itemStyle: { color: '#111827' } },
    { name: '净销售额', type: 'bar', yAxisIndex: 1, data: trend.value.map((item) => item.netRevenue == null ? null : item.netRevenue / 100), itemStyle: { color: '#c7a66a' } }
  ]
}))

const openAttention = (item: { spuId: number; riskType: string }) => {
  query.scope = 'PRODUCT'
  query.spuId = item.spuId
  query.riskType = item.riskType
  query.pageNo = 1
}

const applyProductFilters = () => {
  query.pageNo = 1
  void loadDashboard()
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
  <div class="furniture-dashboard" v-loading="loading" aria-live="polite">
    <el-result v-if="!canQuery" icon="warning" title="暂无数据看板查询权限" sub-title="请联系管理员授予 statistics:dashboard:query" />
    <template v-else>
      <header class="dashboard-header">
        <div>
          <p class="eyebrow">商业运营</p>
          <h1>数据看板</h1>
          <p>聚合家具商城访问、转化、销售、退款和利润，帮助运营人员快速找到值得关注的商品。</p>
        </div>
        <div class="header-actions">
          <el-button v-if="canExport" :loading="exporting" @click="exportRows(false)">导出经营数据</el-button>
          <el-button v-if="canProfitExport" type="primary" :loading="exporting" @click="exportRows(true)">导出利润数据</el-button>
        </div>
      </header>

      <section class="filter-bar" aria-label="数据筛选">
        <el-segmented v-model="query.scope" :options="[{ label: '全站', value: 'SITE' }, { label: '商品', value: 'PRODUCT' }]" />
        <div class="quick-ranges">
          <el-button v-for="item in quickRanges" :key="item.label" text @click="selectRange(item.days)">{{ item.label }}</el-button>
        </div>
        <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" :clearable="false" @change="applyDateRange" />
        <template v-if="query.scope === 'PRODUCT'">
          <el-input-number v-model="query.categoryId" :min="1" :controls="false" placeholder="分类 ID" aria-label="分类 ID" @change="applyProductFilters" />
          <el-input-number v-model="query.spuId" :min="1" :controls="false" placeholder="商品 SPU" aria-label="商品 SPU" @change="applyProductFilters" />
          <el-button v-if="query.categoryId || query.spuId" text @click="query.categoryId = undefined; query.spuId = undefined; applyProductFilters()">清除商品筛选</el-button>
        </template>
        <el-button type="primary" @click="loadDashboard">刷新</el-button>
      </section>

      <el-alert v-if="error" type="error" show-icon :closable="false" :title="error"><template #default><el-button text @click="loadDashboard">重试</el-button></template></el-alert>
      <el-alert v-if="summary && summary.trafficDataStatus !== 'COMPLETE'" class="quality-alert" type="warning" show-icon :closable="false" :title="`流量数据状态：${summary.trafficDataStatus}，未知值保持为—，不会伪装成 0。`" />
      <el-alert v-if="summary?.freshnessStatus === 'STALE'" class="quality-alert" type="error" show-icon :closable="false" title="数据已过期，当前保留最后一次成功结果，请关注聚合任务。" />

      <section class="metric-grid" aria-label="核心经营指标">
        <article v-for="card in metricCards" :key="card.label" class="metric-card">
          <span>{{ card.label }}</span><strong>{{ card.value }}</strong><small>{{ card.hint }}</small>
        </article>
      </section>

      <section class="dashboard-grid">
        <article class="panel trend-panel">
          <div class="panel-heading"><div><h2>销售与访问趋势</h2><p>时区 Asia/Shanghai · 金额 USD</p></div><span v-if="summary">更新于 {{ summary.asOf }}</span></div>
          <Echart v-if="trend.length" :height="340" :options="chartOptions" />
          <el-empty v-else description="当前范围暂无趋势数据" />
          <details v-if="trend.length" class="data-alternative"><summary>查看趋势数据表</summary><table><thead><tr><th>日期</th><th>浏览量</th><th>净销售额</th></tr></thead><tbody><tr v-for="item in trend" :key="item.day"><td>{{ item.day }}</td><td>{{ integer(query.scope === 'SITE' ? item.homePv : item.productDetailPv) }}</td><td>{{ money(item.netRevenue) }}</td></tr></tbody></table></details>
        </article>

        <article class="panel stage-panel">
          <div class="panel-heading"><div><h2>阶段概览</h2><p>不同阶段去重口径不同，不是同一批用户的漏斗。</p></div></div>
          <div v-if="stage" class="stage-list">
            <div v-for="item in stage.items" :key="item.stage" :class="{ muted: item.applicability === 'NOT_APPLICABLE' }"><span>{{ item.stage }}</span><strong>{{ integer(item.value) }}</strong><small>{{ item.unit }} · {{ item.dedupeScope }}</small></div>
          </div>
          <el-empty v-else description="暂无阶段数据" />
        </article>
      </section>

      <section class="panel attention-panel">
        <div class="panel-heading"><div><h2>运营关注</h2><p>规则提示仅用于定位问题，不是自动诊断。</p></div></div>
        <div v-if="attention?.items.length" class="attention-list">
          <button v-for="item in attention.items" :key="`${item.spuId}-${item.riskType}`" @click="openAttention(item)"><span>{{ item.riskType }}</span><strong>SPU {{ item.spuId }}</strong><small>{{ item.copy }}</small></button>
        </div>
        <el-empty v-else description="已评估的规则中，暂无商品命中当前关注条件" />
        <el-alert v-for="item in attention?.notEvaluated || []" :key="`${item.spuId}-${item.riskType}`" type="warning" :closable="false" :title="`${item.riskType}：${item.copy}`" />
      </section>

      <section v-if="query.scope === 'PRODUCT'" class="panel product-panel">
        <div class="panel-heading"><div><h2>商品经营明细</h2><p>点击运营关注项可快速聚焦对应商品。</p></div></div>
        <el-table :data="products" row-key="spuId" stripe @sort-change="changeProductSort">
          <el-table-column prop="productName" label="商品名称" min-width="220">
            <template #default="{ row }"><div class="product-identity"><el-image v-if="row.picUrl" :src="row.picUrl" fit="cover" /><span><strong>{{ row.productName || `SPU ${row.spuId}` }}</strong><small>SPU {{ row.spuId }} · 分类 {{ row.categoryId || '—' }}</small></span></div></template>
          </el-table-column>
          <el-table-column prop="spuId" label="SPU" min-width="100" sortable="custom" />
          <el-table-column prop="browseCount" label="详情 PV" min-width="100" sortable="custom"><template #default="{ row }">{{ integer(row.browseCount) }}</template></el-table-column>
          <el-table-column prop="cartCount" label="加购" min-width="90"><template #default="{ row }">{{ integer(row.cartCount) }}</template></el-table-column>
          <el-table-column prop="orderCount" label="支付订单" min-width="110" sortable="custom"><template #default="{ row }">{{ integer(row.orderCount) }}</template></el-table-column>
          <el-table-column prop="orderPayPrice" label="支付金额" min-width="130" sortable="custom"><template #default="{ row }">{{ money(row.orderPayPrice) }}</template></el-table-column>
          <el-table-column prop="afterSaleRefundPrice" label="退款金额" min-width="130" sortable="custom"><template #default="{ row }">{{ money(row.afterSaleRefundPrice) }}</template></el-table-column>
          <el-table-column prop="browseConvertPercent" label="转化率" min-width="100" sortable="custom"><template #default="{ row }">{{ percent(row.browseConvertPercent) }}</template></el-table-column>
          <template v-if="canProfit">
            <el-table-column prop="grossProfit" label="毛利润" min-width="130" sortable="custom"><template #default="{ row }">{{ money(row.grossProfit) }}</template></el-table-column>
            <el-table-column prop="grossMarginPercent" label="毛利率" min-width="100" sortable="custom"><template #default="{ row }">{{ percent(row.grossMarginPercent) }}</template></el-table-column>
            <el-table-column prop="profitDataQuality" label="成本质量" min-width="130" />
          </template>
        </el-table>
        <el-pagination v-if="productTotal" class="product-pagination" :current-page="query.pageNo" :page-size="query.pageSize" :page-sizes="[10, 20, 50, 100]" :total="productTotal" layout="total, sizes, prev, pager, next" @current-change="changeProductPage" @size-change="changeProductPageSize" />
        <el-empty v-if="!products.length" description="当前筛选范围暂无商品经营数据" />
      </section>
    </template>
  </div>
</template>

<style lang="scss" scoped>
.furniture-dashboard { min-height: 100%; padding: 28px; color: #17202a; background: #f5f4f0; }
.dashboard-header, .filter-bar, .panel, .metric-card { border: 1px solid #e3e0d8; background: #fff; box-shadow: 0 8px 26px rgb(31 41 55 / 4%); }
.dashboard-header { display: flex; justify-content: space-between; gap: 24px; padding: 28px; border-radius: 18px; }
.dashboard-header h1 { margin: 2px 0 8px; font-family: Georgia, serif; font-size: 34px; font-weight: 500; }
.dashboard-header p { max-width: 760px; margin: 0; color: #6b7280; line-height: 1.6; }
.eyebrow { color: #9a7b47 !important; font-size: 12px; font-weight: 700; letter-spacing: .16em; text-transform: uppercase; }
.header-actions, .quick-ranges { display: flex; align-items: center; gap: 8px; }
.filter-bar { display: flex; flex-wrap: wrap; align-items: center; gap: 14px; margin: 16px 0; padding: 14px 16px; border-radius: 14px; }
.quality-alert { margin: 10px 0; }
.metric-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin: 16px 0; }
.metric-card { display: flex; flex-direction: column; min-height: 122px; padding: 20px; border-radius: 14px; }
.metric-card span, .metric-card small { color: #6b7280; }.metric-card strong { margin: 12px 0 5px; font-family: Georgia, serif; font-size: 28px; font-weight: 500; }
.dashboard-grid { display: grid; grid-template-columns: minmax(0, 2fr) minmax(300px, 1fr); gap: 16px; }
.panel { margin-bottom: 16px; padding: 22px; border-radius: 16px; }
.panel-heading { display: flex; justify-content: space-between; gap: 20px; margin-bottom: 16px; }.panel-heading h2 { margin: 0 0 5px; font-size: 18px; }.panel-heading p, .panel-heading span { margin: 0; color: #6b7280; font-size: 13px; }
.stage-list { display: grid; gap: 8px; }.stage-list > div { display: grid; grid-template-columns: 1fr auto; gap: 3px 16px; padding: 13px; border-radius: 10px; background: #f7f6f2; }.stage-list small { grid-column: 1 / -1; color: #7c828b; }.stage-list .muted { opacity: .5; }
.attention-list { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; }.attention-list button { display: flex; flex-direction: column; gap: 6px; padding: 15px; text-align: left; border: 1px solid #eadfcb; border-radius: 12px; background: #fffaf2; cursor: pointer; }.attention-list span { color: #9a5d27; font-size: 11px; font-weight: 700; }.attention-list small { color: #6b7280; }
.data-alternative { margin-top: 10px; }.data-alternative table { width: 100%; margin-top: 8px; border-collapse: collapse; }.data-alternative th, .data-alternative td { padding: 7px; text-align: left; border-bottom: 1px solid #eee; }
.product-identity { display: flex; align-items: center; gap: 10px; }.product-identity .el-image { width: 42px; height: 42px; border-radius: 8px; background: #f3f4f6; }.product-identity span { display: flex; flex-direction: column; gap: 3px; }.product-identity small { color: #7c828b; }.product-pagination { justify-content: flex-end; margin-top: 18px; }
@media (max-width: 1100px) { .metric-grid { grid-template-columns: repeat(2, 1fr); }.dashboard-grid { grid-template-columns: 1fr; }.attention-list { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 700px) { .furniture-dashboard { padding: 12px; }.dashboard-header { flex-direction: column; padding: 20px; }.header-actions, .filter-bar { align-items: stretch; flex-direction: column; }.metric-grid, .attention-list { grid-template-columns: 1fr; }.quick-ranges { flex-wrap: wrap; } }
</style>
