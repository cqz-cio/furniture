<template>
  <ContentWrap v-loading="loading">
    <div class="panel-header">
      <div>
        <div class="panel-title">询盘邮件投递</div>
        <div class="panel-subtitle">ERP 中转状态、绑定邮箱与客户回复地址</div>
      </div>
      <el-button
        v-hasPermi="['crm:clue:update']"
        :loading="resendLoading"
        :type="delivery?.status === DeliveryStatus.SUCCESS ? 'default' : 'primary'"
        @click="handleResend"
      >
        <Icon icon="ep:promotion" class="mr-5px" />
        {{ delivery ? '重新发送' : '发送到绑定邮箱' }}
      </el-button>
    </div>

    <el-empty v-if="!loading && !delivery" :image-size="72" description="尚无邮件投递记录">
      <div class="empty-tip">可点击“发送到绑定邮箱”为历史询盘创建投递记录。</div>
    </el-empty>

    <template v-else-if="delivery">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="投递状态">
          <el-tag :type="statusMeta.type">{{ statusMeta.label }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="尝试次数">
          {{ delivery.attemptCount || 0 }}
        </el-descriptions-item>
        <el-descriptions-item label="邮件日志">
          {{ delivery.mailLogId ? `#${delivery.mailLogId}` : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="ERP 绑定邮箱">
          <el-link
            v-if="delivery.recipientEmail"
            :href="`mailto:${delivery.recipientEmail}`"
            type="primary"
          >
            {{ delivery.recipientEmail }}
          </el-link>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="客户回复邮箱">
          <el-link
            v-if="delivery.customerEmail"
            :href="`mailto:${delivery.customerEmail}`"
            type="primary"
          >
            {{ delivery.customerEmail }}
          </el-link>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="发送成功时间">
          {{ formatOptionalDate(delivery.sentTime) }}
        </el-descriptions-item>
        <el-descriptions-item
          v-if="delivery.status !== DeliveryStatus.SUCCESS"
          label="下次自动重试"
        >
          {{ formatOptionalDate(delivery.nextRetryTime) }}
        </el-descriptions-item>
        <el-descriptions-item
          v-if="delivery.lastError"
          label="最近状态说明"
          :span="delivery.status !== DeliveryStatus.SUCCESS ? 2 : 3"
        >
          <span :class="{ 'error-text': delivery.status === DeliveryStatus.FAILURE }">
            {{ delivery.lastError }}
          </span>
        </el-descriptions-item>
      </el-descriptions>

      <el-alert
        class="mt-14px"
        type="success"
        :closable="false"
        show-icon
        title="邮件的 Reply-To 已指向客户邮箱；在邮箱客户端点击“回复”即可直接给客户回信。"
      />
    </template>
  </ContentWrap>
</template>

<script setup lang="ts">
import { formatDate } from '@/utils/formatTime'
import * as InquiryMailApi from '@/api/system/websiteInquiryMail'
import { WebsiteInquiryMailDeliveryStatus as DeliveryStatus } from '@/api/system/websiteInquiryMail'

defineOptions({ name: 'InquiryMailDeliveryPanel' })

const props = defineProps<{
  inquiryId: number
}>()

const message = useMessage()
const loading = ref(false)
const resendLoading = ref(false)
const delivery = ref<InquiryMailApi.WebsiteInquiryMailDeliveryVO | null>(null)

const statusMetadata = {
  [DeliveryStatus.PENDING]: { label: '待发送', type: 'info' },
  [DeliveryStatus.SENDING]: { label: '发送中', type: 'primary' },
  [DeliveryStatus.SUCCESS]: { label: '发送成功', type: 'success' },
  [DeliveryStatus.FAILURE]: { label: '发送失败', type: 'danger' },
  [DeliveryStatus.CONFIG_REQUIRED]: { label: '等待配置', type: 'warning' }
} as const

const statusMeta = computed(
  () =>
    statusMetadata[delivery.value?.status ?? DeliveryStatus.PENDING] ||
    statusMetadata[DeliveryStatus.PENDING]
)

const formatOptionalDate = (value?: Date) => (value ? formatDate(value) : '-')

const getDelivery = async () => {
  if (!props.inquiryId) return
  loading.value = true
  try {
    delivery.value = await InquiryMailApi.getWebsiteInquiryMailDelivery(props.inquiryId)
  } finally {
    loading.value = false
  }
}

const handleResend = async () => {
  resendLoading.value = true
  try {
    await InquiryMailApi.resendWebsiteInquiryMail(props.inquiryId)
    message.success('询盘邮件已进入发送队列')
    await getDelivery()
  } finally {
    resendLoading.value = false
  }
}

watch(() => props.inquiryId, getDelivery, { immediate: true })
</script>

<style scoped>
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.panel-title {
  color: var(--el-text-color-primary);
  font-size: 16px;
  font-weight: 600;
}

.panel-subtitle,
.empty-tip {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.error-text {
  color: var(--el-color-danger);
}
</style>
