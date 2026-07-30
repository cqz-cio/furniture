<template>
  <el-alert
    class="mb-16px"
    :closable="false"
    type="info"
    show-icon
    title="联系人管理保存客户公司中具体对接人的姓名、邮箱和电话；一个客户公司可以有多位联系人。"
  />

  <ContentWrap>
    <el-form
      ref="queryFormRef"
      :inline="true"
      :model="queryParams"
      class="-mb-15px"
      label-width="76px"
    >
      <el-form-item label="客户公司" prop="customerId">
        <el-select
          v-model="queryParams.customerId"
          class="!w-240px"
          clearable
          filterable
          placeholder="请选择客户公司"
        >
          <el-option
            v-for="item in customerList"
            :key="item.id"
            :label="item.name"
            :value="item.id!"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="联系人" prop="name">
        <el-input
          v-model="queryParams.name"
          class="!w-200px"
          clearable
          placeholder="请输入联系人"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="电话" prop="telephone">
        <el-input
          v-model="queryParams.telephone"
          class="!w-200px"
          clearable
          placeholder="请输入电话"
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
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        <el-button
          v-hasPermi="['crm:contact:create']"
          type="primary"
          @click="openForm('create')"
        >
          <Icon icon="ep:plus" class="mr-5px" />新增联系人
        </el-button>
        <el-button
          v-hasPermi="['crm:contact:export']"
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
      <el-table-column label="联系人" prop="name" fixed="left" min-width="150">
        <template #default="{ row }">
          <el-link :underline="false" type="primary" @click="openDetail(row.id)">
            {{ row.name }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column label="所属客户公司" prop="customerName" min-width="190">
        <template #default="{ row }">
          <el-link :underline="false" type="primary" @click="openCustomerDetail(row.customerId)">
            {{ row.customerName }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column label="电话 / WhatsApp" prop="telephone" min-width="170" />
      <el-table-column label="邮箱" prop="email" min-width="200" />
      <el-table-column label="职位" prop="post" width="140" />
      <el-table-column label="主要联系人" prop="master" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.master ? 'success' : 'info'">
            {{ row.master ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="负责人" prop="ownerUserName" width="120" />
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
            v-hasPermi="['crm:contact:update']"
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

  <ContactForm ref="formRef" @success="getList" />
</template>

<script lang="ts" setup>
import { dateFormatter } from '@/utils/formatTime'
import download from '@/utils/download'
import * as ContactApi from '@/api/crm/contact'
import * as CustomerApi from '@/api/crm/customer'
import ContactForm from './ContactForm.vue'

defineOptions({ name: 'CrmContact' })

const message = useMessage()
const loading = ref(true)
const exportLoading = ref(false)
const total = ref(0)
const list = ref<ContactApi.ContactVO[]>([])
const customerList = ref<CustomerApi.CustomerVO[]>([])
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  customerId: undefined as number | undefined,
  name: undefined as string | undefined,
  telephone: undefined as string | undefined,
  email: undefined as string | undefined
})
const queryFormRef = ref()

const getList = async () => {
  loading.value = true
  try {
    const data = await ContactApi.getContactPage(queryParams)
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

const formRef = ref<InstanceType<typeof ContactForm>>()
const openForm = (type: string, id?: number) => {
  formRef.value?.open(type, id)
}

const { push } = useRouter()
const openDetail = (id: number) => {
  push({ name: 'CrmContactDetail', params: { id } })
}
const openCustomerDetail = (id: number) => {
  push({ name: 'CrmCustomerDetail', params: { id } })
}

const handleExport = async () => {
  try {
    await message.exportConfirm()
    exportLoading.value = true
    const data = await ContactApi.exportContact(queryParams)
    download.excel(data, '联系人.xls')
  } finally {
    exportLoading.value = false
  }
}

onMounted(async () => {
  await Promise.all([getList(), CustomerApi.getCustomerSimpleList().then((data) => (customerList.value = data))])
})
</script>
