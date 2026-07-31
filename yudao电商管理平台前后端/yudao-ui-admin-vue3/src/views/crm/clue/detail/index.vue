<template>
  <div v-loading="loading" class="clue-detail-page">
    <ErpPageState
      v-if="loadState === 'error'"
      description="未能读取这条询盘。记录可能已删除、当前账号无查看权限，或询盘服务暂时不可用。"
      eyebrow="询盘中心"
      primary-text="重试"
      secondary-text="返回询盘列表"
      title="询盘详情加载失败"
      type="error"
      @primary="getClue"
      @secondary="close"
    />
    <template v-else-if="loadState === 'ready'">
      <ClueDetailsHeader :clue="clue" :loading="loading">
        <el-button v-hasPermi="['crm:clue:update']" @click="openForm">编辑询盘</el-button>
        <el-button
          v-if="clue.processStatus === InquiryProcessStatus.PENDING"
          v-hasPermi="['crm:clue:update']"
          type="primary"
          @click="updateStatus(InquiryProcessStatus.PROCESSING)"
        >
          开始处理
        </el-button>
        <el-button
          v-if="!clue.transformStatus && clue.processStatus !== InquiryProcessStatus.INVALID"
          v-hasPermi="['crm:clue:update']"
          :type="clue.companyName ? 'success' : 'warning'"
          @click="clue.companyName ? transformInquiry() : openForm()"
        >
          {{ clue.companyName ? '生成客户档案' : '补充公司信息' }}
        </el-button>
        <el-button v-if="clue.transformStatus" disabled type="success">已生成客户档案</el-button>
        <el-dropdown
          v-if="showMoreActions"
          v-hasPermi="['crm:clue:update']"
          class="ml-12px"
          @command="updateStatus"
        >
          <el-button>更多处理<Icon icon="ep:arrow-down" /></el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item :command="InquiryProcessStatus.PROCESSED">
                标记已处理
              </el-dropdown-item>
              <el-dropdown-item :command="InquiryProcessStatus.INVALID" divided>
                标记无效
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </ClueDetailsHeader>

      <el-tabs>
        <el-tab-pane label="询盘信息">
          <ClueDetailsInfo :clue="clue" />
          <InquiryMailDeliveryPanel v-if="clueId && clue.externalInquiryId" :inquiry-id="clueId" />
        </el-tab-pane>
        <el-tab-pane label="操作日志">
          <div v-if="logLoadError" class="clue-log-error">
            <el-alert
              :closable="false"
              description="询盘主体数据不受影响，可以稍后单独重试日志。"
              show-icon
              title="操作日志暂时无法读取"
              type="warning"
            />
            <el-button class="mt-12px" size="small" @click="getOperateLogs">
              重新加载日志
            </el-button>
          </div>
          <OperateLogV2 v-else :log-list="logList" />
        </el-tab-pane>
      </el-tabs>

      <ClueForm ref="formRef" @success="getClue" />
    </template>
  </div>
</template>

<script lang="ts" setup>
import { useTagsViewStore } from '@/store/modules/tagsView'
import * as ClueApi from '@/api/crm/clue'
import { InquiryProcessStatus } from '@/api/crm/clue'
import ClueForm from '@/views/crm/clue/ClueForm.vue'
import ClueDetailsHeader from './ClueDetailsHeader.vue'
import ClueDetailsInfo from './ClueDetailsInfo.vue'
import InquiryMailDeliveryPanel from './InquiryMailDeliveryPanel.vue'
import type { OperateLogVO } from '@/api/system/operatelog'
import { getOperateLogPage } from '@/api/crm/operateLog'
import { BizTypeEnum } from '@/api/crm/permission'
import { ErpPageState } from '@/components/ErpPageState'

defineOptions({ name: 'CrmClueDetail' })

const clueId = ref(0)
const loading = ref(true)
const loadState = ref<'loading' | 'ready' | 'error'>('loading')
const logLoadError = ref(false)
const message = useMessage()
const { delView } = useTagsViewStore()
const { currentRoute, push } = useRouter()
const clue = ref<ClueApi.ClueVO>({} as ClueApi.ClueVO)
const logList = ref<OperateLogVO[]>([])
const showMoreActions = computed(
  () =>
    clue.value.processStatus !== InquiryProcessStatus.PROCESSED &&
    clue.value.processStatus !== InquiryProcessStatus.INVALID
)

const getOperateLogs = async () => {
  logLoadError.value = false
  try {
    const data = await getOperateLogPage({
      bizType: BizTypeEnum.CRM_CLUE,
      bizId: clueId.value
    })
    logList.value = data.list
  } catch {
    logList.value = []
    logLoadError.value = true
  }
}

const getClue = async () => {
  if (!clueId.value) {
    loadState.value = 'error'
    loading.value = false
    return
  }
  loading.value = true
  loadState.value = 'loading'
  try {
    const result = await ClueApi.getClue(clueId.value, { hideErrorMessage: true })
    if (!result?.id) {
      throw new Error('询盘记录不存在')
    }
    clue.value = result
    loadState.value = 'ready'
    await getOperateLogs()
  } catch {
    clue.value = {} as ClueApi.ClueVO
    logList.value = []
    loadState.value = 'error'
  } finally {
    loading.value = false
  }
}

const formRef = ref<InstanceType<typeof ClueForm>>()
const openForm = () => {
  formRef.value?.open('update', clueId.value)
}

const updateStatus = async (status: InquiryProcessStatus) => {
  if (status === InquiryProcessStatus.INVALID) {
    await message.confirm(`确定将“${clue.value.inquirySubject}”标记为无效询盘吗？`)
  }
  await ClueApi.updateInquiryProcessStatus({ id: clueId.value, processStatus: status })
  message.success('询盘处理状态已更新')
  await getClue()
}

const transformInquiry = async () => {
  await message.confirm(
    `确定生成客户档案“${clue.value.companyName}”和联系人“${clue.value.contactName}”吗？`
  )
  const result = await ClueApi.transformClue(clueId.value)
  const customerAction = result.customerCreated ? '创建客户档案' : '关联现有客户档案'
  const contactAction = result.contactCreated ? '创建联系人' : '关联现有联系人'
  message.success(`已${customerAction}并${contactAction}`)
  await getClue()
}

const close = () => {
  delView(unref(currentRoute))
  push('/crm/clue')
}

const { params } = useRoute()
onMounted(() => {
  if (!params.id) {
    message.warning('参数错误，询盘不能为空！')
    close()
    return
  }
  clueId.value = params.id as unknown as number
  getClue()
})
</script>

<style scoped>
.clue-detail-page {
  min-height: 260px;
}

.clue-log-error {
  padding: 12px 0;
}
</style>
