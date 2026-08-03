<template>
  <section class="furniture-admin-page" v-loading="loading">
    <div class="furniture-admin-page-head">
      <div>
        <p class="furniture-admin-kicker">Oakved Console · B2B</p>
        <h1>询盘运营控制台</h1>
        <p>聚焦客户询盘、商品展示与 SEO 获客，优先处理待回复和跟进中的商机。</p>
      </div>
      <el-space wrap>
        <el-tag :type="summary.pendingCount > 0 ? 'warning' : 'success'" effect="plain">
          {{ summary.pendingCount > 0 ? `${summary.pendingCount} 条询盘待回复` : '询盘处理正常' }}
        </el-tag>
        <el-button v-if="canReadInquiry" type="primary" @click="go('/crm/clue')">
          处理询盘
        </el-button>
        <el-button v-if="canReadProduct" @click="go('/mall/product/spu')">维护商品</el-button>
      </el-space>
    </div>

    <el-alert
      v-if="loadError"
      class="b2b-home-alert"
      :closable="false"
      show-icon
      title="部分经营数据暂时无法加载，可刷新后重试。"
      type="warning"
    />

    <div class="furniture-admin-metrics">
      <button
        v-for="item in metrics"
        :key="item.label"
        class="furniture-admin-metric"
        type="button"
        @click="go(item.path)"
      >
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <small>{{ item.hint }}</small>
      </button>
    </div>

    <div class="furniture-admin-work-grid">
      <el-card class="furniture-admin-panel" shadow="never">
        <template #header>
          <div class="furniture-admin-panel-title">
            <span>今日待办</span>
            <el-tag v-if="summary.pendingCount" type="warning" effect="plain">优先回复</el-tag>
            <span v-else>暂无紧急询盘</span>
          </div>
        </template>
        <div class="furniture-admin-status-list">
          <button
            v-for="item in queues"
            :key="item.title"
            class="furniture-admin-status-row"
            type="button"
            @click="go(item.path)"
          >
            <span>
              <strong>{{ item.title }}</strong>
              <small>{{ item.description }}</small>
            </span>
            <el-tag :type="item.type" effect="light">{{ item.count }}</el-tag>
          </button>
        </div>
      </el-card>

      <el-card class="furniture-admin-panel" shadow="never">
        <template #header>
          <div class="furniture-admin-panel-title">
            <span>常用工作台</span>
            <span>B2B 高频入口</span>
          </div>
        </template>
        <div class="furniture-admin-action-grid">
          <button
            v-for="item in actions"
            :key="item.title"
            class="furniture-admin-action-card"
            type="button"
            @click="go(item.path)"
          >
            <Icon :icon="item.icon" :size="22" />
            <span>{{ item.title }}</span>
            <small>{{ item.description }}</small>
          </button>
        </div>
      </el-card>
    </div>

    <el-card
      v-if="canReadInquiry"
      class="furniture-admin-table-panel b2b-recent-panel"
      shadow="never"
    >
      <template #header>
        <div class="furniture-admin-panel-title">
          <span>最新询盘</span>
          <el-button text type="primary" @click="go('/crm/clue')">查看全部</el-button>
        </div>
      </template>
      <el-empty v-if="!recentInquiries.length && !loading" description="暂无询盘" />
      <el-table v-else :data="recentInquiries">
        <el-table-column label="状态" width="105">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.processStatus).type" effect="light">
              {{ statusMeta(row.processStatus).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="询盘主题" min-width="220">
          <template #default="{ row }">
            <el-link :underline="false" type="primary" @click="openInquiry(row.id)">
              {{ row.inquirySubject || row.name || '未命名询盘' }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column label="公司" min-width="170">
          <template #default="{ row }">{{ row.companyName || '待补充' }}</template>
        </el-table-column>
        <el-table-column prop="contactName" label="联系人" width="130" />
        <el-table-column label="来源页面" min-width="190">
          <template #default="{ row }">{{ row.sourcePage || '直接询盘' }}</template>
        </el-table-column>
        <el-table-column label="提交时间" width="175">
          <template #default="{ row }">{{
            formatTime(row.submittedAt || row.createTime)
          }}</template>
        </el-table-column>
        <el-table-column label="负责人" width="120">
          <template #default="{ row }">{{ row.ownerUserName || '待分配' }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>

<script lang="ts" setup>
import dayjs from 'dayjs'
import { useRouter } from 'vue-router'
import * as ClueApi from '@/api/crm/clue'
import { InquiryProcessStatus } from '@/api/crm/clue'
import * as ProductSpuApi from '@/api/mall/product/spu'
import { getSeoMetadataPage } from '@/api/seo/metadata'
import { checkPermi } from '@/utils/permission'

defineOptions({ name: 'B2BHome' })

type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'

const router = useRouter()
const loading = ref(true)
const loadError = ref(false)
const recentInquiries = ref<ClueApi.ClueVO[]>([])
const productCounts = ref<Record<string, number>>({})
const seoPublishedCount = ref(0)
const seoDraftCount = ref(0)
const summary = ref<ClueApi.InquirySummaryVO>({
  totalCount: 0,
  pendingCount: 0,
  processingCount: 0,
  processedCount: 0,
  invalidCount: 0
})

const canReadInquiry = checkPermi(['crm:clue:query'])
const canReadProduct = checkPermi(['product:spu:query'])
const canReadSeo = checkPermi(['seo:metadata:query'])
const canReadCustomer = checkPermi(['crm:customer:query'])
const canReadContact = checkPermi(['crm:contact:query'])

const go = (path: string) => router.push(path)
const openInquiry = (id: number) => router.push({ name: 'CrmClueDetail', params: { id } })
const formatTime = (value?: Date | string) =>
  value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'

const metrics = computed(() => [
  {
    label: '全部询盘',
    value: canReadInquiry ? summary.value.totalCount : '—',
    hint: '当前可见的客户询盘',
    path: '/crm/clue'
  },
  {
    label: '待回复',
    value: canReadInquiry ? summary.value.pendingCount : '—',
    hint: '尚未开始处理',
    path: '/crm/clue'
  },
  {
    label: '跟进中',
    value: canReadInquiry ? summary.value.processingCount : '—',
    hint: '需要持续沟通',
    path: '/crm/clue'
  },
  {
    label: '在售商品',
    value: canReadProduct ? productCounts.value['0'] || 0 : '—',
    hint: 'B2B 网站当前展示',
    path: '/mall/product/spu'
  }
])

const queues = computed<
  Array<{
    title: string
    description: string
    count: number | string
    type: TagType
    path: string
  }>
>(() => {
  const items: Array<{
    title: string
    description: string
    count: number | string
    type: TagType
    path: string
  }> = []
  if (canReadInquiry) {
    items.push(
      {
        title: '待首次回复',
        description: '优先确认客户需求、产品与联系方式',
        count: summary.value.pendingCount,
        type: summary.value.pendingCount ? 'warning' : 'success',
        path: '/crm/clue'
      },
      {
        title: '跟进中询盘',
        description: '补充报价、材质、交期与运输信息',
        count: summary.value.processingCount,
        type: 'primary',
        path: '/crm/clue'
      }
    )
  }
  if (canReadSeo) {
    items.push({
      title: 'SEO 内容待发布',
      description: '检查标题、描述、关键词与索引设置',
      count: seoDraftCount.value,
      type: seoDraftCount.value ? 'info' : 'success',
      path: '/seo/metadata'
    })
  }
  if (!items.length) {
    items.push({
      title: '暂无可见待办',
      description: '请联系管理员配置询盘、商品或 SEO 查询权限',
      count: '—',
      type: 'info',
      path: '/index'
    })
  }
  return items
})

const actions = computed(() => [
  ...(canReadInquiry
    ? [
        {
          title: '询盘中心',
          description: '回复、跟进与转化客户',
          icon: 'ep:message',
          path: '/crm/clue'
        }
      ]
    : []),
  ...(canReadProduct
    ? [
        {
          title: '商品中心',
          description: '产品、分类与展示内容',
          icon: 'ep:goods',
          path: '/mall/product/spu'
        }
      ]
    : []),
  ...(canReadSeo
    ? [
        {
          title: 'SEO 管理',
          description: `已发布 ${seoPublishedCount.value} 项元数据`,
          icon: 'ep:promotion',
          path: '/seo/metadata'
        }
      ]
    : []),
  ...(canReadCustomer
    ? [
        {
          title: '客户档案',
          description: '维护公司与客户信息',
          icon: 'ep:office-building',
          path: '/crm/customer'
        }
      ]
    : []),
  ...(canReadContact
    ? [{ title: '联系人', description: '维护客户联系方式', icon: 'ep:user', path: '/crm/contact' }]
    : []),
  {
    title: '数据看板',
    description: '查看询盘、流量与转化',
    icon: 'ep:data-analysis',
    path: '/dashboard'
  }
])

const statusMeta = (status: InquiryProcessStatus): { label: string; type: TagType } => {
  const statuses: Record<number, { label: string; type: TagType }> = {
    [InquiryProcessStatus.PENDING]: { label: '待处理', type: 'warning' },
    [InquiryProcessStatus.PROCESSING]: { label: '处理中', type: 'primary' },
    [InquiryProcessStatus.PROCESSED]: { label: '已处理', type: 'success' },
    [InquiryProcessStatus.INVALID]: { label: '无效', type: 'info' }
  }
  return statuses[status] || statuses[InquiryProcessStatus.PENDING]
}

const loadOverview = async () => {
  loading.value = true
  loadError.value = false
  const requests: Promise<void>[] = []
  if (canReadInquiry) {
    requests.push(
      Promise.all([
        ClueApi.getInquirySummary(),
        ClueApi.getCluePage({ pageNo: 1, pageSize: 5 })
      ]).then(([aggregate, page]) => {
        summary.value = aggregate
        recentInquiries.value = page.list || []
      })
    )
  }
  if (canReadProduct) {
    requests.push(
      ProductSpuApi.getTabsCount().then((counts) => (productCounts.value = counts || {}))
    )
  }
  if (canReadSeo) {
    requests.push(
      Promise.all([
        getSeoMetadataPage({ pageNo: 1, pageSize: 1, publishStatus: 'PUBLISHED' }),
        getSeoMetadataPage({ pageNo: 1, pageSize: 1, publishStatus: 'DRAFT' })
      ]).then(([published, draft]) => {
        seoPublishedCount.value = published.total || 0
        seoDraftCount.value = draft.total || 0
      })
    )
  }
  const results = await Promise.allSettled(requests)
  loadError.value = results.some((result) => result.status === 'rejected')
  loading.value = false
}

onMounted(loadOverview)
</script>

<style scoped>
.b2b-home-alert {
  margin-bottom: 12px;
}

.b2b-recent-panel {
  margin-top: 12px;
}
</style>
