<template>
  <CustomerDetailsHeader :customer="customer" :loading="loading">
    <el-button
      v-hasPermi="['crm:customer:update']"
      type="primary"
      @click="openForm"
    >
      编辑客户档案
    </el-button>
  </CustomerDetailsHeader>

  <el-tabs>
    <el-tab-pane label="客户信息">
      <CustomerDetailsInfo :customer="customer" />
    </el-tab-pane>
    <el-tab-pane label="历史询盘" lazy>
      <CustomerInquiryList :customer-id="customer.id!" />
    </el-tab-pane>
    <el-tab-pane label="联系人" lazy>
      <ContactList
        :biz-id="customer.id!"
        :customer-id="customer.id!"
        :biz-type="BizTypeEnum.CRM_CUSTOMER"
      />
    </el-tab-pane>
    <el-tab-pane label="操作日志">
      <OperateLogV2 :log-list="logList" />
    </el-tab-pane>
  </el-tabs>

  <CustomerForm ref="formRef" @success="getCustomer" />
</template>

<script lang="ts" setup>
import { useTagsViewStore } from '@/store/modules/tagsView'
import * as CustomerApi from '@/api/crm/customer'
import CustomerForm from '@/views/crm/customer/CustomerForm.vue'
import CustomerDetailsInfo from './CustomerDetailsInfo.vue'
import CustomerDetailsHeader from './CustomerDetailsHeader.vue'
import CustomerInquiryList from './CustomerInquiryList.vue'
import ContactList from '@/views/crm/contact/components/ContactList.vue'
import { BizTypeEnum } from '@/api/crm/permission'
import type { OperateLogVO } from '@/api/system/operatelog'
import { getOperateLogPage } from '@/api/crm/operateLog'

defineOptions({ name: 'CrmCustomerDetail' })

const customerId = ref(0)
const loading = ref(true)
const message = useMessage()
const { delView } = useTagsViewStore()
const { currentRoute, push } = useRouter()
const customer = ref<CustomerApi.CustomerVO>({} as CustomerApi.CustomerVO)
const logList = ref<OperateLogVO[]>([])

const getCustomer = async () => {
  loading.value = true
  try {
    customer.value = await CustomerApi.getCustomer(customerId.value)
    const data = await getOperateLogPage({
      bizType: BizTypeEnum.CRM_CUSTOMER,
      bizId: customerId.value
    })
    logList.value = data.list
  } finally {
    loading.value = false
  }
}

const formRef = ref<InstanceType<typeof CustomerForm>>()
const openForm = () => {
  formRef.value?.open('update', customerId.value)
}

const close = () => {
  delView(unref(currentRoute))
  push({ name: 'CrmCustomer' })
}

const { params } = useRoute()
onMounted(() => {
  if (!params.id) {
    message.warning('参数错误，客户档案不能为空！')
    close()
    return
  }
  customerId.value = params.id as unknown as number
  getCustomer()
})
</script>
