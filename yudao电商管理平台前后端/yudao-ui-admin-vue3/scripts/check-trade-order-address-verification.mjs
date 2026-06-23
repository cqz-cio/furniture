import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const readSource = (path) => readFileSync(resolve(root, path), 'utf8')

const api = readSource('src/api/mall/trade/order/index.ts')
const orderIndex = readSource('src/views/mall/trade/order/index.vue')
const detail = readSource('src/views/mall/trade/order/detail/index.vue')
const tableColumn = readSource('src/views/mall/trade/order/components/OrderTableColumn.vue')
const updateAddressForm = readSource('src/views/mall/trade/order/form/OrderUpdateAddressForm.vue')
const deliveryForm = readSource('src/views/mall/trade/order/form/OrderDeliveryForm.vue')
const backendRoot = resolve(root, '..', 'yudao-cloud')
const readBackendSource = (path) => readFileSync(resolve(backendRoot, path), 'utf8')
const backendPageReq = readBackendSource(
  'yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/admin/order/vo/TradeOrderPageReqVO.java'
)
const backendMapper = readBackendSource(
  'yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/dal/mysql/order/TradeOrderMapper.java'
)
const backendAddressController = readBackendSource(
  'yudao-module-member/yudao-module-member-server/src/main/java/cn/iocoder/yudao/module/member/controller/admin/address/AddressController.java'
)
const backendDeliveryReq = readBackendSource(
  'yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/admin/order/vo/TradeOrderDeliveryReqVO.java'
)
const backendOrderUpdateService = readBackendSource(
  'yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/order/TradeOrderUpdateServiceImpl.java'
)
const backendErrorCodes = readBackendSource(
  'yudao-module-mall/yudao-module-trade-api/src/main/java/cn/iocoder/yudao/module/trade/enums/ErrorCodeConstants.java'
)
const backendOrderOperateType = readBackendSource(
  'yudao-module-mall/yudao-module-trade-api/src/main/java/cn/iocoder/yudao/module/trade/enums/order/TradeOrderOperateTypeEnum.java'
)

assert.match(api, /export interface AddressVerificationAudit/)
assert.match(api, /addressVerification\?: AddressVerificationAudit \| null/)
assert.match(api, /providerResponseId\?: string/)
assert.match(api, /selectedAddress\?: AddressVerificationAddress \| null/)
assert.match(api, /export interface AddressVerificationProviderStatus/)
assert.match(api, /export interface AddressVerificationStatus/)
assert.match(api, /getAddressVerificationStatus/)
assert.match(api, /\/member\/address\/verification-status/)
assert.match(api, /addressVerificationAcknowledged\?: boolean/)

assert.match(detail, /addressVerification = computed/)
assert.match(detail, /addressVerificationAddressLine/)
assert.match(detail, /addressVerificationStatusType/)
assert.match(detail, /addressVerificationSourceLabel/)
assert.match(detail, /addressVerificationAddressSourceLabel/)
assert.match(detail, /addressVerificationChoiceLabel/)
assert.match(detail, /addressVerificationProviderStatusLabel/)
assert.match(detail, /addressVerificationLowTrustWarning/)
assert.match(detail, /addressVerificationMissingWarning/)
assert.match(detail, /title="地址核对记录"/)
assert.match(detail, /formData.addressVerification/)
assert.match(detail, /addressVerification\.selectedAddress/)
assert.match(detail, /addressVerification\.providerResponseId/)
assert.match(detail, /addressVerification\.providerStatus === 'fallback'/)
assert.match(detail, /source === 'local-postal-region'/)
assert.match(detail, /source === 'backend-address-verification'/)
assert.match(detail, /DeliveryTypeEnum\.EXPRESS\.type && !addressVerification/)
assert.match(detail, /该快递订单暂无有效地址核对记录/)
assert.match(detail, /发货前请人工确认收货地址/)

assert.match(tableColumn, /label="地址核对"/)
assert.match(tableColumn, /scope\.row\.addressVerification/)
assert.match(tableColumn, /orderAddressVerificationStatusLabel/)
assert.match(tableColumn, /orderAddressVerificationStatusType/)
assert.match(tableColumn, /orderAddressVerificationSourceLabel/)
assert.match(tableColumn, /orderAddressVerificationLowTrustWarning/)
assert.match(tableColumn, /addressVerification\?\.providerStatus === 'fallback'/)
assert.match(tableColumn, /source === 'local-postal-region'/)
assert.match(tableColumn, /source === 'backend-address-verification'/)
assert.match(tableColumn, /orderTableHeadWidthList\.value\[index\]/)

assert.match(updateAddressForm, /addressVerificationInvalidationWarning/)
assert.match(updateAddressForm, /修改收货地址后，原地址核对记录将失效/)
assert.match(updateAddressForm, /发货前请人工确认新地址/)

assert.match(deliveryForm, /addressVerificationDeliveryWarning/)
assert.match(deliveryForm, /addressVerificationDeliveryAcknowledged/)
assert.match(deliveryForm, /currentOrderAddressVerification/)
assert.match(deliveryForm, /providerStatus === 'fallback'/)
assert.match(deliveryForm, /source === 'local-postal-region'/)
assert.match(deliveryForm, /source === 'backend-address-verification'/)
assert.match(deliveryForm, /status === 'unverified'/)
assert.match(deliveryForm, /发货前请人工复核收货地址/)
assert.match(deliveryForm, /请先确认已人工复核收货地址/)
assert.match(deliveryForm, /addressVerificationAcknowledged: addressVerificationDeliveryAcknowledged\.value/)
assert.match(deliveryForm, /<el-alert/)
assert.match(deliveryForm, /<el-checkbox/)
assert.match(deliveryForm, /message\.warning/)

assert.match(orderIndex, /addressVerificationStatusOptions/)
assert.match(orderIndex, /value: 'missing', label: '未记录'/)
assert.match(orderIndex, /addressVerificationSourceOptions/)
assert.match(orderIndex, /queryParams\.addressVerificationStatus/)
assert.match(orderIndex, /queryParams\.addressVerificationSource/)
assert.match(orderIndex, /queryParams\.addressVerificationProviderStatus/)
assert.match(orderIndex, /addressVerificationStatus: undefined/)
assert.match(orderIndex, /addressVerificationSource: undefined/)
assert.match(orderIndex, /addressVerificationProviderStatus: undefined/)
assert.match(orderIndex, /addressVerificationProviderStatus = ref/)
assert.match(orderIndex, /addressVerificationServiceWarning/)
assert.match(orderIndex, /getAddressVerificationStatus/)
assert.match(orderIndex, /addressVerificationProviderStatus\.value\?\.fallbackActive/)
assert.match(orderIndex, /v-if="addressVerificationServiceWarning"/)
assert.match(orderIndex, /核对服务当前处于兜底/)

assert.match(backendPageReq, /private String addressVerificationStatus;/)
assert.match(backendPageReq, /private String addressVerificationSource;/)
assert.match(backendPageReq, /private String addressVerificationProviderStatus;/)
assert.match(backendMapper, /getAddressVerificationStatus/)
assert.match(backendMapper, /isMissingAddressVerificationStatus/)
assert.match(backendMapper, /isNull\(isMissingAddressVerificationStatus/)
assert.match(backendMapper, /TradeOrderDO::getAddressVerification/)
assert.match(backendMapper, /getAddressVerificationSource/)
assert.match(backendMapper, /getAddressVerificationProviderStatus/)
assert.match(backendMapper, /JSON_EXTRACT\(address_verification, '\$\.status'\)/)
assert.match(backendMapper, /JSON_EXTRACT\(address_verification, '\$\.source'\)/)
assert.match(backendMapper, /JSON_EXTRACT\(address_verification, '\$\.providerStatus'\)/)
assert.match(backendAddressController, /AddressVerificationService/)
assert.match(backendAddressController, /AppAddressVerificationStatusRespVO/)
assert.match(backendAddressController, /@GetMapping\("\/verification-status"\)/)
assert.match(backendAddressController, /getAddressVerificationStatus/)
assert.match(backendAddressController, /addressVerificationService\.getStatus\(\)/)

assert.match(backendDeliveryReq, /private Boolean addressVerificationAcknowledged;/)
assert.match(backendOrderUpdateService, /validateAddressVerificationDeliveryReview/)
assert.match(backendOrderUpdateService, /isAddressVerificationDeliveryReviewRequired/)
assert.match(backendOrderUpdateService, /getAddressVerificationAcknowledged/)
assert.match(backendOrderUpdateService, /ORDER_DELIVERY_FAIL_ADDRESS_VERIFICATION_NEEDS_REVIEW/)
assert.match(backendOrderUpdateService, /providerStatus/)
assert.match(backendOrderUpdateService, /local-postal-region/)
assert.match(backendOrderUpdateService, /backend-address-verification/)
assert.match(backendOrderUpdateService, /unverified/)
assert.match(backendErrorCodes, /ORDER_DELIVERY_FAIL_ADDRESS_VERIFICATION_NEEDS_REVIEW/)
assert.match(backendOrderUpdateService, /addressVerificationReviewNote/)
assert.match(backendOrderUpdateService, /地址风险已人工复核/)
assert.match(backendOrderOperateType, /addressVerificationReviewNote/)

console.log('trade order address verification admin checks passed')
