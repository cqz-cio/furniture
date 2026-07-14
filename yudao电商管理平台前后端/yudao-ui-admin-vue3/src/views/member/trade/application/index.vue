<template>
  <ContentWrap>
    <el-form
      ref="queryFormRef"
      :inline="true"
      :model="queryParams"
      class="-mb-15px"
      label-width="110px"
    >
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" clearable class="!w-180px" placeholder="全部">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="企业名称" prop="businessName">
        <el-input
          v-model="queryParams.businessName"
          class="!w-240px"
          clearable
          placeholder="请输入企业名称"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="主要邮箱" prop="primaryEmail">
        <el-input
          v-model="queryParams.primaryEmail"
          class="!w-240px"
          clearable
          placeholder="请输入主要邮箱"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button v-hasPermi="['member:trade-application:query']" @click="handleQuery">
          <Icon class="mr-5px" icon="ep:search" />
          搜索
        </el-button>
        <el-button @click="resetQuery">
          <Icon class="mr-5px" icon="ep:refresh" />
          重置
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" :show-overflow-tooltip="true" :stripe="true">
      <el-table-column align="center" label="ID" prop="id" width="90" />
      <el-table-column label="企业名称" min-width="180" prop="businessName" />
      <el-table-column label="主要邮箱" min-width="220" prop="primaryEmail" />
      <el-table-column label="所在地" min-width="180">
        <template #default="{ row }">
          {{ [row.city, row.state, row.country].filter(Boolean).join(', ') || '-' }}
        </template>
      </el-table-column>
      <el-table-column align="center" label="状态" prop="status" width="120">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="贸易编号" prop="tradeId" width="160" />
      <el-table-column align="center" label="创建时间" prop="createTime" width="180" />
      <el-table-column align="center" fixed="right" label="操作" width="220">
        <template #default="{ row }">
          <el-button v-hasPermi="['member:trade-application:query']" link type="primary" @click="openDetail(row.id)">详情</el-button>
          <el-button
            v-if="row.status === TRADE_APPLICATION_STATUS.pending"
            v-hasPermi="['member:trade-application:review']"
            link
            type="success"
            @click="openReview(row, 'approve')"
          >
            通过
          </el-button>
          <el-button
            v-if="row.status === TRADE_APPLICATION_STATUS.pending"
            v-hasPermi="['member:trade-application:review']"
            link
            type="danger"
            @click="openReview(row, 'reject')"
          >
            驳回
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

  <el-drawer v-model="detailVisible" size="640px" title="交易申请详情">
    <el-descriptions v-if="detail" :column="1" border>
      <el-descriptions-item label="企业名称">{{ detail.businessName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="主要邮箱">{{ detail.primaryEmail || '-' }}</el-descriptions-item>
      <el-descriptions-item label="地址">
        {{ [detail.street, detail.address2, detail.city, detail.state, detail.postalCode, detail.country].filter(Boolean).join(', ') || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="企业说明">{{ detail.businessDescription || '-' }}</el-descriptions-item>
      <el-descriptions-item label="官网">{{ detail.website || '-' }}</el-descriptions-item>
      <el-descriptions-item label="作品集">{{ detail.portfolio || '-' }}</el-descriptions-item>
      <el-descriptions-item label="社交账号">
        {{ [detail.instagram, detail.pinterest, detail.houzz, detail.linkedin].filter(Boolean).join(' / ') || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
      <el-descriptions-item label="贸易编号">{{ detail.tradeId || '-' }}</el-descriptions-item>
      <el-descriptions-item label="审核备注">{{ detail.reviewReason || '-' }}</el-descriptions-item>
    </el-descriptions>

    <el-divider content-position="left">授权用户</el-divider>
    <el-table :data="detail?.authorizedUsers || []" size="small">
      <el-table-column label="姓名">
        <template #default="{ row }">
          {{ [row.firstName, row.lastName].filter(Boolean).join(' ') || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="职位" prop="title" />
      <el-table-column label="邮箱" prop="email" />
      <el-table-column label="电话" prop="phone" />
    </el-table>

    <el-divider content-position="left">申请资料</el-divider>
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
      <el-text v-if="!hasDocuments" type="info">暂无资料</el-text>
    </el-space>
  </el-drawer>

  <el-dialog v-model="reviewVisible" :title="reviewMode === 'approve' ? '通过申请' : '驳回申请'" width="460px">
    <el-form ref="reviewFormRef" :model="reviewForm" :rules="reviewRules" label-width="110px">
      <el-form-item v-if="reviewMode === 'approve'" label="贸易编号" prop="tradeId">
        <el-input v-model="reviewForm.tradeId" maxlength="64" placeholder="RH-TRADE-10086" />
      </el-form-item>
      <el-form-item label="备注" prop="reviewReason">
        <el-input
          v-model="reviewForm.reviewReason"
          :rows="3"
          maxlength="512"
          placeholder="请输入审核备注"
          show-word-limit
          type="textarea"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="reviewVisible = false">取消</el-button>
      <el-button :loading="reviewLoading" type="primary" @click="submitReview">确认</el-button>
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
  { label: '待审核', value: TRADE_APPLICATION_STATUS.pending },
  { label: '已通过', value: TRADE_APPLICATION_STATUS.approved },
  { label: '已驳回', value: TRADE_APPLICATION_STATUS.rejected }
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
      ? [{ required: true, message: '请输入贸易编号', trigger: 'blur' }]
      : [],
  reviewReason:
    reviewMode.value === 'reject'
      ? [{ required: true, message: '请输入驳回原因', trigger: 'blur' }]
      : []
}))

const statusLabel = (status: number) => statusOptions.find((item) => item.value === status)?.label || '未知'
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
    message.success('审核已提交')
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
