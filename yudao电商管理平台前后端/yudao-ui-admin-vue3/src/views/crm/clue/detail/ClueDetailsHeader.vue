<template>
  <div v-loading="loading">
    <div class="flex items-start justify-between">
      <div class="flex items-center gap-12px">
        <span class="text-xl font-bold">{{ clue.inquirySubject || clue.name }}</span>
        <el-tag :type="statusMeta.type">{{ statusMeta.label }}</el-tag>
      </div>
      <div>
        <slot></slot>
      </div>
    </div>
  </div>
  <ContentWrap class="mt-10px">
    <el-descriptions :column="5" direction="vertical">
      <el-descriptions-item label="公司名称">
        {{ clue.companyName || '待补充' }}
      </el-descriptions-item>
      <el-descriptions-item label="联系人">{{ clue.contactName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="电话 / WhatsApp">{{ displayPhone }}</el-descriptions-item>
      <el-descriptions-item label="邮箱">{{ clue.email || '-' }}</el-descriptions-item>
      <el-descriptions-item label="提交时间">
        {{ formatDate(clue.submittedAt || clue.createTime) }}
      </el-descriptions-item>
    </el-descriptions>
  </ContentWrap>
</template>

<script lang="ts" setup>
import * as ClueApi from '@/api/crm/clue'
import { InquiryProcessStatus } from '@/api/crm/clue'
import { formatDate } from '@/utils/formatTime'

defineOptions({ name: 'CrmClueDetailsHeader' })
const props = defineProps<{
  clue: ClueApi.ClueVO
  loading: boolean
}>()

const statusMeta = computed(() => {
  const metadata = {
    [InquiryProcessStatus.PENDING]: { label: '待处理', type: 'warning' },
    [InquiryProcessStatus.PROCESSING]: { label: '处理中', type: 'primary' },
    [InquiryProcessStatus.PROCESSED]: { label: '已处理', type: 'success' },
    [InquiryProcessStatus.INVALID]: { label: '无效询盘', type: 'info' }
  } as const
  return metadata[props.clue.processStatus] || metadata[InquiryProcessStatus.PENDING]
})

const displayPhone = computed(
  () => [props.clue.countryCode, props.clue.telephone].filter(Boolean).join(' ') || '-'
)
</script>
