<template>
  <el-alert
    v-if="visible"
    class="inquiry-mail-health"
    :closable="false"
    :show-icon="true"
    :type="alertType"
  >
    <template #title>
      <strong>{{ title }}</strong>
    </template>
    <div class="inquiry-mail-health__body">
      <span>{{ description }}</span>
      <el-button
        v-if="canConfigure && status !== 'healthy'"
        v-hasPermi="['crm:clue:update']"
        link
        type="primary"
        @click="emit('configure')"
      >
        立即配置并发送测试邮件
      </el-button>
      <el-button v-if="status === 'error'" link type="primary" @click="refresh">
        重新检查
      </el-button>
    </div>
  </el-alert>
</template>

<script setup lang="ts">
import * as InquiryMailApi from '@/api/system/websiteInquiryMail'

defineOptions({ name: 'InquiryMailHealthAlert' })

const props = withDefaults(
  defineProps<{
    showHealthy?: boolean
    canConfigure?: boolean
  }>(),
  { showHealthy: false, canConfigure: true }
)

const emit = defineEmits<{ configure: [] }>()
const status = ref<'loading' | 'healthy' | 'unconfigured' | 'disabled' | 'error'>('loading')
const config = ref<InquiryMailApi.WebsiteInquiryMailConfigVO>()

const visible = computed(
  () => status.value !== 'loading' && (props.showHealthy || status.value !== 'healthy')
)
const alertType = computed(() => {
  if (status.value === 'healthy') return 'success'
  if (status.value === 'error') return 'warning'
  return 'error'
})
const title = computed(() => {
  if (status.value === 'healthy') return '询盘邮件通知正常'
  if (status.value === 'disabled') return '询盘邮件通知已停用'
  if (status.value === 'error') return '暂时无法检查询盘邮件通知'
  return '询盘邮件通知尚未配置'
})
const description = computed(() => {
  if (status.value === 'healthy') {
    return `新询盘将发送到 ${config.value?.recipientEmail || '已绑定邮箱'}。`
  }
  if (status.value === 'disabled') {
    return '新询盘仍会保存在 ERP，但不会发送到业务邮箱，可能造成回复延迟。'
  }
  if (status.value === 'error') {
    return '询盘数据不受影响；请刷新检查配置状态。'
  }
  return '新询盘只会进入 ERP，不会主动送达业务邮箱。请配置 SMTP、接收邮箱并完成测试发送。'
})

const refresh = async () => {
  status.value = 'loading'
  try {
    config.value = await InquiryMailApi.getWebsiteInquiryMailConfig()
    if (!config.value?.configured) status.value = 'unconfigured'
    else if (!config.value.enabled) status.value = 'disabled'
    else status.value = 'healthy'
  } catch {
    status.value = 'error'
  }
}

defineExpose({ refresh })
onMounted(refresh)
</script>

<style scoped>
.inquiry-mail-health__body {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 12px;
  line-height: 1.6;
}
</style>
