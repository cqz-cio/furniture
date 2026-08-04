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
          <el-descriptions-item label="邮箱">
            <el-link v-if="clue.email" :href="`mailto:${clue.email}`" type="primary">
              {{ clue.email }}
            </el-link>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="电话区号">
            {{ clue.countryCode || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="电话 / WhatsApp">
            {{ clue.telephone || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="浏览器语言">{{ clue.locale || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="!quoteInquiry" label="具体需求" :span="3">
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
          <el-descriptions-item label="UTM 活动">{{
            clue.utmCampaign || '-'
          }}</el-descriptions-item>
        </el-descriptions>

        <section v-if="quoteInquiry" class="quote-inquiry">
          <div class="quote-inquiry__head">
            <div>
              <strong>报价商品清单</strong>
              <p>已从官网 Quote List 自动整理；询盘原文仍完整保留。</p>
            </div>
            <el-tag type="primary" effect="plain">{{ quoteInquiry.items.length }} 个商品</el-tag>
          </div>
          <el-table :data="quoteInquiry.items" border show-overflow-tooltip>
            <el-table-column type="index" label="#" width="54" />
            <el-table-column label="商品" prop="name" min-width="210" />
            <el-table-column label="商品编码" min-width="145">
              <template #default="{ row }">
                {{ row.productReference || row.productId || '-' }}
              </template>
            </el-table-column>
            <el-table-column label="SKU" prop="skuId" min-width="100">
              <template #default="{ row }">{{ row.skuId || '-' }}</template>
            </el-table-column>
            <el-table-column label="数量" prop="quantity" width="90" align="center" />
            <el-table-column label="规格选择" prop="selections" min-width="180">
              <template #default="{ row }">{{ row.selections || '-' }}</template>
            </el-table-column>
            <el-table-column label="单品备注" prop="note" min-width="260">
              <template #default="{ row }">{{ row.note || '-' }}</template>
            </el-table-column>
          </el-table>

          <el-descriptions
            v-if="hasProjectDetails"
            class="quote-inquiry__project"
            :column="4"
            border
          >
            <el-descriptions-item label="项目名称">
              {{ quoteInquiry.projectName || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="国家 / 地区">
              {{ quoteInquiry.country || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="采购时间">
              {{ quoteInquiry.buyingTimeframe || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="补充要求">
              {{ quoteInquiry.additionalRequirements || '-' }}
            </el-descriptions-item>
          </el-descriptions>

          <el-collapse class="quote-inquiry__raw">
            <el-collapse-item title="查看完整询盘原文" name="raw">
              <div class="inquiry-message">{{ clue.inquiryMessage }}</div>
            </el-collapse-item>
          </el-collapse>
        </section>
      </el-collapse-item>

      <el-collapse-item name="processing">
        <template #title>
          <span class="text-base font-bold">处理与转化信息</span>
        </template>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="处理状态">
            <el-tag :type="statusMeta.type">{{ statusMeta.label }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="处理人">{{
            clue.ownerUserName || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="处理完成时间">
            {{ formatOptionalDate(clue.processedAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="优先级">
            <el-tag :type="priority.type" effect="plain">{{ priority.label }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="销售阶段">
            {{ salesStageLabel(clue.salesStage) }}
          </el-descriptions-item>
          <el-descriptions-item label="首次响应">
            <el-tag :type="sla.type" effect="plain">{{ sla.label }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="下次跟进">
            {{ formatOptionalDate(clue.contactNextTime) }}
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
import {
  inquirySlaMeta,
  parseQuoteInquiry,
  priorityMeta,
  salesStageLabel
} from '../inquiryOperations'

defineOptions({ name: 'CrmClueDetailsInfo' })
const props = defineProps<{
  clue: ClueApi.ClueVO
}>()
const activeNames = ref(['inquiry', 'processing'])
const formatOptionalDate = (value?: Date) => (value ? formatDate(value) : '-')
const quoteInquiry = computed(() => parseQuoteInquiry(props.clue.inquiryMessage))
const hasProjectDetails = computed(
  () =>
    Boolean(quoteInquiry.value?.projectName) ||
    Boolean(quoteInquiry.value?.country) ||
    Boolean(quoteInquiry.value?.buyingTimeframe) ||
    Boolean(quoteInquiry.value?.additionalRequirements)
)
const priority = computed(() => priorityMeta(props.clue.priority))
const sla = computed(() => inquirySlaMeta(props.clue))

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

.quote-inquiry {
  display: grid;
  gap: 14px;
  margin-top: 16px;
}

.quote-inquiry__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.quote-inquiry__head strong {
  color: var(--furniture-admin-ink);
  font-size: 15px;
}

.quote-inquiry__head p {
  margin: 4px 0 0;
  color: var(--furniture-admin-muted);
  font-size: 12px;
}

.quote-inquiry__project {
  margin-top: 2px;
}

.quote-inquiry__raw :deep(.el-collapse-item__content) {
  padding: 0 14px 14px;
}
</style>
