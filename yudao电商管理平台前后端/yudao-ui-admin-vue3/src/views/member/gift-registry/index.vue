<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :inline="true" :model="queryParams" class="-mb-15px" label-width="120px">
      <el-form-item label="用户 ID" prop="userId">
        <el-input-number v-model="queryParams.userId" class="!w-180px" :min="1" controls-position="right" />
      </el-form-item>
      <el-form-item label="登记人" prop="registrantName">
        <el-input v-model="queryParams.registrantName" class="!w-220px" clearable placeholder="请输入登记人姓名" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="活动类型" prop="eventType">
        <el-input v-model="queryParams.eventType" class="!w-180px" clearable placeholder="请输入活动类型" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" clearable class="!w-180px" placeholder="全部">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="公开编码" prop="publicCode">
        <el-input v-model="queryParams.publicCode" class="!w-240px" clearable placeholder="请输入公开编码" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button v-hasPermi="['member:gift-registry:query']" @click="handleQuery">
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
      <el-table-column align="center" label="用户 ID" prop="userId" width="110" />
      <el-table-column label="登记人" min-width="220">
        <template #default="{ row }">
          {{ row.registrantName }}{{ row.coRegistrantName ? ` & ${row.coRegistrantName}` : '' }}
        </template>
      </el-table-column>
      <el-table-column label="公开编码" min-width="220" prop="publicCode" />
      <el-table-column label="活动信息" min-width="180">
        <template #default="{ row }">{{ row.eventType }} / {{ row.eventDate }}</template>
      </el-table-column>
      <el-table-column align="center" label="可见性" prop="visibility" width="150" />
      <el-table-column align="center" label="状态" prop="status" width="130">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column align="center" label="创建时间" prop="createTime" width="180" />
      <el-table-column align="center" fixed="right" label="操作" width="170">
        <template #default="{ row }">
          <el-button v-hasPermi="['member:gift-registry:query']" link type="primary" @click="openDetail(row.id)">详情</el-button>
          <el-button v-hasPermi="['member:gift-registry:update']" link type="success" @click="openStatus(row)">状态</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination v-model:limit="queryParams.pageSize" v-model:page="queryParams.pageNo" :total="total" @pagination="getList" />
  </ContentWrap>

  <el-drawer v-model="detailVisible" size="720px" title="礼品登记详情">
    <el-descriptions v-if="detail" :column="1" border>
      <el-descriptions-item label="用户 ID">{{ detail.userId }}</el-descriptions-item>
      <el-descriptions-item label="公开编码">{{ detail.publicCode }}</el-descriptions-item>
      <el-descriptions-item label="登记人">{{ detail.registrantName }} {{ detail.coRegistrantName || '' }}</el-descriptions-item>
      <el-descriptions-item label="邮箱">{{ detail.email }}</el-descriptions-item>
      <el-descriptions-item label="活动信息">{{ detail.eventType }} / {{ detail.eventDate }} / {{ detail.eventLocation || '-' }}</el-descriptions-item>
      <el-descriptions-item label="可见性">{{ detail.visibility }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
      <el-descriptions-item label="礼品卡偏好">{{ detail.giftCardPreference ? '接受' : '隐藏' }}</el-descriptions-item>
      <el-descriptions-item label="留言偏好">{{ detail.messagePreference ? '开启' : '关闭' }}</el-descriptions-item>
    </el-descriptions>

    <el-table v-if="detail?.items?.length" class="mt-20px" :data="detail.items" :stripe="true">
      <el-table-column label="商品" min-width="220" prop="productName" />
      <el-table-column align="center" label="SPU" prop="spuId" width="120" />
      <el-table-column align="center" label="SKU" prop="skuId" width="120" />
      <el-table-column align="center" label="期望数量" prop="quantityRequested" width="110" />
      <el-table-column align="center" label="已购数量" prop="quantityPurchased" width="110" />
      <el-table-column label="优先级" prop="priority" width="120" />
    </el-table>
  </el-drawer>

  <el-dialog v-model="statusVisible" title="更新登记状态" width="420px">
    <el-form ref="statusFormRef" :model="statusForm" label-width="100px">
      <el-form-item label="状态" prop="status" required>
        <el-select v-model="statusForm.status" class="!w-220px">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="statusVisible = false">取消</el-button>
      <el-button :loading="statusLoading" type="primary" @click="submitStatus">确认</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import type { FormInstance } from 'element-plus'
import * as GiftRegistryApi from '@/api/member/giftRegistry'

defineOptions({ name: 'MemberGiftRegistry' })

const message = useMessage()

const statusOptions = [
  { label: '启用', value: 'active' },
  { label: '隐藏', value: 'hidden' },
  { label: '关闭', value: 'closed' }
]

const loading = ref(false)
const list = ref<GiftRegistryApi.GiftRegistryVO[]>([])
const total = ref(0)
const queryFormRef = ref<FormInstance>()
const queryParams = reactive<GiftRegistryApi.GiftRegistryPageReqVO>({
  pageNo: 1,
  pageSize: 10,
  userId: undefined,
  registrantName: '',
  eventType: '',
  status: '',
  publicCode: ''
})

const detailVisible = ref(false)
const detail = ref<GiftRegistryApi.GiftRegistryVO>()
const statusVisible = ref(false)
const statusLoading = ref(false)
const statusForm = reactive<GiftRegistryApi.GiftRegistryStatusUpdateReqVO>({
  id: 0,
  status: 'active'
})

const statusLabel = (status?: string) => statusOptions.find((item) => item.value === status)?.label || '未知'
const statusTagType = (status?: string) => {
  if (status === 'active') return 'success'
  if (status === 'hidden') return 'warning'
  if (status === 'closed') return 'info'
  return 'danger'
}

const getList = async () => {
  loading.value = true
  try {
    const data = await GiftRegistryApi.getGiftRegistryPage(queryParams)
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
  detail.value = await GiftRegistryApi.getGiftRegistry(id)
  detailVisible.value = true
}

const openStatus = (row: GiftRegistryApi.GiftRegistryVO) => {
  Object.assign(statusForm, { id: row.id, status: row.status || 'active' })
  statusVisible.value = true
}

const submitStatus = async () => {
  statusLoading.value = true
  try {
    await GiftRegistryApi.updateGiftRegistryStatus(statusForm)
    message.success('礼品登记状态已保存')
    statusVisible.value = false
    await getList()
  } finally {
    statusLoading.value = false
  }
}

onMounted(() => {
  getList()
})
</script>
