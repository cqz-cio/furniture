<template>
  <ContentWrap>
    <el-form
      ref="queryFormRef"
      :inline="true"
      :model="queryParams"
      class="-mb-15px"
      label-width="76px"
    >
      <el-form-item label="客户名称" prop="name">
        <el-input
          v-model="queryParams.name"
          class="!w-240px"
          clearable
          placeholder="请输入公司或客户名称"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="联系电话" prop="telephone">
        <el-input
          v-model="queryParams.telephone"
          class="!w-220px"
          clearable
          placeholder="请输入联系电话"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="客户来源" prop="source">
        <el-select
          v-model="queryParams.source"
          class="!w-180px"
          clearable
          placeholder="全部来源"
        >
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.CRM_CUSTOMER_SOURCE)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        <el-button
          v-hasPermi="['crm:customer:create']"
          type="primary"
          @click="openForm('create')"
        >
          <Icon icon="ep:plus" class="mr-5px" />新增客户档案
        </el-button>
        <el-button
          v-hasPermi="['crm:customer:export']"
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
    <el-table v-loading="loading" :data="list" :show-overflow-tooltip="true" :stripe="true">
      <el-table-column label="客户名称" prop="name" fixed="left" min-width="190">
        <template #default="{ row }">
          <el-link :underline="false" type="primary" @click="openDetail(row.id)">
            {{ row.name }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column label="客户来源" prop="source" width="110" align="center">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.CRM_CUSTOMER_SOURCE" :value="row.source" />
        </template>
      </el-table-column>
      <el-table-column label="联系电话" prop="telephone" min-width="150" />
      <el-table-column label="邮箱" prop="email" min-width="200" />
      <el-table-column label="负责人" prop="ownerUserName" width="120" />
      <el-table-column label="备注" prop="remark" min-width="230" />
      <el-table-column
        label="建立时间"
        prop="createTime"
        :formatter="dateFormatter"
        width="180"
      />
      <el-table-column label="操作" align="center" fixed="right" width="150">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">查看</el-button>
          <el-button
            v-hasPermi="['crm:customer:update']"
            link
            type="primary"
            @click="openForm('update', row.id)"
          >
            编辑
          </el-button>
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

  <CustomerForm ref="formRef" @success="getList" />
</template>

<script lang="ts" setup>
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { dateFormatter } from '@/utils/formatTime'
import download from '@/utils/download'
import * as CustomerApi from '@/api/crm/customer'
import CustomerForm from './CustomerForm.vue'

defineOptions({ name: 'CrmCustomer' })

const message = useMessage()
const loading = ref(true)
const exportLoading = ref(false)
const total = ref(0)
const list = ref<CustomerApi.CustomerVO[]>([])
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  name: '',
  telephone: '',
  source: undefined as number | undefined
})
const queryFormRef = ref()

const getList = async () => {
  loading.value = true
  try {
    const data = await CustomerApi.getCustomerPage(queryParams)
    list.value = data.list
    total.value = data.total
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

const { push } = useRouter()
const openDetail = (id: number) => {
  push({ name: 'CrmCustomerDetail', params: { id } })
}

const formRef = ref<InstanceType<typeof CustomerForm>>()
const openForm = (type: string, id?: number) => {
  formRef.value?.open(type, id)
}

const handleExport = async () => {
  try {
    await message.exportConfirm()
    exportLoading.value = true
    const data = await CustomerApi.exportCustomer(queryParams)
    download.excel(data, '客户档案.xls')
  } finally {
    exportLoading.value = false
  }
}

onMounted(getList)
</script>
