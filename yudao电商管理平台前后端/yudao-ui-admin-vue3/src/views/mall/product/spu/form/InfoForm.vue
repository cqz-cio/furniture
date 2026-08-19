<!-- 商品发布 - 基础设置 -->
<template>
  <el-form ref="formRef" :disabled="isDetail" :model="formData" :rules="rules" label-width="120px">
    <el-form-item label="商品名称" prop="name">
      <el-input
        v-model="formData.name"
        :autosize="{ minRows: 2, maxRows: 2 }"
        :clearable="true"
        :show-word-limit="true"
        class="w-80!"
        maxlength="64"
        placeholder="请输入商品名称"
        type="textarea"
      />
      <el-tag v-if="isB2B" class="ml-8px" effect="plain" size="small" type="success">
        网站公开
      </el-tag>
    </el-form-item>
    <template v-if="isB2B">
      <el-form-item label="Room" prop="roomCategoryId">
        <el-select
          v-model="formData.roomCategoryId"
          class="w-80!"
          clearable
          placeholder="请选择 Dining、Living 或 Bedroom Room"
          @change="syncRoomSelection"
        >
          <el-option
            v-for="room in productRoomOptions"
            :key="room.value"
            :label="room.label"
            :value="room.value"
          />
        </el-select>
        <el-button :icon="RefreshRight" @click="refreshCategoryList" class="ml-1" size="small" />
      </el-form-item>
      <el-form-item label="Product type" prop="categoryId">
        <el-select
          v-model="formData.categoryId"
          class="w-80!"
          clearable
          :disabled="!formData.roomCategoryId"
          placeholder="请选择当前 Room 下的 Product type"
          @change="syncProductTypeSelection"
        >
          <el-option
            v-for="option in productTypeOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-tag
          class="ml-8px"
          effect="plain"
          size="small"
          :type="fieldTagType('category')"
        >
          {{ fieldTagLabel('category') }}
        </el-tag>
      </el-form-item>
    </template>
    <el-form-item v-else label="商品分类" prop="categoryId">
      <el-cascader
        v-model="formData.categoryId"
        :options="categoryTree"
        :props="defaultProps"
        class="w-80!"
        clearable
        filterable
        placeholder="请选择商品分类"
        @change="syncProductTypeSelection"
      />
      <el-button :icon="RefreshRight" @click="refreshCategoryList" class="ml-1" size="small" />
      <el-tag
        v-if="isB2B"
        class="ml-8px"
        effect="plain"
        size="small"
        :type="fieldTagType('category')"
      >
        {{ fieldTagLabel('category') }}
      </el-tag>
    </el-form-item>
    <el-form-item label="商品品牌" prop="brandId">
      <el-select v-model="formData.brandId" class="w-80!" placeholder="请选择商品品牌">
        <el-option
          v-for="item in brandList"
          :key="item.id"
          :label="item.name"
          :value="item.id as number"
        />
      </el-select>
      <el-button :icon="RefreshRight" @click="refreshBrandList" class="ml-1" size="small" />
      <el-tag v-if="isB2B" class="ml-8px" effect="plain" size="small" type="info">
        ERP 内部
      </el-tag>
    </el-form-item>
    <el-form-item label="商品关键字" prop="keyword">
      <el-input v-model="formData.keyword" class="w-80!" placeholder="请输入商品关键字" />
      <el-tag v-if="isB2B" class="ml-8px" effect="plain" size="small" type="info">
        ERP 内部
      </el-tag>
    </el-form-item>
    <el-form-item :label="isB2B ? '商品卡片简介' : '商品简介'" prop="introduction">
      <div class="w-80!">
        <el-input
          v-model="formData.introduction"
          :autosize="{ minRows: 2, maxRows: 4 }"
          :clearable="true"
          :show-word-limit="true"
          class="w-100!"
          maxlength="256"
          :placeholder="isB2B ? '用于商品列表卡片的简短介绍' : '请输入商品简介'"
          type="textarea"
        />
        <div v-if="isB2B" class="mt-4px text-12px text-[var(--el-text-color-secondary)]">
          此处仅用于列表卡片；详情页标题下方的完整描述请填写在“详情页描述”标签页。
        </div>
      </div>
      <el-tag
        v-if="isB2B"
        class="ml-8px"
        effect="plain"
        size="small"
        :type="fieldTagType('introduction')"
      >
        {{ fieldTagLabel('introduction') }}
      </el-tag>
    </el-form-item>
    <el-form-item label="商品封面图" prop="picUrl">
      <UploadImg v-model="formData.picUrl" :disabled="isDetail" height="80px" />
      <el-tag v-if="isB2B" class="ml-8px" effect="plain" size="small" type="success">
        网站公开
      </el-tag>
    </el-form-item>
    <el-form-item label="商品轮播图" prop="sliderPicUrls">
      <UploadImgs v-model="formData.sliderPicUrls" :disabled="isDetail" />
      <el-tag v-if="isB2B" class="ml-8px" effect="plain" size="small" type="success">
        网站公开
      </el-tag>
    </el-form-item>
  </el-form>
</template>
<script lang="ts" setup>
import { computed, onMounted, reactive, ref, unref, watch } from 'vue'
import type { PropType } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { copyValueToTarget } from '@/utils'
import { required } from '@/utils/formRules'
import { propTypes } from '@/utils/propTypes'
import { defaultProps, handleTree } from '@/utils/tree'
import type { Spu } from '@/api/mall/product/spu'
import * as ProductCategoryApi from '@/api/mall/product/category'
import type { CategoryVO } from '@/api/mall/product/category'
import * as ProductBrandApi from '@/api/mall/product/brand'
import type { BrandVO } from '@/api/mall/product/brand'
import { RefreshRight } from '@element-plus/icons-vue'
import type { ProductFieldState } from '@/api/system/tenant'
import {
  getProductRoomOptions,
  getProductTypeOptions,
  isProductTypeSelectionValid,
  migrateProductType,
  resolveProductRoom
} from './productTypeOptions'

defineOptions({ name: 'ProductSpuInfoForm' })
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
  }
})
const isB2B = computed(() => props.businessMode === 'B2B')
const fieldState = (field: string): ProductFieldState => props.fieldStates[field] || 'INTERNAL'
const fieldTagLabel = (field: string) => (fieldState(field) === 'WEBSITE' ? '网站公开' : 'ERP 内部')
const fieldTagType = (field: string): 'success' | 'info' =>
  fieldState(field) === 'WEBSITE' ? 'success' : 'info'

const message = useMessage() // 消息弹窗

const formRef = ref() // 表单 Ref
const formData = reactive<Spu & { sliderPicUrls: string[] }>({
  name: '', // 商品名称
  roomCategoryId: undefined, // B2B P1 Room
  categoryId: undefined, // 商品分类
  keyword: '', // 关键字
  picUrl: '', // 商品封面图
  sliderPicUrls: [], // 商品轮播图
  introduction: '', // 商品简介
  brandId: undefined // 商品品牌
})
const rules = computed(() => ({
  name: [required],
  roomCategoryId: isB2B.value ? [required] : [],
  categoryId: [required],
  keyword: [required],
  introduction: [required],
  picUrl: [required],
  sliderPicUrls: [required],
  brandId: [required]
}))

const syncProductTypeSelection = (categoryId: unknown) => {
  if (categoryId === null || categoryId === undefined || categoryId === '') {
    props.propFormData.categoryId = undefined
    return
  }
  const normalizedCategoryId = Number(categoryId)
  props.propFormData.categoryId = Number.isFinite(normalizedCategoryId)
    ? normalizedCategoryId
    : undefined
}

const syncRoomSelection = (roomCategoryId: unknown) => {
  const normalizedRoomId = Number(roomCategoryId)
  formData.roomCategoryId = Number.isFinite(normalizedRoomId) ? normalizedRoomId : undefined
  props.propFormData.roomCategoryId = formData.roomCategoryId
  if (
    !isProductTypeSelectionValid(
      categoryList.value,
      formData.roomCategoryId,
      formData.categoryId
    )
  ) {
    formData.categoryId = undefined
    props.propFormData.categoryId = undefined
  }
}

/** 将传进来的值赋值给 formData */
watch(
  () => props.propFormData,
  (data) => {
    if (!data) {
      return
    }
    copyValueToTarget(formData, data)
    formData.sliderPicUrls = Array.isArray(data.sliderPicUrls) ? data.sliderPicUrls : []
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
    if (
      isB2B.value &&
      !isProductTypeSelectionValid(
        categoryList.value,
        formData.roomCategoryId,
        formData.categoryId
      )
    ) {
      throw new Error('Product type must belong to the selected Room')
    }
    // 校验通过更新数据
    Object.assign(props.propFormData, formData)
  } catch (e) {
    message.error('【基础设置】不完善，请填写相关信息')
    emit('update:activeName', 'info')
    throw e // 目的截断之后的校验
  }
}
defineExpose({ validate })

/** 初始化 */
const brandList = ref<BrandVO[]>([]) // 商品品牌列表
const categoryList = ref<CategoryVO[]>([]) // 商品分类平铺列表
const categoryTree = computed(() => handleTree(categoryList.value, 'id'))
const productRoomOptions = computed(() => getProductRoomOptions(categoryList.value))
const productTypeOptions = computed(() =>
  getProductTypeOptions(categoryList.value, formData.roomCategoryId)
)

const restoreB2BCategorySelection = () => {
  if (!isB2B.value) return
  const room = resolveProductRoom(categoryList.value, formData.categoryId)
  if (room?.id) {
    formData.roomCategoryId = Number(room.id)
  }
  const legacyProductType = String((props.propFormData as any)?.detailConfig?.productType || '')
  if (
    formData.roomCategoryId &&
    !isProductTypeSelectionValid(
      categoryList.value,
      formData.roomCategoryId,
      formData.categoryId
    )
  ) {
    formData.categoryId = migrateProductType(
      categoryList.value,
      formData.roomCategoryId,
      legacyProductType
    )
  }
  props.propFormData.roomCategoryId = formData.roomCategoryId
  props.propFormData.categoryId = formData.categoryId
}

async function refreshCategoryList() {
  categoryList.value = await ProductCategoryApi.getCategoryList({})
  restoreB2BCategorySelection()
}

async function refreshBrandList() {
  brandList.value = await ProductBrandApi.getSimpleBrandList()
}

onMounted(async () => {
  await refreshCategoryList()
  // 获取商品品牌列表
  await refreshBrandList()
})
</script>
