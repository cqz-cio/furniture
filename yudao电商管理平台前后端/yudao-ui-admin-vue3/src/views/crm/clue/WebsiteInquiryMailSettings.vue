<template>
  <Dialog v-model="dialogVisible" title="询盘邮件通知设置" :width="920">
    <el-alert
      class="mb-18px"
      type="info"
      :closable="false"
      show-icon
      title="ERP 会把新询盘转发到绑定邮箱；邮件的 Reply-To 会设置为客户邮箱，可直接在邮件客户端点击回复。"
    />

    <el-form
      ref="formRef"
      v-loading="loading"
      :model="formData"
      :rules="formRules"
      label-width="150px"
    >
      <el-form-item label="启用邮件转发" prop="enabled">
        <el-switch v-model="formData.enabled" />
      </el-form-item>

      <el-form-item label="绑定接收邮箱" prop="recipientEmail">
        <el-input
          v-model="formData.recipientEmail"
          class="!w-520px"
          clearable
          placeholder="例如 sales@your-company.com"
        />
        <div class="form-tip">该地址会在 ERP 中保存并回显，新询盘将发到这里。</div>
      </el-form-item>

      <el-form-item label="平台发件邮箱" prop="mailAccountId">
        <el-select
          v-model="formData.mailAccountId"
          class="!w-520px"
          clearable
          filterable
          placeholder="请选择已配置 SMTP 的邮箱账号"
        >
          <el-option
            v-for="account in accountList"
            :key="account.id"
            :label="account.mail"
            :value="account.id"
          />
        </el-select>
        <div v-if="!accountList.length" class="form-tip is-warning">
          暂无可用发件邮箱，请先由管理员在“系统管理 → 邮箱账号”配置 SMTP。
        </div>
      </el-form-item>

      <el-form-item label="实际发件地址">
        <el-input
          :model-value="selectedSenderEmail || '选择发件邮箱后显示'"
          class="!w-520px"
          disabled
        />
      </el-form-item>

      <el-form-item label="发件人显示名称" prop="senderName">
        <el-input
          v-model="formData.senderName"
          class="!w-520px"
          placeholder="例如 VANZ Inquiry Desk"
        />
      </el-form-item>

      <el-form-item label="ERP 管理端地址" prop="erpBaseUrl">
        <el-input
          v-model="formData.erpBaseUrl"
          class="!w-520px"
          placeholder="例如 https://erp.example.com"
        />
        <div class="form-tip">用于邮件中的“在 ERP 查看询盘”按钮。</div>
      </el-form-item>

      <el-form-item label="模板变量">
        <div class="template-variable-panel">
          <el-radio-group v-model="insertTarget" size="small">
            <el-radio-button value="subject">插入标题</el-radio-button>
            <el-radio-button value="content">插入正文</el-radio-button>
          </el-radio-group>
          <div class="template-variable-list">
            <el-button
              v-for="variable in availableVariables"
              :key="variable"
              class="variable-button"
              size="small"
              @click="insertVariable(variable)"
            >
              {{ formatVariable(variable) }}
            </el-button>
          </div>
        </div>
      </el-form-item>

      <el-form-item label="邮件标题" prop="subjectTemplate">
        <el-input
          v-model="formData.subjectTemplate"
          maxlength="255"
          placeholder="请输入询盘邮件标题"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="邮件正文格式" prop="contentTemplate">
        <div class="editor-wrap">
          <Editor
            v-model="formData.contentTemplate"
            editor-id="website-inquiry-mail-editor"
            height="340px"
          />
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button
        :disabled="loading || !formData.enabled"
        :loading="testLoading"
        @click="saveAndSendTest"
      >
        <Icon icon="ep:promotion" class="mr-5px" />保存并发送测试邮件
      </el-button>
      <el-button :disabled="loading" type="primary" @click="submitForm"> 保存设置 </el-button>
      <el-button @click="dialogVisible = false">取消</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import * as MailAccountApi from '@/api/system/mail/account'
import * as InquiryMailApi from '@/api/system/websiteInquiryMail'

defineOptions({ name: 'WebsiteInquiryMailSettings' })

type InsertTarget = 'subject' | 'content'

const message = useMessage()
const dialogVisible = ref(false)
const loading = ref(false)
const testLoading = ref(false)
const formRef = ref<FormInstance>()
const accountList = ref<MailAccountApi.MailAccountVO[]>([])
const availableVariables = ref<string[]>([])
const insertTarget = ref<InsertTarget>('content')
const formData = ref<InquiryMailApi.WebsiteInquiryMailConfigSaveVO>({
  enabled: false,
  recipientEmail: '',
  mailAccountId: undefined,
  senderName: '',
  subjectTemplate: '',
  contentTemplate: '',
  erpBaseUrl: ''
})

const requiredWhenEnabled = (messageText: string) => ({
  validator: (_rule: unknown, value: unknown, callback: (error?: Error) => void) => {
    if (formData.value.enabled && (value === undefined || value === null || value === '')) {
      callback(new Error(messageText))
      return
    }
    callback()
  },
  trigger: ['blur', 'change']
})

const formRules: FormRules = {
  recipientEmail: [
    requiredWhenEnabled('启用邮件转发时必须填写接收邮箱'),
    { type: 'email', message: '接收邮箱格式不正确', trigger: 'blur' }
  ],
  mailAccountId: [requiredWhenEnabled('启用邮件转发时必须选择发件邮箱')],
  senderName: [{ required: true, message: '发件人显示名称不能为空', trigger: 'blur' }],
  erpBaseUrl: [requiredWhenEnabled('启用邮件转发时必须填写 ERP 地址')],
  subjectTemplate: [{ required: true, message: '邮件标题不能为空', trigger: 'blur' }],
  contentTemplate: [{ required: true, message: '邮件正文不能为空', trigger: 'blur' }]
}

const selectedSenderEmail = computed(
  () => accountList.value.find((item) => item.id === formData.value.mailAccountId)?.mail
)

const open = async () => {
  dialogVisible.value = true
  loading.value = true
  try {
    const [config, accounts] = await Promise.all([
      InquiryMailApi.getWebsiteInquiryMailConfig(),
      MailAccountApi.getSimpleMailAccountList()
    ])
    accountList.value = accounts
    availableVariables.value = config.availableVariables || []
    formData.value = {
      enabled: Boolean(config.enabled),
      recipientEmail: config.recipientEmail || '',
      mailAccountId: config.mailAccountId,
      senderName: config.senderName,
      subjectTemplate: config.subjectTemplate,
      contentTemplate: config.contentTemplate,
      erpBaseUrl: config.erpBaseUrl || window.location.origin
    }
    await nextTick()
    formRef.value?.clearValidate()
  } finally {
    loading.value = false
  }
}
defineExpose({ open })

const insertVariable = (variable: string) => {
  const token = formatVariable(variable)
  if (insertTarget.value === 'subject') {
    formData.value.subjectTemplate = `${formData.value.subjectTemplate}${token}`
  } else {
    formData.value.contentTemplate = `${formData.value.contentTemplate}<span>${token}</span>`
  }
}

const formatVariable = (variable: string) => `{${variable}}`

const save = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return false
  loading.value = true
  try {
    await InquiryMailApi.saveWebsiteInquiryMailConfig(formData.value)
    return true
  } finally {
    loading.value = false
  }
}

const emit = defineEmits<{
  success: []
}>()

const submitForm = async () => {
  if (!(await save())) return
  message.success('询盘邮件设置已保存')
  dialogVisible.value = false
  emit('success')
}

const saveAndSendTest = async () => {
  if (!(await save())) return
  testLoading.value = true
  try {
    await InquiryMailApi.sendWebsiteInquiryTestMail()
    message.success('测试邮件已进入发送队列，请检查绑定邮箱')
    emit('success')
  } finally {
    testLoading.value = false
  }
}
</script>

<style scoped>
.form-tip {
  width: 100%;
  margin-top: 5px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.form-tip.is-warning {
  color: var(--el-color-warning);
}

.template-variable-panel,
.editor-wrap {
  width: 100%;
}

.template-variable-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.variable-button {
  margin-left: 0;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}
</style>
