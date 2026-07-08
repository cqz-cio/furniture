<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :inline="true" :model="queryParams" class="-mb-15px" label-width="100px">
      <el-form-item label="用户 ID" prop="userId">
        <el-input-number v-model="queryParams.userId" class="!w-180px" :min="1" controls-position="right" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" clearable class="!w-180px" placeholder="全部">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="会员编号" prop="memberId">
        <el-input v-model="queryParams.memberId" class="!w-220px" clearable placeholder="请输入会员编号" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button v-hasPermi="['member:membership:query']" @click="handleQuery">
          <Icon class="mr-5px" icon="ep:search" />
          搜索
        </el-button>
        <el-button @click="resetQuery">
          <Icon class="mr-5px" icon="ep:refresh" />
          重置
        </el-button>
        <el-button v-hasPermi="['member:membership:update']" type="primary" @click="openCreate">
          <Icon class="mr-5px" icon="ep:plus" />
          开通年费会员
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" :show-overflow-tooltip="true" :stripe="true">
      <el-table-column align="center" label="ID" prop="id" width="90" />
      <el-table-column align="center" label="用户 ID" prop="userId" width="110" />
      <el-table-column label="会员编号" min-width="180" prop="memberId" />
      <el-table-column label="会员方案" min-width="180" prop="planName" />
      <el-table-column align="center" label="状态" prop="status" width="150">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column align="center" label="自动续费" prop="autoRenew" width="120">
        <template #default="{ row }">
          <el-tag :type="row.autoRenew ? 'success' : 'info'">{{ row.autoRenew ? '开启' : '关闭' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column align="center" label="开始时间" prop="startedAt" width="180" />
      <el-table-column align="center" label="到期时间" prop="expiresAt" width="180" />
      <el-table-column align="center" fixed="right" label="操作" width="160">
        <template #default="{ row }">
          <el-button v-hasPermi="['member:membership:query']" link type="primary" @click="openDetail(row.id)">详情</el-button>
          <el-button v-hasPermi="['member:membership:update']" link type="success" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination v-model:limit="queryParams.pageSize" v-model:page="queryParams.pageNo" :total="total" @pagination="getList" />
  </ContentWrap>

  <el-drawer v-model="detailVisible" size="520px" title="会员权益详情">
    <el-descriptions v-if="detail" :column="1" border>
      <el-descriptions-item label="用户 ID">{{ detail.userId }}</el-descriptions-item>
      <el-descriptions-item label="会员编号">{{ detail.memberId || '-' }}</el-descriptions-item>
      <el-descriptions-item label="会员方案">{{ detail.planName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
      <el-descriptions-item label="开始时间">{{ detail.startedAt || '-' }}</el-descriptions-item>
      <el-descriptions-item label="到期时间">{{ detail.expiresAt || '-' }}</el-descriptions-item>
      <el-descriptions-item label="自动续费">{{ detail.autoRenew ? '开启' : '关闭' }}</el-descriptions-item>
      <el-descriptions-item label="来源订单">{{ detail.sourceOrderId || '-' }}</el-descriptions-item>
      <el-descriptions-item label="来源支付单">{{ detail.sourcePayOrderId || '-' }}</el-descriptions-item>
    </el-descriptions>
  </el-drawer>

  <el-dialog v-model="formVisible" :title="formMode === 'create' ? '开通年费会员' : '编辑会员权益'" width="460px">
    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px">
      <el-form-item v-if="formMode === 'create'" label="用户 ID" prop="userId">
        <el-input-number v-model="formData.userId" class="!w-220px" :min="1" controls-position="right" />
      </el-form-item>
      <template v-else>
        <el-form-item label="状态" prop="status">
          <el-select v-model="formData.status" class="!w-220px">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="到期时间" prop="expiresAt">
          <el-date-picker v-model="formData.expiresAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
        <el-form-item label="自动续费" prop="autoRenew">
          <el-switch v-model="formData.autoRenew" />
        </el-form-item>
      </template>
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <el-button :loading="formLoading" type="primary" @click="submitForm">确认</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus'
import * as MembershipApi from '@/api/member/membership'

defineOptions({ name: 'MemberMembership' })

const message = useMessage()

const statusOptions = [
  { label: '年费有效', value: 'active_annual' },
  { label: '已到期', value: 'expired' },
  { label: '待绑定', value: 'pending_link' },
  { label: '非会员', value: 'not_member' }
]

const loading = ref(false)
const list = ref<MembershipApi.MemberMembershipVO[]>([])
const total = ref(0)
const queryFormRef = ref<FormInstance>()
const queryParams = reactive<MembershipApi.MemberMembershipPageReqVO>({
  pageNo: 1,
  pageSize: 10,
  userId: undefined,
  memberId: '',
  status: ''
})

const detailVisible = ref(false)
const detail = ref<MembershipApi.MemberMembershipVO>()
const formVisible = ref(false)
const formLoading = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formRef = ref<FormInstance>()
const formData = reactive<MembershipApi.MemberMembershipOpenReqVO & MembershipApi.MemberMembershipUpdateReqVO>({
  id: 0,
  userId: 0,
  status: 'active_annual',
  expiresAt: '',
  autoRenew: true
})
const formRules = computed<FormRules>(() => ({
  userId: formMode.value === 'create' ? [{ required: true, message: '请输入用户 ID', trigger: 'blur' }] : [],
  status: formMode.value === 'edit' ? [{ required: true, message: '请选择状态', trigger: 'change' }] : []
}))

const statusLabel = (status?: string) => statusOptions.find((item) => item.value === status)?.label || '未知'
const statusTagType = (status?: string) => {
  if (status === 'active_annual') return 'success'
  if (status === 'expired') return 'danger'
  if (status === 'pending_link') return 'warning'
  return 'info'
}

const getList = async () => {
  loading.value = true
  try {
    const data = await MembershipApi.getMembershipPage(queryParams)
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
  detail.value = await MembershipApi.getMembership(id)
  detailVisible.value = true
}

const openCreate = () => {
  formMode.value = 'create'
  Object.assign(formData, { id: 0, userId: 0, status: 'active_annual', expiresAt: '', autoRenew: true })
  formVisible.value = true
}

const openEdit = (row: MembershipApi.MemberMembershipVO) => {
  formMode.value = 'edit'
  Object.assign(formData, {
    id: row.id,
    userId: row.userId,
    status: row.status || 'active_annual',
    expiresAt: row.expiresAt || '',
    autoRenew: row.autoRenew === true
  })
  formVisible.value = true
}

const submitForm = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return
  formLoading.value = true
  try {
    if (formMode.value === 'create') {
      await MembershipApi.openMembership({ userId: formData.userId })
    } else {
      await MembershipApi.updateMembership({
        id: formData.id,
        status: formData.status,
        expiresAt: formData.expiresAt,
        autoRenew: formData.autoRenew
      })
    }
    message.success('会员权益已保存')
    formVisible.value = false
    await getList()
  } finally {
    formLoading.value = false
  }
}

onMounted(() => {
  getList()
})
</script>
