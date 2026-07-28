<template>
  <div class="inquiry-summary">
    <button
      v-for="card in summaryCards"
      :key="card.key"
      class="summary-card"
      :class="{ 'is-active': queryParams.processStatus === card.status }"
      type="button"
      @click="filterByStatus(card.status)"
    >
      <span class="summary-card__label">{{ card.label }}</span>
      <strong>{{ card.value }}</strong>
    </button>
  </div>

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
        <el-button @click="handleQuery">
          <Icon icon="ep:search" class="mr-5px" />搜索
        </el-button>
        <el-button @click="resetQuery">
          <Icon icon="ep:refresh" class="mr-5px" />重置
        </el-button>
        <el-button
          v-hasPermi="['crm:clue:export']"
          :loading="exportLoading"
          plain
          type="success"
          @click="handleExport"
        >
          <Icon icon="ep:download" class="mr-5px" />导出
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" :stripe="true" :show-overflow-tooltip="true">
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
        </template>
      </el-table-column>
      <el-table-column label="公司名称" prop="companyName" min-width="170">
        <template #default="{ row }">{{ row.companyName || '待补充' }}</template>
      </el-table-column>
      <el-table-column label="联系人" prop="contactName" width="130" />
      <el-table-column label="电话 / WhatsApp" min-width="165">
        <template #default="{ row }">{{ displayPhone(row) }}</template>
      </el-table-column>
      <el-table-column label="邮箱" prop="email" min-width="200" />
      <el-table-column label="提交页面" prop="sourcePage" min-width="190" />
      <el-table-column
        label="提交时间"
        prop="submittedAt"
        :formatter="dateFormatter"
        width="180"
      />
      <el-table-column label="处理人" prop="ownerUserName" width="110" />
      <el-table-column label="客户档案" prop="customerName" min-width="150">
        <template #default="{ row }">{{ row.customerName || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" align="center" fixed="right" width="260">
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
          <el-button
            v-if="!row.transformStatus && row.processStatus !== InquiryProcessStatus.INVALID"
            v-hasPermi="['crm:clue:update']"
            :disabled="!row.companyName"
            link
            type="success"
            @click="transformInquiry(row)"
          >
            生成客户档案
          </el-button>
          <el-dropdown
            v-if="
              row.processStatus !== InquiryProcessStatus.PROCESSED &&
              row.processStatus !== InquiryProcessStatus.INVALID
            "
            v-hasPermi="['crm:clue:update']"
            class="ml-12px"
            @command="(command) => updateStatus(row, command)"
          >
            <el-button link type="primary">更多<Icon icon="ep:arrow-down" /></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item :command="InquiryProcessStatus.PROCESSED">
                  标记已处理
                </el-dropdown-item>
                <el-dropdown-item :command="InquiryProcessStatus.INVALID" divided>
                  标记无效
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
</template>

<script setup lang="ts">
import { dateFormatter } from '@/utils/formatTime'
import download from '@/utils/download'
import * as ClueApi from '@/api/crm/clue'
import { InquiryProcessStatus } from '@/api/crm/clue'

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
  invalidCount: 0
})
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  companyName: undefined as string | undefined,
  contactName: undefined as string | undefined,
  email: undefined as string | undefined,
  inquirySubject: undefined as string | undefined,
  processStatus: undefined as InquiryProcessStatus | undefined,
  submittedAt: undefined as string[] | undefined
})
const queryFormRef = ref()

const processStatusOptions = [
  { value: InquiryProcessStatus.PENDING, label: '待处理', type: 'warning' },
  { value: InquiryProcessStatus.PROCESSING, label: '处理中', type: 'primary' },
  { value: InquiryProcessStatus.PROCESSED, label: '已处理', type: 'success' },
  { value: InquiryProcessStatus.INVALID, label: '无效询盘', type: 'info' }
] as const

const summaryCards = computed(() => [
  { key: 'total', label: '全部询盘', value: summary.value.totalCount, status: undefined },
  {
    key: 'pending',
    label: '待处理',
    value: summary.value.pendingCount,
    status: InquiryProcessStatus.PENDING
  },
  {
    key: 'processing',
    label: '处理中',
    value: summary.value.processingCount,
    status: InquiryProcessStatus.PROCESSING
  },
  {
    key: 'processed',
    label: '已处理',
    value: summary.value.processedCount,
    status: InquiryProcessStatus.PROCESSED
  },
  {
    key: 'invalid',
    label: '无效询盘',
    value: summary.value.invalidCount,
    status: InquiryProcessStatus.INVALID
  }
])

const getProcessStatusMeta = (status: InquiryProcessStatus) =>
  processStatusOptions.find((item) => item.value === status) || processStatusOptions[0]

const getList = async () => {
  loading.value = true
  try {
    const [page, aggregate] = await Promise.all([
      ClueApi.getCluePage(queryParams),
      ClueApi.getInquirySummary()
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
  handleQuery()
}

const filterByStatus = (status?: InquiryProcessStatus) => {
  queryParams.processStatus = status
  handleQuery()
}

const displayPhone = (row: ClueApi.ClueVO) =>
  [row.countryCode, row.telephone].filter(Boolean).join(' ') || '-'

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
  display: flex;
  min-height: 88px;
  cursor: pointer;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
  padding: 16px 20px;
  color: var(--el-text-color-primary);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.summary-card:hover,
.summary-card.is-active {
  border-color: var(--el-color-primary);
  box-shadow: 0 4px 16px rgb(0 0 0 / 6%);
}

.summary-card__label {
  margin-bottom: 8px;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.summary-card strong {
  font-size: 26px;
  line-height: 1;
}

@media (max-width: 1100px) {
  .inquiry-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
