<template>
  <ContentWrap>
    <el-form
      ref="queryFormRef"
      :inline="true"
      :model="queryParams"
      class="-mb-15px"
      label-width="110px"
    >
      <el-form-item label="Status" prop="status">
        <el-select v-model="queryParams.status" clearable class="!w-180px" placeholder="All">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="Business" prop="businessName">
        <el-input
          v-model="queryParams.businessName"
          class="!w-240px"
          clearable
          placeholder="Business name"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="Primary email" prop="primaryEmail">
        <el-input
          v-model="queryParams.primaryEmail"
          class="!w-240px"
          clearable
          placeholder="Primary email"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery">
          <Icon class="mr-5px" icon="ep:search" />
          Search
        </el-button>
        <el-button @click="resetQuery">
          <Icon class="mr-5px" icon="ep:refresh" />
          Reset
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" :show-overflow-tooltip="true" :stripe="true">
      <el-table-column align="center" label="ID" prop="id" width="90" />
      <el-table-column label="Business" min-width="180" prop="businessName" />
      <el-table-column label="Primary email" min-width="220" prop="primaryEmail" />
      <el-table-column label="Location" min-width="180">
        <template #default="{ row }">
          {{ [row.city, row.state, row.country].filter(Boolean).join(', ') || '-' }}
        </template>
      </el-table-column>
      <el-table-column align="center" label="Status" prop="status" width="120">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Trade ID" prop="tradeId" width="160" />
      <el-table-column align="center" label="Created" prop="createTime" width="180" />
      <el-table-column align="center" fixed="right" label="Actions" width="220">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">Detail</el-button>
          <el-button
            v-if="row.status === TRADE_APPLICATION_STATUS.pending"
            v-hasPermi="['member:trade-application:review']"
            link
            type="success"
            @click="openReview(row, 'approve')"
          >
            Approve
          </el-button>
          <el-button
            v-if="row.status === TRADE_APPLICATION_STATUS.pending"
            v-hasPermi="['member:trade-application:review']"
            link
            type="danger"
            @click="openReview(row, 'reject')"
          >
            Reject
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:limit="queryParams.pageSize"
      v-model:page="queryParams.pageNo"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>

  <el-drawer v-model="detailVisible" size="640px" title="Trade application detail">
    <el-descriptions v-if="detail" :column="1" border>
      <el-descriptions-item label="Business">{{ detail.businessName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="Primary email">{{ detail.primaryEmail || '-' }}</el-descriptions-item>
      <el-descriptions-item label="Address">
        {{ [detail.street, detail.address2, detail.city, detail.state, detail.postalCode, detail.country].filter(Boolean).join(', ') || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="Description">{{ detail.businessDescription || '-' }}</el-descriptions-item>
      <el-descriptions-item label="Website">{{ detail.website || '-' }}</el-descriptions-item>
      <el-descriptions-item label="Portfolio">{{ detail.portfolio || '-' }}</el-descriptions-item>
      <el-descriptions-item label="Social">
        {{ [detail.instagram, detail.pinterest, detail.houzz, detail.linkedin].filter(Boolean).join(' / ') || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="Status">{{ statusLabel(detail.status) }}</el-descriptions-item>
      <el-descriptions-item label="Trade ID">{{ detail.tradeId || '-' }}</el-descriptions-item>
      <el-descriptions-item label="Review reason">{{ detail.reviewReason || '-' }}</el-descriptions-item>
    </el-descriptions>

    <el-divider content-position="left">Authorized users</el-divider>
    <el-table :data="detail?.authorizedUsers || []" size="small">
      <el-table-column label="Name">
        <template #default="{ row }">
          {{ [row.firstName, row.lastName].filter(Boolean).join(' ') || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="Title" prop="title" />
      <el-table-column label="Email" prop="email" />
      <el-table-column label="Phone" prop="phone" />
    </el-table>

    <el-divider content-position="left">Documents</el-divider>
    <el-space direction="vertical" alignment="flex-start">
      <el-link
        v-for="file in [...(detail?.businessDocuments || []), ...(detail?.taxDocuments || [])]"
        :key="`${file.name}-${file.url}`"
        :href="file.url"
        target="_blank"
        type="primary"
      >
        {{ file.name || file.url }}
      </el-link>
      <el-text v-if="!hasDocuments" type="info">No documents</el-text>
    </el-space>
  </el-drawer>

  <el-dialog v-model="reviewVisible" :title="reviewMode === 'approve' ? 'Approve application' : 'Reject application'" width="460px">
    <el-form ref="reviewFormRef" :model="reviewForm" :rules="reviewRules" label-width="110px">
      <el-form-item v-if="reviewMode === 'approve'" label="Trade ID" prop="tradeId">
        <el-input v-model="reviewForm.tradeId" maxlength="64" placeholder="RH-TRADE-10086" />
      </el-form-item>
      <el-form-item label="Reason" prop="reviewReason">
        <el-input
          v-model="reviewForm.reviewReason"
          :rows="3"
          maxlength="512"
          placeholder="Review note"
          show-word-limit
          type="textarea"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="reviewVisible = false">Cancel</el-button>
      <el-button :loading="reviewLoading" type="primary" @click="submitReview">Confirm</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus'
import * as TradeApplicationApi from '@/api/member/trade/application'

defineOptions({ name: 'MemberTradeApplication' })

const message = useMessage()

const TRADE_APPLICATION_STATUS = {
  pending: 0,
  approved: 1,
  rejected: 2
}

const statusOptions = [
  { label: 'Pending', value: TRADE_APPLICATION_STATUS.pending },
  { label: 'Approved', value: TRADE_APPLICATION_STATUS.approved },
  { label: 'Rejected', value: TRADE_APPLICATION_STATUS.rejected }
]

const loading = ref(false)
const list = ref<TradeApplicationApi.TradeApplicationVO[]>([])
const total = ref(0)
const queryFormRef = ref<FormInstance>()
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  status: undefined as number | undefined,
  primaryEmail: '',
  businessName: ''
})

const detailVisible = ref(false)
const detail = ref<TradeApplicationApi.TradeApplicationVO>()
const hasDocuments = computed(
  () => Boolean(detail.value?.businessDocuments?.length) || Boolean(detail.value?.taxDocuments?.length)
)

const reviewVisible = ref(false)
const reviewLoading = ref(false)
const reviewMode = ref<'approve' | 'reject'>('approve')
const reviewFormRef = ref<FormInstance>()
const reviewForm = reactive<TradeApplicationApi.TradeApplicationReviewReqVO>({
  id: 0,
  tradeId: '',
  reviewReason: ''
})
const reviewRules = computed<FormRules>(() => ({
  tradeId:
    reviewMode.value === 'approve'
      ? [{ required: true, message: 'Trade ID is required', trigger: 'blur' }]
      : [],
  reviewReason:
    reviewMode.value === 'reject'
      ? [{ required: true, message: 'Reject reason is required', trigger: 'blur' }]
      : []
}))

const statusLabel = (status: number) => statusOptions.find((item) => item.value === status)?.label || 'Unknown'
const statusTagType = (status: number) => {
  if (status === TRADE_APPLICATION_STATUS.approved) return 'success'
  if (status === TRADE_APPLICATION_STATUS.rejected) return 'danger'
  return 'warning'
}

const getList = async () => {
  loading.value = true
  try {
    const data = await TradeApplicationApi.getTradeApplicationPage(queryParams)
    list.value = data.list || []
    total.value = data.total || 0
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
  handleQuery()
}

const openDetail = async (id: number) => {
  detail.value = await TradeApplicationApi.getTradeApplication(id)
  detailVisible.value = true
}

const openReview = (row: TradeApplicationApi.TradeApplicationVO, mode: 'approve' | 'reject') => {
  reviewMode.value = mode
  reviewForm.id = row.id
  reviewForm.tradeId = row.tradeId || ''
  reviewForm.reviewReason = ''
  reviewVisible.value = true
}

const submitReview = async () => {
  const valid = await reviewFormRef.value?.validate()
  if (!valid) return
  reviewLoading.value = true
  try {
    if (reviewMode.value === 'approve') {
      await TradeApplicationApi.approveTradeApplication(reviewForm)
    } else {
      await TradeApplicationApi.rejectTradeApplication(reviewForm)
    }
    message.success('Review submitted')
    reviewVisible.value = false
    await getList()
  } finally {
    reviewLoading.value = false
  }
}

onMounted(() => {
  getList()
})
</script>
