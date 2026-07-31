<template>
  <ContentWrap v-loading="loading">
    <ErpPageState
      v-if="loadState === 'error'"
      description="未能读取这条订单记录。订单可能已删除、当前账号无查看权限，或交易服务暂时不可用。"
      eyebrow="交易中心"
      primary-text="重试"
      secondary-text="返回订单列表"
      title="订单详情加载失败"
      type="error"
      @primary="loadOrderPage"
      @secondary="close"
    />
    <template v-else-if="loadState === 'ready'">
      <el-alert
        v-if="supplementaryError"
        class="mb-16px"
        :closable="false"
        description="订单主体数据可正常查看，但物流公司、轨迹或自提门店信息暂时无法读取。"
        show-icon
        title="部分物流信息暂不可用"
        type="warning"
      />
      <!-- 订单信息 -->
      <el-descriptions title="订单信息">
        <el-descriptions-item label="订单号: ">{{ formData.no }}</el-descriptions-item>
        <el-descriptions-item label="买家: ">{{ formData?.user?.nickname }}</el-descriptions-item>
        <el-descriptions-item label="订单类型: ">
          <dict-tag :type="DICT_TYPE.TRADE_ORDER_TYPE" :value="formData.type!" />
        </el-descriptions-item>
        <el-descriptions-item label="订单来源: ">
          <dict-tag :type="DICT_TYPE.TERMINAL" :value="formData.terminal!" />
        </el-descriptions-item>
        <el-descriptions-item label="买家留言: ">{{ formData.userRemark }}</el-descriptions-item>
        <el-descriptions-item label="商家备注: ">{{ formData.remark }}</el-descriptions-item>
        <el-descriptions-item label="支付单号: ">{{ formData.payOrderId }}</el-descriptions-item>
        <el-descriptions-item label="付款方式: ">
          <dict-tag :type="DICT_TYPE.PAY_CHANNEL_CODE" :value="formData.payChannelCode!" />
        </el-descriptions-item>
        <el-descriptions-item v-if="formData.brokerageUser" label="推广用户: ">
          {{ formData.brokerageUser?.nickname }}
        </el-descriptions-item>
      </el-descriptions>

      <!-- 订单状态 -->
      <el-descriptions :column="1" title="订单状态">
        <el-descriptions-item label="订单状态: ">
          <dict-tag :type="DICT_TYPE.TRADE_ORDER_STATUS" :value="formData.status!" />
        </el-descriptions-item>
        <el-descriptions-item v-hasPermi="['trade:order:update']" label-class-name="no-colon">
          <el-button
            v-if="formData.status! === TradeOrderStatusEnum.UNPAID.status"
            type="primary"
            @click="updatePrice"
          >
            调整价格
          </el-button>
          <el-button type="primary" @click="remark">备注</el-button>
          <!-- 待发货 -->
          <template v-if="formData.status! === TradeOrderStatusEnum.UNDELIVERED.status">
            <!-- 快递发货 -->
            <el-button
              v-if="formData.deliveryType === DeliveryTypeEnum.EXPRESS.type"
              type="primary"
              @click="delivery"
            >
              发货
            </el-button>
            <el-button
              v-if="formData.deliveryType === DeliveryTypeEnum.EXPRESS.type"
              type="primary"
              @click="updateAddress"
            >
              修改地址
            </el-button>
            <!-- 到店自提 -->
            <el-button
              v-if="formData.deliveryType === DeliveryTypeEnum.PICK_UP.type && showPickUp"
              type="primary"
              @click="handlePickUp"
            >
              核销
            </el-button>
          </template>
        </el-descriptions-item>
        <el-descriptions-item>
          <template #label><span style="color: red">提醒: </span></template>
          买家付款成功后，货款将直接进入您的商户号（微信、支付宝）<br />
          请及时关注你发出的包裹状态，确保可以配送至买家手中 <br />
          如果买家表示没收到货或货物有问题，请及时联系买家处理，友好协商
        </el-descriptions-item>
      </el-descriptions>

      <!-- 商品信息 -->
      <el-descriptions title="商品信息">
        <el-descriptions-item labelClassName="no-colon">
          <el-row :gutter="20">
            <el-col :span="15">
              <el-table :data="formData.items" border>
                <el-table-column label="商品" prop="spuName" width="auto">
                  <template #default="{ row }">
                    {{ row.spuName }}
                    <el-tag v-for="property in row.properties" :key="property.propertyId">
                      {{ property.propertyName }}: {{ property.valueName }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="商品原价" prop="price" width="150">
                  <template #default="{ row }">{{ fenToYuan(row.price) }}元</template>
                </el-table-column>
                <el-table-column label="数量" prop="count" width="100" />
                <el-table-column label="合计" prop="payPrice" width="150">
                  <template #default="{ row }">{{ fenToYuan(row.payPrice) }}元</template>
                </el-table-column>
                <el-table-column label="售后状态" prop="afterSaleStatus" width="120">
                  <template #default="{ row }">
                    <dict-tag
                      :type="DICT_TYPE.TRADE_ORDER_ITEM_AFTER_SALE_STATUS"
                      :value="row.afterSaleStatus"
                    />
                  </template>
                </el-table-column>
              </el-table>
            </el-col>
            <el-col :span="10" />
          </el-row>
        </el-descriptions-item>
      </el-descriptions>
      <el-descriptions :column="4">
        <!-- 第一层 -->
        <el-descriptions-item label="商品总额: ">
          {{ fenToYuan(formData.totalPrice!) }} 元
        </el-descriptions-item>
        <el-descriptions-item label="运费金额: ">
          {{ fenToYuan(formData.deliveryPrice!) }} 元
        </el-descriptions-item>
        <el-descriptions-item label="订单调价: ">
          {{ fenToYuan(formData.adjustPrice!) }} 元
        </el-descriptions-item>
        <el-descriptions-item v-for="item in 1" :key="item" label-class-name="no-colon" />
        <!-- 第二层 -->
        <el-descriptions-item>
          <template #label><span style="color: red">优惠劵优惠: </span></template>
          {{ fenToYuan(formData.couponPrice!) }} 元
        </el-descriptions-item>
        <el-descriptions-item>
          <template #label><span style="color: red">VIP 优惠: </span></template>
          {{ fenToYuan(formData.vipPrice!) }} 元
        </el-descriptions-item>
        <el-descriptions-item>
          <template #label><span style="color: red">活动优惠: </span></template>
          {{ fenToYuan(formData.discountPrice!) }} 元
        </el-descriptions-item>
        <el-descriptions-item>
          <template #label><span style="color: red">积分抵扣: </span></template>
          {{ fenToYuan(formData.pointPrice!) }} 元
        </el-descriptions-item>
        <!-- 第三层 -->
        <el-descriptions-item v-for="item in 3" :key="item" label-class-name="no-colon" />
        <el-descriptions-item label="应付金额: ">
          {{ fenToYuan(formData.payPrice!) }} 元
        </el-descriptions-item>
      </el-descriptions>

      <!-- 物流信息 -->
      <el-descriptions :column="4" title="收货信息">
        <el-descriptions-item label="配送方式: ">
          <dict-tag :type="DICT_TYPE.TRADE_DELIVERY_TYPE" :value="formData.deliveryType!" />
        </el-descriptions-item>
        <el-descriptions-item label="收货人: ">{{ formData.receiverName }}</el-descriptions-item>
        <el-descriptions-item label="联系电话: ">{{
          formData.receiverMobile
        }}</el-descriptions-item>
        <!-- 快递配送 -->
        <div v-if="formData.deliveryType === DeliveryTypeEnum.EXPRESS.type">
          <el-descriptions-item v-if="formData.receiverDetailAddress" label="收货地址: ">
            {{ formData.receiverAreaName }} {{ formData.receiverDetailAddress }}
            <el-link
              aria-label="复制收货地址"
              icon="ep:document-copy"
              type="primary"
              @click="copyReceiverAddress"
            />
          </el-descriptions-item>
          <el-descriptions-item v-if="formData.logisticsId" label="物流公司: ">
            {{ deliveryExpressList.find((item) => item.id === formData.logisticsId)?.name }}
          </el-descriptions-item>
          <el-descriptions-item v-if="formData.logisticsId" label="运单号: ">
            {{ formData.logisticsNo }}
          </el-descriptions-item>
          <el-descriptions-item v-if="formData.deliveryTime" label="发货时间: ">
            {{ formatDate(formData.deliveryTime) }}
          </el-descriptions-item>
          <el-descriptions-item v-for="item in 2" :key="item" label-class-name="no-colon" />
          <el-descriptions-item v-if="expressTrackList.length > 0" label="物流详情: ">
            <el-timeline>
              <el-timeline-item
                v-for="(express, index) in expressTrackList"
                :key="index"
                :timestamp="formatDate(express.time)"
              >
                {{ express.content }}
              </el-timeline-item>
            </el-timeline>
          </el-descriptions-item>
        </div>
        <!-- 自提门店 -->
        <div v-if="formData.deliveryType === DeliveryTypeEnum.PICK_UP.type">
          <el-descriptions-item v-if="formData.pickUpStoreId" label="自提门店: ">
            {{ pickUpStore?.name }}
          </el-descriptions-item>
        </div>
      </el-descriptions>

      <el-descriptions v-if="formData.addressVerification" :column="2" title="地址核对记录">
        <el-descriptions-item label="核对来源: ">
          {{ addressVerificationSourceLabel }}
        </el-descriptions-item>
        <el-descriptions-item label="地址来源: ">
          {{ addressVerificationAddressSourceLabel }}
        </el-descriptions-item>
        <el-descriptions-item label="核对状态: ">
          <el-tag :type="addressVerificationStatusType">
            {{ addressVerificationStatusLabel }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="用户选择: ">
          {{ addressVerificationChoiceLabel }}
        </el-descriptions-item>
        <el-descriptions-item v-if="addressVerification?.reason" label="核对原因: ">
          {{ addressVerification.reason }}
        </el-descriptions-item>
        <el-descriptions-item v-if="addressVerification?.confirmedAt" label="确认时间: ">
          {{ addressVerification.confirmedAt }}
        </el-descriptions-item>
        <el-descriptions-item v-if="addressVerification?.providerResponseId" label="服务响应编号: ">
          {{ addressVerification.providerResponseId }}
        </el-descriptions-item>
        <el-descriptions-item v-if="addressVerification?.providerStatus" label="服务状态: ">
          {{ addressVerificationProviderStatusLabel }}
        </el-descriptions-item>
        <el-descriptions-item v-if="addressVerification?.originalAddress" label="用户填写地址: ">
          {{ addressVerificationAddressLine(addressVerification.originalAddress) }}
        </el-descriptions-item>
        <el-descriptions-item v-if="addressVerification?.suggestedAddress" label="系统建议地址: ">
          {{ addressVerificationAddressLine(addressVerification.suggestedAddress) }}
        </el-descriptions-item>
        <el-descriptions-item v-if="addressVerification?.selectedAddress" label="最终确认地址: ">
          {{ addressVerificationAddressLine(addressVerification.selectedAddress) }}
        </el-descriptions-item>
        <el-descriptions-item
          v-if="addressVerification && addressVerification.providerStatus === 'fallback'"
          label-class-name="no-colon"
        >
          <el-alert
            :closable="false"
            show-icon
            title="远程地址核对服务不可用，本订单仅记录本地 ZIP 核对结果，请发货前再次人工确认。"
            type="warning"
          />
        </el-descriptions-item>
        <el-descriptions-item v-if="addressVerificationLowTrustWarning" label-class-name="no-colon">
          <el-alert
            :closable="false"
            show-icon
            :title="addressVerificationLowTrustWarning"
            type="warning"
          />
        </el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-if="addressVerificationMissingWarning"
        class="mb-16px"
        :closable="false"
        show-icon
        :title="addressVerificationMissingWarning"
        type="warning"
      />

      <!-- 订单日志 -->
      <el-descriptions title="订单操作日志">
        <el-descriptions-item labelClassName="no-colon">
          <el-timeline>
            <el-timeline-item
              v-for="(log, index) in formData.logs"
              :key="index"
              :timestamp="formatDate(log.createTime!)"
              placement="top"
            >
              <div class="el-timeline-right-content">
                {{ log.content }}
              </div>
              <template #dot>
                <span
                  :style="{ backgroundColor: getUserTypeColor(log.userType!) }"
                  class="dot-node-style"
                >
                  {{ getDictLabel(DICT_TYPE.USER_TYPE, log.userType)[0] }}
                </span>
              </template>
            </el-timeline-item>
          </el-timeline>
        </el-descriptions-item>
      </el-descriptions>
    </template>
  </ContentWrap>

  <!-- 各种操作的弹窗 -->
  <OrderDeliveryForm ref="deliveryFormRef" @success="getDetail" />
  <OrderUpdateRemarkForm ref="updateRemarkForm" @success="getDetail" />
  <OrderUpdateAddressForm ref="updateAddressFormRef" @success="getDetail" />
  <OrderUpdatePriceForm ref="updatePriceFormRef" @success="getDetail" />
</template>
<script lang="ts" setup>
import * as TradeOrderApi from '@/api/mall/trade/order'
import { fenToYuan } from '@/utils'
import { formatDate } from '@/utils/formatTime'
import { DICT_TYPE, getDictLabel, getDictObj } from '@/utils/dict'
import OrderUpdateRemarkForm from '@/views/mall/trade/order/form/OrderUpdateRemarkForm.vue'
import OrderDeliveryForm from '@/views/mall/trade/order/form/OrderDeliveryForm.vue'
import OrderUpdateAddressForm from '@/views/mall/trade/order/form/OrderUpdateAddressForm.vue'
import OrderUpdatePriceForm from '@/views/mall/trade/order/form/OrderUpdatePriceForm.vue'
import * as DeliveryExpressApi from '@/api/mall/trade/delivery/express'
import { useTagsViewStore } from '@/store/modules/tagsView'
import { DeliveryTypeEnum, TradeOrderStatusEnum } from '@/utils/constants'
import * as DeliveryPickUpStoreApi from '@/api/mall/trade/delivery/pickUpStore'
import { propTypes } from '@/utils/propTypes'
import { ErpPageState } from '@/components/ErpPageState'

defineOptions({ name: 'TradeOrderDetail' })

const message = useMessage() // 消息弹窗
const loading = ref(true)
const loadState = ref<'loading' | 'ready' | 'error'>('loading')
const supplementaryError = ref(false)

/** 获得 userType 颜色 */
const getUserTypeColor = (type: number) => {
  const dict = getDictObj(DICT_TYPE.USER_TYPE, type)
  switch (dict?.colorType) {
    case 'success':
      return '#67C23A'
    case 'info':
      return '#909399'
    case 'warning':
      return '#E6A23C'
    case 'danger':
      return '#F56C6C'
  }
  return '#409EFF'
}

// 订单详情
const formData = ref<TradeOrderApi.OrderVO>({
  logs: []
})
const addressVerification = computed(() => formData.value.addressVerification)
const labelFromMap = (value: unknown, labels: Record<string, string>) => {
  const key = String(value || '').trim()
  return key ? labels[key] || key : '-'
}
const addressVerificationAddressLine = (
  address?: TradeOrderApi.AddressVerificationAddress | null
) => {
  if (!address) {
    return '-'
  }
  return [
    address.street,
    address.apartment,
    address.city,
    [address.state, address.postalCode].filter(Boolean).join(' ')
  ]
    .map((part) => String(part || '').trim())
    .filter(Boolean)
    .join(', ')
}
const addressVerificationSourceLabel = computed(() =>
  labelFromMap(addressVerification.value?.source, {
    'google-address-validation': 'Google 地址验证',
    'remote-address-verification': '远程地址服务',
    'backend-address-verification': '后端兜底核对',
    'local-postal-region': '本地邮编核对'
  })
)
const addressVerificationAddressSourceLabel = computed(() =>
  labelFromMap(addressVerification.value?.addressSource, {
    manual: '用户新填地址',
    saved: '用户保存地址'
  })
)
const addressVerificationStatusLabel = computed(() =>
  labelFromMap(addressVerification.value?.status, {
    verified: '已核对',
    suggested: '有建议',
    unverified: '需复核'
  })
)
const addressVerificationChoiceLabel = computed(() =>
  labelFromMap(addressVerification.value?.choice, {
    original: '使用用户填写地址',
    suggested: '使用系统建议地址'
  })
)
const addressVerificationProviderStatusLabel = computed(() =>
  labelFromMap(addressVerification.value?.providerStatus, {
    fallback: '服务降级'
  })
)
const addressVerificationStatusType = computed(() => {
  switch (addressVerification.value?.status) {
    case 'verified':
      return 'success'
    case 'suggested':
      return 'warning'
    case 'unverified':
      return 'danger'
    default:
      return 'info'
  }
})
const addressVerificationLowTrustWarning = computed(() => {
  if (addressVerification.value?.source === 'local-postal-region') {
    return '本订单仅完成 ZIP、城市、州的本地匹配，不代表街道地址可投递，发货前请人工确认。'
  }
  if (addressVerification.value?.source === 'backend-address-verification') {
    return '本订单仅经过后端兜底标准化，不代表街道地址可投递，发货前请人工确认。'
  }
  return ''
})
const addressVerificationMissingWarning = computed(() => {
  if (formData.value.deliveryType === DeliveryTypeEnum.EXPRESS.type && !addressVerification.value) {
    return '该快递订单暂无有效地址核对记录；发货前请人工确认收货地址。'
  }
  return ''
})

/** 各种操作 */
const updateRemarkForm = ref() // 订单备注表单 Ref
const remark = () => {
  updateRemarkForm.value?.open(formData.value)
}
const deliveryFormRef = ref() // 发货表单 Ref
const delivery = () => {
  deliveryFormRef.value?.open(formData.value)
}
const updateAddressFormRef = ref() // 收货地址表单 Ref
const updateAddress = () => {
  updateAddressFormRef.value?.open(formData.value)
}
const updatePriceFormRef = ref() // 订单调价表单 Ref
const updatePrice = () => {
  updatePriceFormRef.value?.open(formData.value)
}

/** 核销 */
const handlePickUp = async () => {
  try {
    // 二次确认
    await message.confirm('确认核销订单吗？')
    // 提交
    await TradeOrderApi.pickUpOrder(formData.value.id!)
    message.success('核销成功')
    // 刷新列表
    await getDetail()
  } catch {}
}

/** 获得详情 */
const { params } = useRoute() // 查询参数
const props = defineProps({
  id: propTypes.number.def(undefined), // 订单ID
  showPickUp: propTypes.bool.def(true) // 显示核销按钮
})
const id = (params.id || props.id) as unknown as number
const getDetail = async () => {
  if (!id) {
    loadState.value = 'error'
    return false
  }
  loading.value = true
  loadState.value = 'loading'
  try {
    const res = (await TradeOrderApi.getOrder(id, {
      hideErrorMessage: true
    })) as TradeOrderApi.OrderVO
    if (!res?.id) {
      throw new Error('交易订单不存在')
    }
    formData.value = res
    loadState.value = 'ready'
    return true
  } catch {
    formData.value = { logs: [] }
    loadState.value = 'error'
    return false
  } finally {
    loading.value = false
  }
}

/** 关闭 tag */
const { delView } = useTagsViewStore() // 视图操作
const { push, currentRoute } = useRouter() // 路由
const close = () => {
  delView(unref(currentRoute))
  push({ name: 'TradeOrder' })
}

/** 复制收货地址 */
const copyReceiverAddress = async () => {
  const address = [formData.value.receiverAreaName, formData.value.receiverDetailAddress]
    .filter(Boolean)
    .join(' ')
  if (!address) {
    return
  }
  try {
    await navigator.clipboard.writeText(address)
    message.success('收货地址已复制')
  } catch {
    message.error('复制失败，请手动选择地址文本')
  }
}

/** 初始化 **/
interface ExpressTrack {
  time: string | Date
  content: string
}

const deliveryExpressList = ref<DeliveryExpressApi.DeliveryExpressVO[]>([]) // 物流公司
const expressTrackList = ref<ExpressTrack[]>([]) // 物流详情
const pickUpStore = ref<DeliveryPickUpStoreApi.DeliveryPickUpStoreVO | null>(null) // 自提门店
const loadOrderPage = async () => {
  supplementaryError.value = false
  deliveryExpressList.value = []
  expressTrackList.value = []
  pickUpStore.value = null
  if (!(await getDetail())) {
    return
  }
  try {
    // 如果配送方式为快递，则查询物流公司
    if (formData.value.deliveryType === DeliveryTypeEnum.EXPRESS.type) {
      deliveryExpressList.value = await DeliveryExpressApi.getSimpleDeliveryExpressList()
      if (formData.value.logisticsId) {
        expressTrackList.value = await TradeOrderApi.getExpressTrackList(formData.value.id!)
      }
    } else if (
      formData.value.deliveryType === DeliveryTypeEnum.PICK_UP.type &&
      formData.value.pickUpStoreId
    ) {
      pickUpStore.value = await DeliveryPickUpStoreApi.getDeliveryPickUpStore(
        formData.value.pickUpStoreId
      )
    }
  } catch {
    supplementaryError.value = true
  }
}

onMounted(loadOrderPage)
</script>
<style lang="scss" scoped>
:deep(.el-descriptions) {
  &:not(:nth-child(1)) {
    margin-top: 20px;
  }

  .el-descriptions__title {
    display: flex;
    align-items: center;

    &::before {
      display: inline-block;
      width: 3px;
      height: 20px;
      margin-right: 10px;
      background-color: #409eff;
      content: '';
    }
  }

  .el-descriptions-item__container {
    margin: 0 10px;

    .no-colon {
      margin: 0;

      &::after {
        content: '';
      }
    }
  }
}

// 时间线样式调整
:deep(.el-timeline) {
  margin: 10px 0 0 160px;

  .el-timeline-item__wrapper {
    position: relative;
    top: -20px;

    .el-timeline-item__timestamp {
      position: absolute !important;
      top: 10px;
      left: -150px;
    }
  }

  .el-timeline-right-content {
    display: flex;
    min-height: 30px;
    padding: 10px;
    background-color: var(--app-content-bg-color);
    border-radius: var(--el-card-border-radius);
    align-items: center;

    &::before {
      position: absolute;
      top: 10px;
      left: 13px; /* 将伪元素水平居中 */
      border-color: transparent var(--app-content-bg-color) transparent transparent; /* 尖角颜色，左侧朝向 */
      border-style: solid;
      border-width: 8px; /* 调整尖角大小 */
      content: ''; /* 必须设置 content 属性 */
    }
  }

  .dot-node-style {
    position: absolute;
    left: -5px;
    display: flex;
    width: 20px;
    height: 20px;
    font-size: 10px;
    color: #fff;
    border-radius: 50%;
    justify-content: center;
    align-items: center;
  }
}
</style>
