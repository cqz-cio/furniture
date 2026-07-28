<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="760px">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="110px"
    >
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="联系人姓名" prop="contactName">
            <el-input
              v-model="formData.contactName"
              maxlength="60"
              placeholder="请输入联系人姓名"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="formData.email" maxlength="120" placeholder="请输入邮箱" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="电话区号" prop="countryCode">
            <el-input
              v-model="formData.countryCode"
              maxlength="8"
              placeholder="例如 +44"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="电话 / WhatsApp" prop="telephone">
            <el-input
              v-model="formData.telephone"
              maxlength="40"
              placeholder="请输入电话或 WhatsApp"
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="公司名称" prop="companyName">
        <el-input
          v-model="formData.companyName"
          maxlength="80"
          placeholder="未填写时请先补充，再生成客户档案"
        />
      </el-form-item>
      <el-form-item label="询盘主题" prop="inquirySubject">
        <el-input
          v-model="formData.inquirySubject"
          maxlength="100"
          placeholder="请输入询盘主题"
        />
      </el-form-item>
      <el-form-item label="询盘内容" prop="inquiryMessage">
        <el-input
          v-model="formData.inquiryMessage"
          type="textarea"
          :rows="7"
          maxlength="4000"
          show-word-limit
          placeholder="请输入客户的具体需求"
        />
      </el-form-item>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="处理人" prop="ownerUserId">
            <el-select
              v-model="formData.ownerUserId"
              :disabled="formType !== 'create'"
              class="w-1/1"
            >
              <el-option
                v-for="item in userOptions"
                :key="item.id"
                :label="item.nickname"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="处理备注" prop="remark">
            <el-input
              v-model="formData.remark"
              maxlength="500"
              placeholder="可填写内部处理说明"
            />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">保存</el-button>
      <el-button @click="dialogVisible = false">取消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as ClueApi from '@/api/crm/clue'
import * as UserApi from '@/api/system/user'
import { useUserStore } from '@/store/modules/user'

const message = useMessage()
const dialogVisible = ref(false)
const dialogTitle = ref('编辑询盘')
const formLoading = ref(false)
const formType = ref('update')
const userOptions = ref<UserApi.UserVO[]>([])
const formRef = ref()

const emptyForm = () => ({
  id: undefined as number | undefined,
  name: '',
  contactName: '',
  email: '',
  countryCode: '',
  telephone: '',
  companyName: '',
  inquirySubject: '',
  inquiryMessage: '',
  ownerUserId: 0,
  remark: ''
})
const formData = ref(emptyForm())
const formRules = reactive({
  contactName: [{ required: true, message: '联系人姓名不能为空', trigger: 'blur' }],
  email: [
    { required: true, message: '邮箱不能为空', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  inquirySubject: [{ required: true, message: '询盘主题不能为空', trigger: 'blur' }],
  ownerUserId: [{ required: true, message: '处理人不能为空', trigger: 'change' }]
})

const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  formType.value = type
  dialogTitle.value = type === 'create' ? '新增询盘' : '编辑询盘'
  formData.value = emptyForm()
  formRef.value?.resetFields()
  userOptions.value = await UserApi.getSimpleUserList()
  if (id) {
    formLoading.value = true
    try {
      formData.value = {
        ...emptyForm(),
        ...(await ClueApi.getClue(id))
      }
    } finally {
      formLoading.value = false
    }
  } else {
    formData.value.ownerUserId = useUserStore().getUser.id
  }
}
defineExpose({ open })

const emit = defineEmits(['success'])
const submitForm = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return
  formLoading.value = true
  try {
    const displayPrefix = formData.value.companyName || formData.value.contactName
    formData.value.name = `${displayPrefix} · ${formData.value.inquirySubject}`.slice(0, 128)
    const data = formData.value as unknown as ClueApi.ClueVO
    if (formType.value === 'create') {
      await ClueApi.createClue(data)
      message.success('询盘创建成功')
    } else {
      await ClueApi.updateClue(data)
      message.success('询盘信息已更新')
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}
</script>
