<template>
  <el-table v-loading="loading" :data="list" :show-overflow-tooltip="true" :stripe="true">
    <el-table-column label="处理状态" width="100" align="center">
      <template #default="{ row }">
        <el-tag :type="statusMeta(row.processStatus).type">
          {{ statusMeta(row.processStatus).label }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="询盘主题" prop="inquirySubject" min-width="220">
      <template #default="{ row }">
        <el-link :underline="false" type="primary" @click="openInquiry(row.id)">
          {{ row.inquirySubject || row.name }}
        </el-link>
      </template>
    </el-table-column>
    <el-table-column label="联系人" prop="contactName" width="140" />
    <el-table-column label="邮箱" prop="email" min-width="200" />
    <el-table-column
      label="提交时间"
      prop="submittedAt"
      :formatter="dateFormatter"
      width="180"
    />
  </el-table>
  <Pagination
    v-model:page="queryParams.pageNo"
    v-model:limit="queryParams.pageSize"
    :total="total"
    @pagination="getList"
  />
</template>

<script lang="ts" setup>
import * as ClueApi from '@/api/crm/clue'
import { InquiryProcessStatus } from '@/api/crm/clue'
import { dateFormatter } from '@/utils/formatTime'

const props = defineProps<{ customerId: number }>()
const loading = ref(false)
const list = ref<ClueApi.ClueVO[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  customerId: props.customerId
})

const metadata = {
  [InquiryProcessStatus.PENDING]: { label: '待处理', type: 'warning' },
  [InquiryProcessStatus.PROCESSING]: { label: '处理中', type: 'primary' },
  [InquiryProcessStatus.PROCESSED]: { label: '已处理', type: 'success' },
  [InquiryProcessStatus.INVALID]: { label: '无效询盘', type: 'info' }
} as const
const statusMeta = (status: InquiryProcessStatus) =>
  metadata[status] || metadata[InquiryProcessStatus.PENDING]

const getList = async () => {
  if (!props.customerId) return
  loading.value = true
  try {
    queryParams.customerId = props.customerId
    const data = await ClueApi.getCluePage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const { push } = useRouter()
const openInquiry = (id: number) => {
  push({ name: 'CrmClueDetail', params: { id } })
}

watch(() => props.customerId, getList, { immediate: true })
</script>
