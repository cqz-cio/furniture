<template>
  <div class="inquiry-summary">
    <button
      v-for="card in summaryCards"
      :key="card.key"
      class="summary-card"
      :class="{ 'is-active': queryParams.processStatus === card.status }"
      :aria-pressed="queryParams.processStatus === card.status"
      type="button"
      @click="filterByStatus(card.status)"
    >
      <span class="summary-card__icon" aria-hidden="true">
        <Icon :icon="card.icon" :size="17" />
      </span>
      <span class="summary-card__label">{{ card.label }}</span>
      <strong>{{ card.value }}</strong>
      <small>{{ card.hint }}</small>
    </button>
  </div>

  <InquiryMailHealthAlert ref="mailHealthRef" @configure="openMailSettings" />

  <el-alert class="mb-16px" :closable="false" show-icon type="info">
    <template #title>经营指标默认排除测试数据</template>
    <span>
      已隔离 {{ summary.testDataCount }} 条 TEST / QA / E2E
      询盘；可在“数据范围”中单独查看和纠正标记。
    </span>
  </el-alert>

  <ContentWrap>
    <el-form
      ref="queryFormRef"
      class="-mb-15px"
      :model="queryParams"
      :inline="true"
      label-width="76px"
    >
      <el-form-item label="公司名称" prop="companyName">
        <el-input
          v-model="queryParams.companyName"
          class="!w-220px"
          clearable
          placeholder="请输入公司名称"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="联系人" prop="contactName">
        <el-input
          v-model="queryParams.contactName"
          class="!w-200px"
          clearable
          placeholder="请输入联系人"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="邮箱" prop="email">
        <el-input
          v-model="queryParams.email"
          class="!w-220px"
          clearable
          placeholder="请输入邮箱"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="询盘主题" prop="inquirySubject">
        <el-input
          v-model="queryParams.inquirySubject"
          class="!w-240px"
          clearable
          placeholder="请输入询盘主题"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="处理状态" prop="processStatus">
        <el-select
          v-model="queryParams.processStatus"
          class="!w-160px"
          clearable
          placeholder="全部状态"
        >
          <el-option
            v-for="option in processStatusOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="数据范围" prop="testData">
        <el-select v-model="queryParams.testData" class="!w-150px" clearable placeholder="全部数据">
          <el-option label="经营数据" :value="false" />
          <el-option label="测试数据" :value="true" />
        </el-select>
      </el-form-item>
      <el-form-item label="优先级" prop="priority">
        <el-select
          v-model="queryParams.priority"
          class="!w-150px"
          clearable
          placeholder="全部优先级"
        >
          <el-option
            v-for="option in inquiryPriorityOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="销售阶段" prop="salesStage">
        <el-select
          v-model="queryParams.salesStage"
          class="!w-160px"
          clearable
          placeholder="全部阶段"
        >
          <el-option
            v-for="option in inquirySalesStageOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="提交时间" prop="submittedAt">
        <el-date-picker
          v-model="queryParams.submittedAt"
          class="!w-340px"
          end-placeholder="结束日期"
          range-separator="-"
          start-placeholder="开始日期"
          type="datetimerange"
          value-format="YYYY-MM-DD HH:mm:ss"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"> <Icon icon="ep:search" class="mr-5px" />搜索 </el-button>
        <el-button @click="resetQuery"> <Icon icon="ep:refresh" class="mr-5px" />重置 </el-button>
        <el-button
          v-hasPermi="['crm:clue:export']"
          :loading="exportLoading"
          plain
          type="success"
          @click="handleExport"
        >
          <Icon icon="ep:download" class="mr-5px" />导出
        </el-button>
        <el-button v-hasPermi="['crm:clue:update']" plain type="primary" @click="openMailSettings">
          <Icon icon="ep:message" class="mr-5px" />邮件通知设置
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" :stripe="true" :show-overflow-tooltip="true">
      <el-table-column type="expand" width="48">
        <template #default="{ row }">
          <el-descriptions class="inquiry-row-details" :column="4" border>
            <el-descriptions-item label="邮箱">
              <el-link v-if="row.email" :href="`mailto:${row.email}`" type="primary">
                {{ row.email }}
              </el-link>
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="电话 / WhatsApp">{{
              displayPhone(row)
            }}</el-descriptions-item>
            <el-descriptions-item label="提交页面">{{
              row.sourcePage || '-'
            }}</el-descriptions-item>
            <el-descriptions-item label="客户档案">{{
              row.customerName || '尚未生成'
            }}</el-descriptions-item>
          </el-descriptions>
        </template>
      </el-table-column>
      <el-table-column label="优先级" align="center" fixed="left" width="96">
        <template #default="{ row }">
          <el-tag :type="priorityMeta(row.priority).type" effect="plain">
            {{ priorityMeta(row.priority).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" fixed="left" width="92">
        <template #default="{ row }">
          <el-tag :type="getProcessStatusMeta(row.processStatus).type">
            {{ getProcessStatusMeta(row.processStatus).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="询盘主题" prop="inquirySubject" fixed="left" min-width="220">
        <template #default="{ row }">
          <el-link :underline="false" type="primary" @click="openDetail(row.id)">
            {{ row.inquirySubject || row.name }}
          </el-link>
          <el-tag v-if="row.testData" class="ml-6px" size="small" type="info" effect="plain">
            测试
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="公司名称" prop="companyName" min-width="170">
        <template #default="{ row }">{{ row.companyName || '待补充' }}</template>
      </el-table-column>
      <el-table-column label="联系人" prop="contactName" width="130" />
      <el-table-column label="销售阶段" align="center" width="116">
        <template #default="{ row }">{{ salesStageLabel(row.salesStage) }}</template>
      </el-table-column>
      <el-table-column label="响应 SLA" min-width="150">
        <template #default="{ row }">
          <el-tooltip :content="inquirySlaMeta(row).hint" placement="top">
            <el-tag :type="inquirySlaMeta(row).type" effect="plain">
              {{ inquirySlaMeta(row).label }}
            </el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="下次跟进" min-width="160">
        <template #default="{ row }">
          {{ row.contactNextTime ? formatDateTime(row.contactNextTime) : '待安排' }}
        </template>
      </el-table-column>
      <el-table-column label="提交时间" prop="submittedAt" :formatter="dateFormatter" width="180" />
      <el-table-column label="处理人" prop="ownerUserName" width="110" />
      <el-table-column label="操作" align="center" fixed="right" width="190">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">查看</el-button>
          <el-button
            v-if="row.processStatus === InquiryProcessStatus.PENDING"
            v-hasPermi="['crm:clue:update']"
            link
            type="primary"
            @click="updateStatus(row, InquiryProcessStatus.PROCESSING)"
          >
            开始处理
          </el-button>
          <el-dropdown v-hasPermi="['crm:clue:update']" class="ml-12px">
            <el-button link type="primary">更多<Icon icon="ep:arrow-down" /></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-if="!row.transformStatus && row.processStatus !== InquiryProcessStatus.INVALID"
                  :disabled="!row.companyName"
                  @click="transformInquiry(row)"
                >
                  生成客户档案
                </el-dropdown-item>
                <el-dropdown-item
                  v-if="row.processStatus !== InquiryProcessStatus.PROCESSED"
                  @click="updateStatus(row, InquiryProcessStatus.PROCESSED)"
                >
                  标记已处理
                </el-dropdown-item>
                <el-dropdown-item
                  v-if="row.processStatus !== InquiryProcessStatus.INVALID"
                  @click="updateStatus(row, InquiryProcessStatus.INVALID)"
                >
                  标记无效
                </el-dropdown-item>
                <el-dropdown-item divided @click="toggleTestData(row)">
                  {{ row.testData ? '恢复为经营数据' : '标记为测试数据' }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>

  <WebsiteInquiryMailSettings ref="mailSettingsRef" @success="handleMailSettingsSaved" />
</template>

<script setup lang="ts">
import dayjs from 'dayjs'
import { dateFormatter } from '@/utils/formatTime'
import download from '@/utils/download'
import * as ClueApi from '@/api/crm/clue'
import { InquiryProcessStatus } from '@/api/crm/clue'
import WebsiteInquiryMailSettings from './WebsiteInquiryMailSettings.vue'
import InquiryMailHealthAlert from './InquiryMailHealthAlert.vue'
import {
  inquiryPriorityOptions,
  inquirySalesStageOptions,
  inquirySlaMeta,
  priorityMeta,
  salesStageLabel
} from './inquiryOperations'

defineOptions({ name: 'CrmClue' })

const message = useMessage()
const loading = ref(true)
const exportLoading = ref(false)
const total = ref(0)
const list = ref<ClueApi.ClueVO[]>([])
const summary = ref<ClueApi.InquirySummaryVO>({
  totalCount: 0,
  pendingCount: 0,
  processingCount: 0,
  processedCount: 0,
  invalidCount: 0,
  overdueCount: 0,
  testDataCount: 0
})
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  companyName: undefined as string | undefined,
  contactName: undefined as string | undefined,
  email: undefined as string | undefined,
  inquirySubject: undefined as string | undefined,
  processStatus: undefined as InquiryProcessStatus | undefined,
  testData: false as boolean | undefined,
  priority: undefined as ClueApi.InquiryPriority | undefined,
  salesStage: undefined as ClueApi.InquirySalesStage | undefined,
  submittedAt: undefined as string[] | undefined
})
const queryFormRef = ref()
const mailSettingsRef = ref<InstanceType<typeof WebsiteInquiryMailSettings>>()
const mailHealthRef = ref<InstanceType<typeof InquiryMailHealthAlert>>()

const processStatusOptions = [
  { value: InquiryProcessStatus.PENDING, label: '待处理', type: 'warning' },
  { value: InquiryProcessStatus.PROCESSING, label: '处理中', type: 'primary' },
  { value: InquiryProcessStatus.PROCESSED, label: '已处理', type: 'success' },
  { value: InquiryProcessStatus.INVALID, label: '无效询盘', type: 'info' }
] as const

const summaryCards = computed(() => [
  {
    key: 'total',
    label: '全部询盘',
    value: summary.value.totalCount,
    status: undefined,
    icon: 'ep:files',
    hint: '当前可见询盘'
  },
  {
    key: 'pending',
    label: '待处理',
    value: summary.value.pendingCount,
    status: InquiryProcessStatus.PENDING,
    icon: 'ep:clock',
    hint: '等待首次响应'
  },
  {
    key: 'processing',
    label: '处理中',
    value: summary.value.processingCount,
    status: InquiryProcessStatus.PROCESSING,
    icon: 'ep:connection',
    hint: '正在持续跟进'
  },
  {
    key: 'processed',
    label: '已处理',
    value: summary.value.processedCount,
    status: InquiryProcessStatus.PROCESSED,
    icon: 'ep:circle-check',
    hint: '已完成处理'
  },
  {
    key: 'invalid',
    label: '无效询盘',
    value: summary.value.invalidCount,
    status: InquiryProcessStatus.INVALID,
    icon: 'ep:remove',
    hint: '已排除或关闭'
  }
])

const getProcessStatusMeta = (status: InquiryProcessStatus) =>
  processStatusOptions.find((item) => item.value === status) || processStatusOptions[0]

const getList = async () => {
  loading.value = true
  try {
    const [page, aggregate] = await Promise.all([
      ClueApi.getCluePage(queryParams),
      ClueApi.getInquirySummary(queryParams.testData)
    ])
    list.value = page.list
    total.value = page.total
    summary.value = aggregate
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  queryParams.processStatus = undefined
  queryParams.testData = false
  handleQuery()
}

const filterByStatus = (status?: InquiryProcessStatus) => {
  queryParams.processStatus = status
  handleQuery()
}

const displayPhone = (row: ClueApi.ClueVO) =>
  [row.countryCode, row.telephone].filter(Boolean).join(' ') || '-'
const formatDateTime = (value?: Date | string) =>
  value && dayjs(value).isValid() ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'

const { push } = useRouter()
const openDetail = (id: number) => {
  push({ name: 'CrmClueDetail', params: { id } })
}

const updateStatus = async (row: ClueApi.ClueVO, status: InquiryProcessStatus) => {
  if (status === InquiryProcessStatus.INVALID) {
    await message.confirm(`确定将“${row.inquirySubject || row.name}”标记为无效询盘吗？`)
  }
  await ClueApi.updateInquiryProcessStatus({ id: row.id, processStatus: status })
  message.success(`询盘状态已更新为“${getProcessStatusMeta(status).label}”`)
  await getList()
}

const transformInquiry = async (row: ClueApi.ClueVO) => {
  await message.confirm(
    `确定根据该询盘生成客户档案“${row.companyName}”及联系人“${row.contactName}”吗？`
  )
  const result = await ClueApi.transformClue(row.id)
  const customerAction = result.customerCreated ? '已创建客户档案' : '已关联现有客户档案'
  const contactAction = result.contactCreated ? '已创建联系人' : '已关联现有联系人'
  message.success(`${customerAction}，${contactAction}`)
  await getList()
}

const toggleTestData = async (row: ClueApi.ClueVO) => {
  const nextValue = !row.testData
  await message.confirm(
    nextValue
      ? `确定把“${row.inquirySubject || row.name}”标记为测试数据吗？它将从经营首页和报表中排除。`
      : `确定把“${row.inquirySubject || row.name}”恢复为经营数据吗？它将重新进入经营统计。`
  )
  await ClueApi.updateInquiryTestData(row.id, nextValue)
  message.success(nextValue ? '已隔离为测试数据' : '已恢复为经营数据')
  await getList()
}

const handleExport = async () => {
  try {
    await message.exportConfirm()
    exportLoading.value = true
    const data = await ClueApi.exportClue(queryParams)
    download.excel(data, '询盘汇总.xls')
  } finally {
    exportLoading.value = false
  }
}

const openMailSettings = () => {
  mailSettingsRef.value?.open()
}

const handleMailSettingsSaved = () => {
  mailHealthRef.value?.refresh()
}

onMounted(getList)
</script>

<style scoped>
.inquiry-summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.summary-card {
  position: relative;
  display: grid;
  min-height: 112px;
  padding: 16px 18px;
  overflow: hidden;
  color: var(--furniture-admin-ink);
  text-align: left;
  cursor: pointer;
  background: #fff;
  border: 1px solid var(--furniture-admin-border);
  border-radius: 8px;
  transition:
    border-color 0.16s ease,
    box-shadow 0.16s ease,
    transform 0.16s ease;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
  gap: 5px 12px;
}

.summary-card:hover {
  border-color: var(--furniture-admin-border-strong);
  transform: translateY(-1px);
  box-shadow: 0 8px 22px rgb(15 36 58 / 7%);
}

.summary-card.is-active {
  background: #f5f9ff;
  border-color: #9fc3f3;
  box-shadow: 0 0 0 2px rgb(23 107 219 / 6%);
}

.summary-card__icon {
  display: grid;
  grid-row: 1;
  grid-column: 2;
  width: 32px;
  height: 32px;
  color: var(--furniture-admin-primary);
  background: #edf5ff;
  border-radius: 7px;
  place-items: center;
}

.summary-card__label {
  font-size: 12px;
  font-weight: 550;
  color: var(--furniture-admin-body);
  grid-row: 1;
  grid-column: 1;
}

.summary-card strong {
  grid-row: 2;
  grid-column: 1 / -1;
  margin-top: 4px;
  font-size: 27px;
  font-weight: 650;
  line-height: 1;
}

.summary-card small {
  font-size: 11px;
  color: var(--furniture-admin-muted);
  grid-row: 3;
  grid-column: 1 / -1;
}

.inquiry-row-details {
  padding: 14px 24px;
}

@media (width <= 1100px) {
  .inquiry-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (width <= 640px) {
  .inquiry-summary {
    grid-template-columns: 1fr;
  }
}
</style>
