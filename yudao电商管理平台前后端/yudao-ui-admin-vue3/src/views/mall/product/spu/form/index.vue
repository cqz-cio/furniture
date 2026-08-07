<template>
  <ContentWrap v-loading="pageLoading">
    <ErpPageState
      v-if="profileError"
      description="当前租户的业务模式和字段配置暂时无法读取。为避免错误保存字段，页面已停止加载表单。"
      primary-text="重新加载"
      secondary-text="返回商品列表"
      title="业务配置加载失败"
      type="error"
      @primary="initializePage"
      @secondary="close"
    />
    <ErpPageState
      v-else-if="profileLoaded && recordLoadState === 'error'"
      :description="recordLoadDescription"
      :title="isDetail ? '商品详情加载失败' : '商品信息加载失败'"
      eyebrow="商品中心"
      primary-text="重试"
      secondary-text="返回商品列表"
      type="error"
      @primary="getDetail"
      @secondary="close"
    />
    <template v-else-if="profileLoaded && recordLoadState === 'ready'">
      <el-alert
        v-if="isDetail"
        :closable="false"
        class="mb-16px"
        description="详情页用于核对数据，所有字段均为只读。如需调整商品信息，请返回列表并点击“修改”。"
        show-icon
        title="当前为商品详情模式"
        type="warning"
      />
      <div v-if="isB2B" class="b2b-mode-panel">
        <div>
          <div class="mb-8px text-15px font-600">B2B 商品字段视图</div>
          <div class="text-13px text-[var(--el-text-color-secondary)]">
            默认只展示 B2B 网站字段和必要的 ERP 内部字段，B2C 专用字段不会参与 B2B
            商品校验或网站展示。
          </div>
          <div class="mt-12px flex flex-wrap items-center gap-8px">
            <span class="text-13px">字段标识：</span>
            <el-tag effect="plain" size="small" type="success">网站公开</el-tag>
            <el-tag effect="plain" size="small" type="info">ERP 内部</el-tag>
            <el-tag effect="plain" size="small" type="warning">B2C 专用</el-tag>
          </div>
        </div>
        <el-switch
          v-model="showB2CFields"
          active-text="显示 B2C 专用字段"
          inactive-text="隐藏 B2C 专用字段"
        />
      </div>
      <el-tabs v-model="activeName">
        <el-tab-pane label="基础设置" name="info">
          <InfoForm
            ref="infoRef"
            v-model:activeName="activeName"
            :business-mode="businessMode"
            :field-states="productFieldStates"
            :is-detail="isDetail"
            :propFormData="formData"
          />
        </el-tab-pane>
        <el-tab-pane
          :label="isB2B ? '规格与 ERP 内部信息' : inventoryEnabled ? '价格库存' : '价格与规格'"
          name="sku"
        >
          <SkuForm
            ref="skuRef"
            v-model:activeName="activeName"
            :business-mode="businessMode"
            :field-states="productFieldStates"
            :is-detail="isDetail"
            :propFormData="formData"
            :show-inactive-fields="showB2CFields"
            :show-stock="inventoryEnabled"
          />
        </el-tab-pane>
        <el-tab-pane
          v-if="!isB2B || showB2CFields"
          :label="isB2B ? '物流设置（B2C 专用）' : '物流设置'"
          name="delivery"
        >
          <DeliveryForm
            ref="deliveryRef"
            v-model:activeName="activeName"
            :is-detail="isDetail || isB2B"
            :propFormData="formData"
          />
        </el-tab-pane>
        <el-tab-pane :label="isB2B ? '详情页描述' : '商品详情'" name="description">
          <DescriptionForm
            ref="descriptionRef"
            v-model:activeName="activeName"
            :business-mode="businessMode"
            :is-detail="isDetail"
            :propFormData="formData"
          />
        </el-tab-pane>
        <el-tab-pane :label="isB2B ? 'B2B 网站内容' : '家具详情配置'" name="furnitureDetail">
          <FurnitureDetailForm
            ref="furnitureDetailRef"
            v-model:activeName="activeName"
            :business-mode="businessMode"
            :is-detail="isDetail"
            :propFormData="formData"
          />
        </el-tab-pane>
        <el-tab-pane :label="isB2B ? '展示设置' : '其它设置'" name="other">
          <OtherForm
            ref="otherRef"
            v-model:activeName="activeName"
            :business-mode="businessMode"
            :field-states="productFieldStates"
            :is-detail="isDetail"
            :propFormData="formData"
            :show-inactive-fields="showB2CFields"
          />
        </el-tab-pane>
      </el-tabs>
      <el-form>
        <el-form-item style="float: right">
          <el-button
            v-if="!isDetail"
            v-hasPermi="savePermissions"
            :loading="formLoading"
            type="primary"
            @click="submitForm"
          >
            保存
          </el-button>
          <el-button @click="close">返回</el-button>
        </el-form-item>
      </el-form>
    </template>
  </ContentWrap>
</template>
<script lang="ts" setup>
import { cloneDeep } from 'lodash-es'
import { useTagsViewStore } from '@/store/modules/tagsView'
import * as ProductSpuApi from '@/api/mall/product/spu'
import InfoForm from './InfoForm.vue'
import DescriptionForm from './DescriptionForm.vue'
import OtherForm from './OtherForm.vue'
import SkuForm from './SkuForm.vue'
import DeliveryForm from './DeliveryForm.vue'
import FurnitureDetailForm from './FurnitureDetailForm.vue'
import { convertToInteger, floatToFixed2, formatToFraction } from '@/utils'
import { isEmpty } from '@/utils/is'
import { useTenantBusinessProfile } from '@/hooks/web/useTenantBusinessProfile'
import { ErpPageState } from '@/components/ErpPageState'

defineOptions({ name: 'ProductSpuAdd' })

const { t } = useI18n() // 国际化
const message = useMessage() // 消息弹窗
const { push, currentRoute } = useRouter() // 路由
const { params, name } = useRoute() // 查询参数
const { delView } = useTagsViewStore() // 视图操作

const formLoading = ref(false) // 表单的加载中：1）修改时的数据加载；2）提交的按钮禁用
const {
  profileLoading,
  profileLoaded,
  profileError,
  businessMode,
  isB2B,
  inventoryEnabled,
  productFieldStates,
  loadTenantBusinessProfile
} = useTenantBusinessProfile()
const pageLoading = computed(() => formLoading.value || profileLoading.value)
const activeName = ref('info') // Tag 激活的窗口
const isDetail = ref(false) // 是否查看详情
const savePermissions = computed(() =>
  params.id ? ['product:spu:update'] : ['product:spu:create']
)
const recordLoadState = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')
const recordLoadDescription = ref('')
const showB2CFields = ref(false) // B2B 模式下仅用于查看历史 B2C 专用字段
const infoRef = ref() // 商品信息 Ref
const skuRef = ref() // 商品规格 Ref
const deliveryRef = ref() // 物流设置 Ref
const descriptionRef = ref() // 商品详情 Ref
const furnitureDetailRef = ref() // furniture detail configuration Ref
const otherRef = ref() // 其他设置 Ref
// SPU 表单数据
const formData = ref<ProductSpuApi.Spu>({
  name: '', // 商品名称
  categoryId: undefined, // 商品分类
  keyword: '', // 关键字
  picUrl: '', // 商品封面图
  sliderPicUrls: [], // 商品轮播图
  introduction: '', // 商品简介
  deliveryTypes: [], // 配送方式数组
  deliveryTemplateId: undefined, // 运费模版
  brandId: undefined, // 商品品牌
  specType: false, // 商品规格
  subCommissionType: false, // 分销类型
  skus: [
    {
      name: '', // SKU 名称，提交时会自动使用 SPU 名称
      price: 0, // 商品价格
      marketPrice: 0, // 市场价
      costPrice: 0, // 成本价
      barCode: '', // 商品条码
      picUrl: '', // 图片地址
      stock: 0, // 库存
      weight: 0, // 商品重量
      volume: 0, // 商品体积
      firstBrokeragePrice: 0, // 一级分销的佣金
      secondBrokeragePrice: 0 // 二级分销的佣金
    }
  ],
  description: '', // 商品详情
  sort: 0, // 商品排序
  giveIntegral: 0, // 赠送积分
  virtualSalesCount: 0 // 虚拟销量
})

/** 获得详情 */
const getDetail = async () => {
  if ('ProductSpuDetail' === name) {
    isDetail.value = true
  }
  const id = params.id as unknown as number
  if (!id) {
    recordLoadState.value = 'ready'
    return
  }
  formLoading.value = true
  recordLoadState.value = 'loading'
  recordLoadDescription.value = ''
  try {
    const res = (await ProductSpuApi.getSpu(id, {
      hideErrorMessage: true
    })) as ProductSpuApi.Spu | null
    if (!res?.id) {
      throw new Error('商品记录不存在')
    }
    res.skus = res.skus || []
    res.skus.forEach((item) => {
      if (isDetail.value) {
        item.price = floatToFixed2(item.price)
        item.marketPrice = floatToFixed2(item.marketPrice)
        item.costPrice = floatToFixed2(item.costPrice)
        item.firstBrokeragePrice = floatToFixed2(item.firstBrokeragePrice)
        item.secondBrokeragePrice = floatToFixed2(item.secondBrokeragePrice)
      } else {
        // 回显价格分转元
        item.price = formatToFraction(item.price)
        item.marketPrice = formatToFraction(item.marketPrice)
        item.costPrice = formatToFraction(item.costPrice)
        item.firstBrokeragePrice = formatToFraction(item.firstBrokeragePrice)
        item.secondBrokeragePrice = formatToFraction(item.secondBrokeragePrice)
      }
    })
    formData.value = res
    recordLoadState.value = 'ready'
  } catch {
    recordLoadDescription.value =
      '未能读取这条商品记录。记录可能已删除、当前账号无查看权限，或商品服务暂时不可用。'
    recordLoadState.value = 'error'
  } finally {
    formLoading.value = false
  }
}

/** 提交按钮 */
const submitForm = async () => {
  // 提交请求
  formLoading.value = true
  try {
    // 校验各表单
    await unref(infoRef)?.validate()
    await unref(skuRef)?.validate()
    if (!isB2B.value) {
      await unref(deliveryRef)?.validate()
    }
    await unref(descriptionRef)?.validate()
    await unref(furnitureDetailRef)?.validate()
    await unref(otherRef)?.validate()
    // 深拷贝一份, 这样最终 server 端不满足，不需要影响原始数据
    const deepCopyFormData = cloneDeep(unref(formData.value)) as ProductSpuApi.Spu
    // 校验商品名称不能为空（用于 SKU name）
    if (isEmpty(deepCopyFormData.name)) {
      message.error('商品名称不能为空')
      return
    }
    deepCopyFormData.skus!.forEach((item) => {
      // 给sku name赋值（使用商品名称作为 SKU 名称）
      item.name = deepCopyFormData.name
      // sku相关价格元转分
      item.price = convertToInteger(item.price)
      item.marketPrice = convertToInteger(item.marketPrice)
      item.costPrice = convertToInteger(item.costPrice)
      item.firstBrokeragePrice = convertToInteger(item.firstBrokeragePrice)
      item.secondBrokeragePrice = convertToInteger(item.secondBrokeragePrice)
    })
    // 处理轮播图列表
    const newSliderPicUrls: any[] = []
    deepCopyFormData.sliderPicUrls!.forEach((item: any) => {
      // 如果是前端选的图
      typeof item === 'object' ? newSliderPicUrls.push(item.url) : newSliderPicUrls.push(item)
    })
    deepCopyFormData.sliderPicUrls = newSliderPicUrls
    // 校验都通过后提交表单
    const data = deepCopyFormData as ProductSpuApi.Spu
    const id = params.id as unknown as number
    if (!id) {
      await ProductSpuApi.createSpu(data)
      message.success(t('common.createSuccess'))
    } else {
      await ProductSpuApi.updateSpu(data)
      message.success(t('common.updateSuccess'))
    }
    close()
  } finally {
    formLoading.value = false
  }
}

/** 关闭按钮 */
const close = () => {
  delView(unref(currentRoute))
  push({ name: 'ProductSpu' })
}

const initializePage = async () => {
  try {
    await loadTenantBusinessProfile()
  } catch {
    return
  }
  await getDetail()
}

/** 初始化 */
onMounted(initializePage)

watch(showB2CFields, (visible) => {
  if (!visible && isB2B.value && activeName.value === 'delivery') {
    activeName.value = 'info'
  }
})
</script>

<style scoped>
.b2b-mode-panel {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  padding: 16px;
  margin-bottom: 16px;
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: var(--el-border-radius-base);
}
</style>
