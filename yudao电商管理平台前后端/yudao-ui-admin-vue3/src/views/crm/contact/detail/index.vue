<template>
  <ContactDetailsHeader v-loading="loading" :contact="contact">
    <el-button
      v-hasPermi="['crm:contact:update']"
      type="primary"
      @click="openForm('update', contact.id)"
    >
      编辑联系人
    </el-button>
  </ContactDetailsHeader>

  <el-tabs>
    <el-tab-pane label="联系人资料">
      <ContactDetailsInfo :contact="contact" />
    </el-tab-pane>
    <el-tab-pane label="操作日志">
      <OperateLogV2 :log-list="logList" />
    </el-tab-pane>
  </el-tabs>

  <ContactForm ref="formRef" @success="getContact" />
</template>

<script lang="ts" setup>
import { useTagsViewStore } from '@/store/modules/tagsView'
import * as ContactApi from '@/api/crm/contact'
import ContactDetailsHeader from './ContactDetailsHeader.vue'
import ContactDetailsInfo from './ContactDetailsInfo.vue'
import { BizTypeEnum } from '@/api/crm/permission'
import type { OperateLogVO } from '@/api/system/operatelog'
import { getOperateLogPage } from '@/api/crm/operateLog'
import ContactForm from '@/views/crm/contact/ContactForm.vue'

defineOptions({ name: 'CrmContactDetail' })

const message = useMessage()
const contactId = ref(0)
const loading = ref(true)
const contact = ref<ContactApi.ContactVO>({} as ContactApi.ContactVO)
const logList = ref<OperateLogVO[]>([])

const getContact = async () => {
  loading.value = true
  try {
    contact.value = await ContactApi.getContact(contactId.value)
    const data = await getOperateLogPage({
      bizType: BizTypeEnum.CRM_CONTACT,
      bizId: contactId.value
    })
    logList.value = data.list
  } finally {
    loading.value = false
  }
}

const formRef = ref<InstanceType<typeof ContactForm>>()
const openForm = (type: string, id?: number) => {
  formRef.value?.open(type, id)
}

const { delView } = useTagsViewStore()
const { currentRoute } = useRouter()
const close = () => {
  delView(unref(currentRoute))
}

const { params } = useRoute()
onMounted(async () => {
  if (!params.id) {
    message.warning('参数错误，联系人不能为空！')
    close()
    return
  }
  contactId.value = params.id as unknown as number
  await getContact()
})
</script>
