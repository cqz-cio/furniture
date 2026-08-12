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
    <el-form-item label="商品分类" prop="categoryId">
      <el-cascader
        v-model="formData.categoryId"
        :options="categoryList"
        :props="defaultProps"
        class="w-80!"
        clearable
        filterable
        placeholder="请选择商品分类"
        @change="syncCategorySelection"
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
import { CategoryVO } from '@/api/mall/product/category'
import * as ProductBrandApi from '@/api/mall/product/brand'
import { BrandVO } from '@/api/mall/product/brand'
import { RefreshRight } from '@element-plus/icons-vue'
import type { ProductFieldState } from '@/api/system/tenant'

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
const formData = reactive<Spu>({
  name: '', // 商品名称
  categoryId: undefined, // 商品分类
  keyword: '', // 关键字
  picUrl: '', // 商品封面图
  sliderPicUrls: [], // 商品轮播图
  introduction: '', // 商品简介
  brandId: undefined // 商品品牌
})
const rules = reactive({
  name: [required],
  categoryId: [required],
  keyword: [required],
  introduction: [required],
  picUrl: [required],
  sliderPicUrls: [required],
  brandId: [required]
})

const syncCategorySelection = (categoryId: unknown) => {
  if (categoryId === null || categoryId === undefined || categoryId === '') {
    props.propFormData.categoryId = undefined
    return
  }
  const normalizedCategoryId = Number(categoryId)
  props.propFormData.categoryId = Number.isFinite(normalizedCategoryId)
    ? normalizedCategoryId
    : undefined
}

/** 将传进来的值赋值给 formData */
watch(
  () => props.propFormData,
  (data) => {
    if (!data) {
      return
    }
    copyValueToTarget(formData, data)
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
const categoryList = ref<CategoryVO[]>([]) // 商品分类树
async function refreshCategoryList() {
  // 获得分类树
  const data = await ProductCategoryApi.getCategoryList({})
  categoryList.value = handleTree(data, 'id')
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
