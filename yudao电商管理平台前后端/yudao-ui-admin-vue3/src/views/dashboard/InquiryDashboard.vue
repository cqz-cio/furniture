<script lang="ts" setup>
import dayjs from 'dayjs'
import type { EChartsOption, LineSeriesOption } from 'echarts'
import { useRouter } from 'vue-router'
import * as ClueApi from '@/api/crm/clue'
import { InquiryProcessStatus } from '@/api/crm/clue'
import * as ProductSpuApi from '@/api/mall/product/spu'
import {
  DashboardApi,
  type DashboardProduct,
  type DashboardQuery,
  type DashboardSummary,
  type DashboardTrendItem
} from '@/api/mall/statistics/dashboard'
import { getSeoMetadataPage } from '@/api/seo/metadata'
import { checkPermi } from '@/utils/permission'

defineOptions({ name: 'InquiryDashboard' })

type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'
type InquiryPeriodSummary = {
  total: number
  pending: number
  processing: number
  processed: number
  invalid: number
}

const router = useRouter()
const loading = ref(true)
const error = ref('')
const trafficSummary = ref<DashboardSummary | null>(null)
const trafficTrend = ref<DashboardTrendItem[]>([])
const inquirySummary = ref<InquiryPeriodSummary>({
  total: 0,
  pending: 0,
  processing: 0,
  processed: 0,
  invalid: 0
})
const recentInquiries = ref<ClueApi.ClueVO[]>([])
const popularProducts = ref<DashboardProduct[]>([])
const productCounts = ref<Record<string, number>>({})
const seoCounts = ref({ published: 0, draft: 0 })

const canReadTraffic = checkPermi(['statistics:dashboard:query'])
const canReadInquiry = checkPermi(['crm:clue:query'])
const canReadProduct = checkPermi(['product:spu:query'])
const canReadSeo = checkPermi(['seo:metadata:query'])

const today = dayjs()
const dateRange = ref<[string, string]>([
  today.subtract(29, 'day').format('YYYY-MM-DD'),
  today.format('YYYY-MM-DD')
])
const quickRanges = [
  { label: '今日', days: 1 },
  { label: '近 7 日', days: 7 },
  { label: '近 30 日', days: 30 },
  { label: '近 90 日', days: 90 }
]

const selectedPeriodLabel = computed(() => `${dateRange.value[0]} 至 ${dateRange.value[1]}`)
const integer = (value: number | null | undefined) =>
  value == null ? '—' : value.toLocaleString('zh-CN')
const percent = (value: number | null | undefined) =>
  value == null || !Number.isFinite(value) ? '—' : `${value.toFixed(2)}%`
const formatTime = (value?: Date | string | null) =>
  value && dayjs(value).isValid() ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'
const go = (path: string) => router.push(path)
const openInquiry = (id: number) => router.push({ name: 'CrmClueDetail', params: { id } })

const validInquiryCount = computed(() => inquirySummary.value.total - inquirySummary.value.invalid)
const inquiryCompletionRate = computed(() =>
  validInquiryCount.value > 0
    ? (inquirySummary.value.processed / validInquiryCount.value) * 100
    : null
)
const visitorToInquiryRate = computed(() => {
  const visitors = trafficSummary.value?.homeUv
  if (!visitors || visitors <= 0) return null
  return (inquirySummary.value.total / visitors) * 100
})
const trafficWarning = computed(() => {
  if (!canReadTraffic || loading.value) return ''
  if (!trafficSummary.value || trafficSummary.value.trafficDataStatus === 'UNAVAILABLE') {
    return '官网访问统计尚未接通或当前周期没有可用数据。询盘统计仍可使用，但访客、商品关注度和访客转询盘率暂不能用于经营判断。'
  }
  if (trafficSummary.value.trafficDataStatus === 'PARTIAL') {
    return '官网访问数据当前只有部分覆盖，请结合数据截至时间谨慎判断。'
  }
  return ''
})

const coreMetrics = computed(() => [
  {
    label: '新增询盘',
    value: canReadInquiry ? integer(inquirySummary.value.total) : '—',
    hint: selectedPeriodLabel.value,
    path: '/crm/clue'
  },
  {
    label: '待回复',
    value: canReadInquiry ? integer(inquirySummary.value.pending) : '—',
    hint: '尚未开始处理',
    path: '/crm/clue'
  },
  {
    label: '跟进中',
    value: canReadInquiry ? integer(inquirySummary.value.processing) : '—',
    hint: '客户正在持续沟通',
    path: '/crm/clue'
  },
  {
    label: '询盘处理完成率',
    value: canReadInquiry ? percent(inquiryCompletionRate.value) : '—',
    hint: '已处理 / 有效询盘',
    path: '/crm/clue'
  }
])

const trafficMetrics = computed(() => [
  {
    label: '网站访客',
    value: canReadTraffic ? integer(trafficSummary.value?.homeUv) : '—',
    hint: '首页 UV'
  },
  {
    label: '商品访客',
    value: canReadTraffic ? integer(trafficSummary.value?.productDetailUv) : '—',
    hint: '商品详情 UV'
  },
  {
    label: '访客转询盘率',
    value: canReadTraffic && canReadInquiry ? percent(visitorToInquiryRate.value) : '—',
    hint: '询盘数 / 首页访客'
  }
])

const chartOptions = computed<EChartsOption>(() => {
  const series: LineSeriesOption[] = [
    {
      name: '网站访客',
      type: 'line',
      smooth: true,
      connectNulls: false,
      data: trafficTrend.value.map((item) => item.homeUv),
      itemStyle: { color: '#176bdb' },
      areaStyle: { color: 'rgba(23, 107, 219, 0.08)' }
    },
    {
      name: '商品访客',
      type: 'line',
      smooth: true,
      connectNulls: false,
      data: trafficTrend.value.map((item) => item.productDetailUv),
      itemStyle: { color: '#8b6a35' }
    }
  ]
  return {
    aria: { enabled: true, description: '按日展示网站和商品访客趋势' },
    tooltip: { trigger: 'axis' },
    legend: { data: ['网站访客', '商品访客'], top: 0 },
    grid: { left: 16, right: 20, top: 42, bottom: 10, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: trafficTrend.value.map((item) => item.day.slice(5))
    },
    yAxis: { type: 'value', minInterval: 1 },
    series
  }
})

const statusMeta = (status: InquiryProcessStatus): { label: string; type: TagType } => {
  const statuses: Record<number, { label: string; type: TagType }> = {
    [InquiryProcessStatus.PENDING]: { label: '待处理', type: 'warning' },
    [InquiryProcessStatus.PROCESSING]: { label: '处理中', type: 'primary' },
    [InquiryProcessStatus.PROCESSED]: { label: '已处理', type: 'success' },
    [InquiryProcessStatus.INVALID]: { label: '无效', type: 'info' }
  }
  return statuses[status] || statuses[InquiryProcessStatus.PENDING]
}

const selectRange = (days: number) => {
  const end = dayjs()
  const start = end.subtract(days - 1, 'day')
  dateRange.value = [start.format('YYYY-MM-DD'), end.format('YYYY-MM-DD')]
  void loadDashboard()
}

const clueParams = (processStatus?: InquiryProcessStatus) => ({
  pageNo: 1,
  pageSize: 1,
  processStatus,
  testData: false,
  submittedAt: [`${dateRange.value[0]} 00:00:00`, `${dateRange.value[1]} 23:59:59`]
})

const loadInquiryData = async () => {
  const [all, pending, processing, processed, invalid, recent] = await Promise.all([
    ClueApi.getCluePage(clueParams()),
    ClueApi.getCluePage(clueParams(InquiryProcessStatus.PENDING)),
    ClueApi.getCluePage(clueParams(InquiryProcessStatus.PROCESSING)),
    ClueApi.getCluePage(clueParams(InquiryProcessStatus.PROCESSED)),
    ClueApi.getCluePage(clueParams(InquiryProcessStatus.INVALID)),
    ClueApi.getCluePage({ ...clueParams(), pageSize: 8 })
  ])
  inquirySummary.value = {
    total: all.total || 0,
    pending: pending.total || 0,
    processing: processing.total || 0,
    processed: processed.total || 0,
    invalid: invalid.total || 0
  }
  recentInquiries.value = recent.list || []
}

const loadTrafficData = async () => {
  const query: DashboardQuery = {
    scope: 'SITE',
    startDate: dateRange.value[0],
    endDate: dateRange.value[1],
    compare: false
  }
  const productQuery: DashboardQuery = {
    ...query,
    scope: 'PRODUCT',
    pageNo: 1,
    pageSize: 6,
    sortField: 'browseCount',
    sortOrder: 'desc'
  }
  const [summary, trend, productPage] = await Promise.all([
    DashboardApi.getSummary(query),
    DashboardApi.getTrend(query),
    DashboardApi.getProductPage(productQuery)
  ])
  trafficSummary.value = summary
  trafficTrend.value = trend || []
  popularProducts.value = productPage.list || []
}

const loadSupportingData = async () => {
  const requests: Promise<unknown>[] = []
  if (canReadProduct) {
    requests.push(
      ProductSpuApi.getTabsCount().then((counts) => {
        productCounts.value = counts || {}
      })
    )
  }
  if (canReadSeo) {
    requests.push(
      Promise.all([
        getSeoMetadataPage({ pageNo: 1, pageSize: 1, publishStatus: 'PUBLISHED' }),
        getSeoMetadataPage({ pageNo: 1, pageSize: 1, publishStatus: 'DRAFT' })
      ]).then(([published, draft]) => {
        seoCounts.value = { published: published.total || 0, draft: draft.total || 0 }
      })
    )
  }
  await Promise.all(requests)
}

const loadDashboard = async () => {
  loading.value = true
  error.value = ''
  const requests: Promise<unknown>[] = []
  if (canReadInquiry) requests.push(loadInquiryData())
  if (canReadTraffic) requests.push(loadTrafficData())
  if (canReadProduct || canReadSeo) requests.push(loadSupportingData())
  const results = await Promise.allSettled(requests)
  if (results.some((result) => result.status === 'rejected')) {
    error.value = '部分数据加载失败，已保留可用的询盘或流量结果。'
  }
  loading.value = false
}

onMounted(loadDashboard)
</script>

<template>
  <div class="inquiry-dashboard" v-loading="loading" aria-live="polite">
    <header class="inquiry-dashboard__header">
      <div>
        <div class="inquiry-dashboard__title-row">
          <h1>数据看板</h1>
          <el-tag effect="plain" round>B2B 询盘型</el-tag>
        </div>
        <p>先看询盘处理效率，再定位网站流量、商品关注度与 SEO 内容覆盖。</p>
        <span class="inquiry-dashboard__period">{{ selectedPeriodLabel }}</span>
      </div>
      <el-space wrap>
        <el-button :loading="loading" @click="loadDashboard">刷新数据</el-button>
        <el-button v-if="canReadInquiry" type="primary" @click="go('/crm/clue')">
          进入询盘中心
        </el-button>
      </el-space>
    </header>

    <section class="inquiry-dashboard__filters" aria-label="数据筛选">
      <span>统计周期</span>
      <div class="inquiry-dashboard__quick-ranges">
        <el-button
          v-for="item in quickRanges"
          :key="item.label"
          text
          @click="selectRange(item.days)"
        >
          {{ item.label }}
        </el-button>
      </div>
      <el-date-picker
        v-model="dateRange"
        :clearable="false"
        end-placeholder="结束日期"
        range-separator="至"
        start-placeholder="开始日期"
        type="daterange"
        value-format="YYYY-MM-DD"
        @change="loadDashboard"
      />
    </section>

    <el-alert v-if="error" :closable="false" show-icon :title="error" type="warning" />
    <el-alert
      v-if="trafficWarning"
      :closable="false"
      show-icon
      :title="trafficWarning"
      type="warning"
    />

    <section class="inquiry-dashboard__metrics" aria-label="询盘指标">
      <button v-for="item in coreMetrics" :key="item.label" type="button" @click="go(item.path)">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <small>{{ item.hint }}</small>
      </button>
    </section>

    <section class="inquiry-dashboard__main-grid">
      <el-card class="inquiry-dashboard__panel inquiry-dashboard__trend" shadow="never">
        <template #header>
          <div class="inquiry-dashboard__panel-title">
            <div>
              <strong>获客流量趋势</strong>
              <small>按日统计网站与商品详情访客</small>
            </div>
            <el-tag v-if="trafficSummary" effect="plain">
              数据截至 {{ formatTime(trafficSummary.asOf) }}
            </el-tag>
          </div>
        </template>
        <el-empty v-if="!canReadTraffic" description="暂无流量看板权限" />
        <el-empty v-else-if="!trafficTrend.length && !loading" description="当前周期暂无流量数据" />
        <Echart v-else :height="320" :options="chartOptions" />
      </el-card>

      <el-card class="inquiry-dashboard__panel" shadow="never">
        <template #header>
          <div class="inquiry-dashboard__panel-title">
            <div>
              <strong>获客效率</strong>
              <small>将流量和询盘处理放在同一周期内观察</small>
            </div>
          </div>
        </template>
        <div class="inquiry-dashboard__traffic-metrics">
          <div v-for="item in trafficMetrics" :key="item.label">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
            <small>{{ item.hint }}</small>
          </div>
        </div>
        <div class="inquiry-dashboard__funnel">
          <div>
            <span>全部询盘</span>
            <strong>{{ integer(inquirySummary.total) }}</strong>
          </div>
          <div>
            <span>有效询盘</span>
            <strong>{{ integer(validInquiryCount) }}</strong>
          </div>
          <div>
            <span>已处理</span>
            <strong>{{ integer(inquirySummary.processed) }}</strong>
          </div>
        </div>
      </el-card>
    </section>

    <section class="inquiry-dashboard__secondary-grid">
      <el-card class="inquiry-dashboard__panel" shadow="never">
        <template #header>
          <div class="inquiry-dashboard__panel-title">
            <div>
              <strong>最新询盘</strong>
              <small>当前统计周期内的最新提交</small>
            </div>
            <el-button v-if="canReadInquiry" text type="primary" @click="go('/crm/clue')">
              查看全部
            </el-button>
          </div>
        </template>
        <el-empty v-if="!canReadInquiry" description="暂无询盘查询权限" />
        <el-empty v-else-if="!recentInquiries.length && !loading" description="当前周期暂无询盘" />
        <el-table v-else :data="recentInquiries">
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusMeta(row.processStatus).type" effect="light">
                {{ statusMeta(row.processStatus).label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="主题" min-width="210">
            <template #default="{ row }">
              <el-link :underline="false" type="primary" @click="openInquiry(row.id)">
                {{ row.inquirySubject || row.name || '未命名询盘' }}
              </el-link>
            </template>
          </el-table-column>
          <el-table-column label="公司" min-width="150">
            <template #default="{ row }">{{ row.companyName || '待补充' }}</template>
          </el-table-column>
          <el-table-column prop="contactName" label="联系人" width="120" />
          <el-table-column label="提交时间" width="165">
            <template #default="{ row }">{{
              formatTime(row.submittedAt || row.createTime)
            }}</template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card class="inquiry-dashboard__panel" shadow="never">
        <template #header>
          <div class="inquiry-dashboard__panel-title">
            <div>
              <strong>内容运营概览</strong>
              <small>商品展示与 SEO 元数据状态</small>
            </div>
          </div>
        </template>
        <div class="inquiry-dashboard__content-overview">
          <button v-if="canReadProduct" type="button" @click="go('/mall/product/spu')">
            <Icon icon="ep:goods" :size="22" />
            <span>在售商品</span>
            <strong>{{ integer(productCounts['0'] || 0) }}</strong>
            <small>管理 B2B 网站展示内容</small>
          </button>
          <button v-if="canReadSeo" type="button" @click="go('/seo/metadata')">
            <Icon icon="ep:promotion" :size="22" />
            <span>已发布 SEO</span>
            <strong>{{ integer(seoCounts.published) }}</strong>
            <small>已生效的元数据</small>
          </button>
          <button v-if="canReadSeo" type="button" @click="go('/seo/metadata')">
            <Icon icon="ep:edit-pen" :size="22" />
            <span>SEO 草稿</span>
            <strong>{{ integer(seoCounts.draft) }}</strong>
            <small>检查后发布</small>
          </button>
          <el-empty v-if="!canReadProduct && !canReadSeo" description="暂无商品或 SEO 查询权限" />
        </div>
      </el-card>
    </section>

    <el-card v-if="canReadTraffic" class="inquiry-dashboard__panel" shadow="never">
      <template #header>
        <div class="inquiry-dashboard__panel-title">
          <div>
            <strong>高关注商品</strong>
            <small>按当前周期商品详情浏览量排序</small>
          </div>
          <el-button v-if="canReadProduct" text type="primary" @click="go('/mall/product/spu')">
            商品中心
          </el-button>
        </div>
      </template>
      <el-empty v-if="!popularProducts.length && !loading" description="当前周期暂无商品浏览数据" />
      <el-table v-else :data="popularProducts">
        <el-table-column label="商品" min-width="260">
          <template #default="{ row }">
            <div class="inquiry-dashboard__product">
              <img v-if="row.picUrl" :alt="row.productName || '商品图片'" :src="row.picUrl" />
              <span v-else><Icon icon="ep:picture" :size="22" /></span>
              <strong>{{ row.productName || `商品 #${row.spuId}` }}</strong>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="详情浏览" width="140">
          <template #default="{ row }">{{ integer(row.browseCount) }}</template>
        </el-table-column>
        <el-table-column label="访客数" width="130">
          <template #default="{ row }">{{ integer(row.browseUserCount) }}</template>
        </el-table-column>
        <el-table-column label="数据状态" width="140">
          <template #default="{ row }">
            <el-tag
              :type="row.trafficDataStatus === 'COMPLETE' ? 'success' : 'warning'"
              effect="plain"
            >
              {{ row.trafficDataStatus === 'COMPLETE' ? '完整' : '部分可用' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.inquiry-dashboard {
  display: grid;
  gap: 14px;
  color: var(--furniture-admin-ink);
}

.inquiry-dashboard__header,
.inquiry-dashboard__filters,
.inquiry-dashboard__panel-title,
.inquiry-dashboard__title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.inquiry-dashboard__header {
  align-items: flex-start;
}

.inquiry-dashboard__title-row {
  justify-content: flex-start;
}

.inquiry-dashboard__header h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 650;
}

.inquiry-dashboard__header p {
  margin: 8px 0 4px;
  color: var(--furniture-admin-body);
  font-size: 13px;
}

.inquiry-dashboard__period,
.inquiry-dashboard__panel-title small {
  color: var(--furniture-admin-muted);
  font-size: 12px;
}

.inquiry-dashboard__filters {
  justify-content: flex-start;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid var(--furniture-admin-border);
  border-radius: var(--furniture-admin-radius);
}

.inquiry-dashboard__filters > span {
  font-size: 12px;
  font-weight: 600;
}

.inquiry-dashboard__quick-ranges {
  display: flex;
}

.inquiry-dashboard__metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.inquiry-dashboard__metrics button {
  padding: 16px;
  text-align: left;
  cursor: pointer;
  background: #fff;
  border: 1px solid var(--furniture-admin-border);
  border-radius: var(--furniture-admin-radius);
}

.inquiry-dashboard__metrics span,
.inquiry-dashboard__traffic-metrics span,
.inquiry-dashboard__content-overview span {
  color: var(--furniture-admin-muted);
  font-size: 12px;
}

.inquiry-dashboard__metrics strong {
  display: block;
  margin: 10px 0 8px;
  font-size: 28px;
  font-weight: 650;
}

.inquiry-dashboard__metrics small,
.inquiry-dashboard__traffic-metrics small,
.inquiry-dashboard__content-overview small {
  color: var(--furniture-admin-body);
  font-size: 12px;
}

.inquiry-dashboard__main-grid,
.inquiry-dashboard__secondary-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(310px, 0.75fr);
  gap: 14px;
}

.inquiry-dashboard__panel {
  overflow: hidden;
  border: 1px solid var(--furniture-admin-border);
}

.inquiry-dashboard__panel-title > div {
  display: grid;
  gap: 4px;
}

.inquiry-dashboard__traffic-metrics,
.inquiry-dashboard__content-overview {
  display: grid;
  gap: 10px;
}

.inquiry-dashboard__traffic-metrics > div,
.inquiry-dashboard__content-overview button {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 5px 10px;
  padding: 12px;
  text-align: left;
  background: var(--furniture-admin-panel-soft);
  border: 1px solid var(--furniture-admin-border);
  border-radius: 6px;
}

.inquiry-dashboard__traffic-metrics strong,
.inquiry-dashboard__content-overview strong {
  font-size: 22px;
  font-weight: 650;
}

.inquiry-dashboard__traffic-metrics small,
.inquiry-dashboard__content-overview small {
  grid-column: 1 / -1;
}

.inquiry-dashboard__content-overview button {
  cursor: pointer;
}

.inquiry-dashboard__content-overview button > svg {
  grid-row: 1 / span 2;
  grid-column: 2;
}

.inquiry-dashboard__funnel {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-top: 14px;
}

.inquiry-dashboard__funnel div {
  padding: 10px;
  text-align: center;
  background: #fff;
  border: 1px solid var(--furniture-admin-border);
  border-radius: 6px;
}

.inquiry-dashboard__funnel span {
  display: block;
  color: var(--furniture-admin-muted);
  font-size: 11px;
}

.inquiry-dashboard__funnel strong {
  display: block;
  margin-top: 7px;
  font-size: 19px;
}

.inquiry-dashboard__product {
  display: flex;
  align-items: center;
  gap: 10px;
}

.inquiry-dashboard__product img,
.inquiry-dashboard__product > span {
  display: grid;
  width: 42px;
  height: 42px;
  flex: 0 0 auto;
  background: var(--furniture-admin-panel-soft);
  border-radius: 5px;
  object-fit: cover;
  place-items: center;
}

@media (width <= 1180px) {
  .inquiry-dashboard__metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .inquiry-dashboard__main-grid,
  .inquiry-dashboard__secondary-grid {
    grid-template-columns: 1fr;
  }
}

@media (width <= 720px) {
  .inquiry-dashboard__header,
  .inquiry-dashboard__filters {
    align-items: stretch;
    flex-direction: column;
  }

  .inquiry-dashboard__metrics {
    grid-template-columns: 1fr;
  }

  .inquiry-dashboard__filters :deep(.el-date-editor) {
    width: 100%;
  }
}
</style>
