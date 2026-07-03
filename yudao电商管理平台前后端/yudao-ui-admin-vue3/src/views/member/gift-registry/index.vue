<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :inline="true" :model="queryParams" class="-mb-15px" label-width="120px">
      <el-form-item label="User ID" prop="userId">
        <el-input-number v-model="queryParams.userId" class="!w-180px" :min="1" controls-position="right" />
      </el-form-item>
      <el-form-item label="Registrant" prop="registrantName">
        <el-input v-model="queryParams.registrantName" class="!w-220px" clearable placeholder="Avery Stone" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="Event Type" prop="eventType">
        <el-input v-model="queryParams.eventType" class="!w-180px" clearable placeholder="Wedding" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="Status" prop="status">
        <el-select v-model="queryParams.status" clearable class="!w-180px" placeholder="All">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="Public Code" prop="publicCode">
        <el-input v-model="queryParams.publicCode" class="!w-240px" clearable placeholder="registry-..." @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button v-hasPermi="['member:gift-registry:query']" @click="handleQuery">
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
      <el-table-column align="center" label="User ID" prop="userId" width="110" />
      <el-table-column label="Registrant" min-width="220">
        <template #default="{ row }">
          {{ row.registrantName }}{{ row.coRegistrantName ? ` & ${row.coRegistrantName}` : '' }}
        </template>
      </el-table-column>
      <el-table-column label="Public Code" min-width="220" prop="publicCode" />
      <el-table-column label="Event" min-width="180">
        <template #default="{ row }">{{ row.eventType }} / {{ row.eventDate }}</template>
      </el-table-column>
      <el-table-column align="center" label="Visibility" prop="visibility" width="150" />
      <el-table-column align="center" label="Status" prop="status" width="130">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column align="center" label="Created" prop="createTime" width="180" />
      <el-table-column align="center" fixed="right" label="Actions" width="170">
        <template #default="{ row }">
          <el-button v-hasPermi="['member:gift-registry:query']" link type="primary" @click="openDetail(row.id)">Detail</el-button>
          <el-button v-hasPermi="['member:gift-registry:update']" link type="success" @click="openStatus(row)">Status</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination v-model:limit="queryParams.pageSize" v-model:page="queryParams.pageNo" :total="total" @pagination="getList" />
  </ContentWrap>

  <el-drawer v-model="detailVisible" size="720px" title="Gift registry detail">
    <el-descriptions v-if="detail" :column="1" border>
      <el-descriptions-item label="User ID">{{ detail.userId }}</el-descriptions-item>
      <el-descriptions-item label="Public Code">{{ detail.publicCode }}</el-descriptions-item>
      <el-descriptions-item label="Registrant">{{ detail.registrantName }} {{ detail.coRegistrantName || '' }}</el-descriptions-item>
      <el-descriptions-item label="Email">{{ detail.email }}</el-descriptions-item>
      <el-descriptions-item label="Event">{{ detail.eventType }} / {{ detail.eventDate }} / {{ detail.eventLocation || '-' }}</el-descriptions-item>
      <el-descriptions-item label="Visibility">{{ detail.visibility }}</el-descriptions-item>
      <el-descriptions-item label="Status">{{ statusLabel(detail.status) }}</el-descriptions-item>
      <el-descriptions-item label="Gift Card Preference">{{ detail.giftCardPreference ? 'Accepted' : 'Hidden' }}</el-descriptions-item>
      <el-descriptions-item label="Message Preference">{{ detail.messagePreference ? 'Enabled' : 'Disabled' }}</el-descriptions-item>
    </el-descriptions>

    <el-table v-if="detail?.items?.length" class="mt-20px" :data="detail.items" :stripe="true">
      <el-table-column label="Product" min-width="220" prop="productName" />
      <el-table-column align="center" label="SPU" prop="spuId" width="120" />
      <el-table-column align="center" label="SKU" prop="skuId" width="120" />
      <el-table-column align="center" label="Requested" prop="quantityRequested" width="110" />
      <el-table-column align="center" label="Purchased" prop="quantityPurchased" width="110" />
      <el-table-column label="Priority" prop="priority" width="120" />
    </el-table>
  </el-drawer>

  <el-dialog v-model="statusVisible" title="Update registry status" width="420px">
    <el-form ref="statusFormRef" :model="statusForm" label-width="100px">
      <el-form-item label="Status" prop="status" required>
        <el-select v-model="statusForm.status" class="!w-220px">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="statusVisible = false">Cancel</el-button>
      <el-button :loading="statusLoading" type="primary" @click="submitStatus">Confirm</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import type { FormInstance } from 'element-plus'
import * as GiftRegistryApi from '@/api/member/giftRegistry'

defineOptions({ name: 'MemberGiftRegistry' })

const message = useMessage()

const statusOptions = [
  { label: 'Active', value: 'active' },
  { label: 'Hidden', value: 'hidden' },
  { label: 'Closed', value: 'closed' }
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

const statusLabel = (status?: string) => statusOptions.find((item) => item.value === status)?.label || 'Unknown'
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
    message.success('Gift registry status saved')
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
