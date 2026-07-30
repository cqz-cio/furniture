<!-- 商品发布 - 其它设置 -->
<template>
  <el-form ref="formRef" :model="formData" :rules="rules" label-width="120px" :disabled="isDetail">
    <el-form-item label="商品排序" prop="sort">
      <el-input-number
        v-model="formData.sort"
        :min="0"
        placeholder="请输入商品排序"
        class="w-80!"
      />
    </el-form-item>
    <el-form-item v-if="showGiveIntegral" prop="giveIntegral">
      <template #label>
        <span>赠送积分</span>
        <el-tag
          v-if="giveIntegralNotApplicable"
          class="ml-4px"
          effect="plain"
          size="small"
          type="warning"
        >
          B2C 专用
        </el-tag>
      </template>
      <el-input-number
        v-model="formData.giveIntegral"
        :disabled="giveIntegralNotApplicable"
        :min="0"
        placeholder="请输入赠送积分"
        class="w-80!"
      />
    </el-form-item>
    <el-form-item v-if="showVirtualSalesCount" prop="virtualSalesCount">
      <template #label>
        <span>虚拟销量</span>
        <el-tag
          v-if="virtualSalesCountNotApplicable"
          class="ml-4px"
          effect="plain"
          size="small"
          type="warning"
        >
          B2C 专用
        </el-tag>
      </template>
      <el-input-number
        v-model="formData.virtualSalesCount"
        :disabled="virtualSalesCountNotApplicable"
        :min="0"
        placeholder="请输入虚拟销量"
        class="w-80!"
      />
    </el-form-item>
  </el-form>
</template>
<script lang="ts" setup>
import type { Spu } from '@/api/mall/product/spu'
import { PropType } from 'vue'
import { propTypes } from '@/utils/propTypes'
import { copyValueToTarget } from '@/utils'
import type { ProductFieldState } from '@/api/system/tenant'

defineOptions({ name: 'ProductOtherForm' })

const message = useMessage() // 消息弹窗

const props = defineProps({
  propFormData: {
    type: Object as PropType<Spu>,
    default: () => {}
  },
  isDetail: propTypes.bool.def(false), // 是否作为详情组件
  businessMode: propTypes.string.def('B2C'),
  fieldStates: {
    type: Object as PropType<Record<string, ProductFieldState>>,
    default: () => ({})
  },
  showInactiveFields: propTypes.bool.def(false)
})

const isB2B = computed(() => props.businessMode === 'B2B')
const fieldState = (field: string): ProductFieldState => props.fieldStates[field] || 'INTERNAL'
const giveIntegralNotApplicable = computed(
  () => isB2B.value && fieldState('giveIntegral') === 'NOT_APPLICABLE'
)
const virtualSalesCountNotApplicable = computed(
  () => isB2B.value && fieldState('virtualSalesCount') === 'NOT_APPLICABLE'
)
const showGiveIntegral = computed(
  () => !giveIntegralNotApplicable.value || props.showInactiveFields
)
const showVirtualSalesCount = computed(
  () => !virtualSalesCountNotApplicable.value || props.showInactiveFields
)
const formRef = ref() // 表单Ref
// 表单数据
const formData = ref<Spu>({
  sort: 0, // 商品排序
  giveIntegral: 0, // 赠送积分
  virtualSalesCount: 0 // 虚拟销量
})
// 表单规则
const rules = computed(() => ({
  sort: [required],
  ...(!giveIntegralNotApplicable.value ? { giveIntegral: [required] } : {}),
  ...(!virtualSalesCountNotApplicable.value ? { virtualSalesCount: [required] } : {})
}))

/** 将传进来的值赋值给 formData */
watch(
  () => props.propFormData,
  (data) => {
    if (!data) {
      return
    }
    copyValueToTarget(formData.value, data)
  },
  {
    immediate: true
  }
)

/** 表单校验 */
const emit = defineEmits(['update:activeName'])
const validate = async () => {
  if (!formRef) return
  try {
    await unref(formRef)?.validate()
    // 校验通过更新数据
    Object.assign(props.propFormData, formData.value)
  } catch (e) {
    message.error(
      isB2B.value ? '【展示设置】不完善，请填写相关信息' : '【其它设置】不完善，请填写相关信息'
    )
    emit('update:activeName', 'other')
    throw e // 目的截断之后的校验
  }
}
defineExpose({ validate })
</script>
