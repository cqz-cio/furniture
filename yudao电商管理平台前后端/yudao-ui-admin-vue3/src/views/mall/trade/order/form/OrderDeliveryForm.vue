<template>
  <Dialog v-model="dialogVisible" title="订单发货" width="25%">
    <el-form ref="formRef" v-loading="formLoading" :model="formData" label-width="80px">
      <el-form-item label="发货方式">
        <el-radio-group v-model="expressType">
          <el-radio border value="express">快递物流</el-radio>
          <el-radio border value="none">无需发货</el-radio>
        </el-radio-group>
      </el-form-item>
      <template v-if="expressType === 'express'">
        <el-alert
          v-if="addressVerificationDeliveryWarning"
          :title="addressVerificationDeliveryWarning"
          type="warning"
          show-icon
          :closable="false"
          class="mb-12px"
        />
        <el-form-item v-if="addressVerificationDeliveryWarning" label="地址确认">
          <el-checkbox v-model="addressVerificationDeliveryAcknowledged">
            发货前请人工复核收货地址，并确认可继续发货
          </el-checkbox>
        </el-form-item>
        <el-form-item label="物流公司">
          <el-select v-model="formData.logisticsId" placeholder="请选择" style="width: 100%">
            <el-option
              v-for="item in deliveryExpressList"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="物流单号">
          <el-input v-model="formData.logisticsNo" />
        </el-form-item>
      </template>
    </el-form>
    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import * as DeliveryExpressApi from '@/api/mall/trade/delivery/express'
import * as TradeOrderApi from '@/api/mall/trade/order'
import { copyValueToTarget } from '@/utils'

defineOptions({ name: 'OrderDeliveryForm' })

const { t } = useI18n() // 国际化
const message = useMessage() // 消息弹窗

const dialogVisible = ref(false) // 弹窗的是否展示
const formLoading = ref(false) // 表单的加载中：1）修改时的数据加载；2）提交的按钮禁用
const expressType = ref('express') // 如果值是 express，则是快递；none 则是无；未来做同城配送；
const formData = ref<TradeOrderApi.DeliveryVO>({
  id: undefined, // 订单编号
  logisticsId: null, // 物流公司编号
  logisticsNo: '', // 物流编号
  addressVerificationAcknowledged: false // 是否已人工复核地址核对风险
})
const formRef = ref() // 表单 Ref
const currentOrderAddressVerification = ref<TradeOrderApi.AddressVerificationAudit | null>(null)
const addressVerificationDeliveryAcknowledged = ref(false)
const addressVerificationDeliveryWarning = computed(() => {
  if (expressType.value !== 'express') {
    return ''
  }
  const addressVerification = currentOrderAddressVerification.value
  if (!addressVerification) {
    return '该快递订单暂无有效地址核对记录，发货前请人工复核收货地址'
  }
  if (addressVerification.providerStatus === 'fallback') {
    return '地址核对服务当时处于兜底状态，发货前请人工复核收货地址'
  }
  const { source, status } = addressVerification
  if (source === 'local-postal-region') {
    return '地址仅通过本地邮编/州匹配，发货前请人工复核收货地址'
  }
  if (source === 'backend-address-verification') {
    return '地址仅通过后台简化核对，发货前请人工复核收货地址'
  }
  if (status === 'unverified') {
    return '地址未通过有效核对，发货前请人工复核收货地址'
  }
  return ''
})

/** 打开弹窗 */
const open = async (row: TradeOrderApi.OrderVO) => {
  resetForm()
  // 设置数据
  copyValueToTarget(formData.value, row)
  currentOrderAddressVerification.value = row.addressVerification ?? null
  addressVerificationDeliveryAcknowledged.value = false
  if (row.logisticsId === 0) {
    expressType.value = 'none'
  }
  dialogVisible.value = true
}
defineExpose({ open }) // 提供 open 方法，用于打开弹窗

/** 提交表单 */
const emit = defineEmits(['success']) // 定义 success 事件，用于操作成功后的回调
const submitForm = async () => {
  if (addressVerificationDeliveryWarning.value && !addressVerificationDeliveryAcknowledged.value) {
    message.warning('请先确认已人工复核收货地址')
    return
  }
  // 提交请求
  formLoading.value = true
  try {
    const data = {
      ...unref(formData),
      addressVerificationAcknowledged: addressVerificationDeliveryAcknowledged.value
    }
    if (expressType.value === 'none') {
      // 无需发货的情况
      data.logisticsId = 0
      data.logisticsNo = ''
    }
    await TradeOrderApi.deliveryOrder(data)
    message.success(t('common.updateSuccess'))
    dialogVisible.value = false
    // 发送操作成功的事件
    emit('success', true)
  } finally {
    formLoading.value = false
  }
}

/** 重置表单 */
const resetForm = () => {
  formData.value = {
    id: undefined, // 订单编号
    logisticsId: null, // 物流公司编号
    logisticsNo: '', // 物流编号
    addressVerificationAcknowledged: false // 是否已人工复核地址核对风险
  }
  currentOrderAddressVerification.value = null
  addressVerificationDeliveryAcknowledged.value = false
  formRef.value?.resetFields()
}
const deliveryExpressList = ref([])
onMounted(async () => {
  deliveryExpressList.value = await DeliveryExpressApi.getSimpleDeliveryExpressList()
})
</script>
