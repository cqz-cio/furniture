<template>
  <ContentWrap>
    <el-collapse v-model="activeNames">
      <el-collapse-item name="inquiry">
        <template #title>
          <span class="text-base font-bold">原始询盘信息</span>
        </template>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="询盘主题" :span="3">
            {{ clue.inquirySubject || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="公司名称">
            {{ clue.companyName || '待补充' }}
          </el-descriptions-item>
          <el-descriptions-item label="联系人">{{ clue.contactName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ clue.email || '-' }}</el-descriptions-item>
          <el-descriptions-item label="电话区号">
            {{ clue.countryCode || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="电话 / WhatsApp">
            {{ clue.telephone || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="浏览器语言">{{ clue.locale || '-' }}</el-descriptions-item>
          <el-descriptions-item label="具体需求" :span="3">
            <div class="inquiry-message">{{ clue.inquiryMessage || '客户未填写具体需求' }}</div>
          </el-descriptions-item>
          <el-descriptions-item label="提交页面" :span="2">
            {{ clue.sourcePage || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="提交时间">
            {{ formatOptionalDate(clue.submittedAt || clue.createTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="UTM 来源">{{ clue.utmSource || '-' }}</el-descriptions-item>
          <el-descriptions-item label="UTM 媒介">{{ clue.utmMedium || '-' }}</el-descriptions-item>
          <el-descriptions-item label="UTM 活动">{{ clue.utmCampaign || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-collapse-item>

      <el-collapse-item name="processing">
        <template #title>
          <span class="text-base font-bold">处理与转化信息</span>
        </template>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="处理状态">
            <el-tag :type="statusMeta.type">{{ statusMeta.label }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="处理人">{{ clue.ownerUserName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="处理完成时间">
            {{ formatOptionalDate(clue.processedAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="客户档案">
            {{ clue.customerName || '尚未生成' }}
          </el-descriptions-item>
          <el-descriptions-item label="联系人档案">
            {{ clue.contactId ? `#${clue.contactId}` : '尚未生成' }}
          </el-descriptions-item>
          <el-descriptions-item label="外部询盘编号">
            {{ clue.externalInquiryId || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="处理备注" :span="3">
            {{ clue.remark || '-' }}
          </el-descriptions-item>
        </el-descriptions>
      </el-collapse-item>
    </el-collapse>
  </ContentWrap>
</template>

<script lang="ts" setup>
import * as ClueApi from '@/api/crm/clue'
import { InquiryProcessStatus } from '@/api/crm/clue'
import { formatDate } from '@/utils/formatTime'

defineOptions({ name: 'CrmClueDetailsInfo' })
const props = defineProps<{
  clue: ClueApi.ClueVO
}>()
const activeNames = ref(['inquiry', 'processing'])
const formatOptionalDate = (value?: Date) => (value ? formatDate(value) : '-')

const statusMeta = computed(() => {
  const metadata = {
    [InquiryProcessStatus.PENDING]: { label: '待处理', type: 'warning' },
    [InquiryProcessStatus.PROCESSING]: { label: '处理中', type: 'primary' },
    [InquiryProcessStatus.PROCESSED]: { label: '已处理', type: 'success' },
    [InquiryProcessStatus.INVALID]: { label: '无效询盘', type: 'info' }
  } as const
  return metadata[props.clue.processStatus] || metadata[InquiryProcessStatus.PENDING]
})
</script>

<style scoped>
.inquiry-message {
  min-height: 96px;
  white-space: pre-wrap;
  line-height: 1.7;
}
</style>
