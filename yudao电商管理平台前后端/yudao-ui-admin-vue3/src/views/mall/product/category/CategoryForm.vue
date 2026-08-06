<template>
  <Dialog :title="dialogTitle" v-model="dialogVisible">
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="120px"
      v-loading="formLoading"
    >
      <el-alert
        v-if="props.navigationCreate && formType === 'create'"
        title="创建完成后会自动加入 Products 二级目录，保存并发布后才会在官网显示。"
        type="info"
        show-icon
        :closable="false"
        class="mb-16px"
      />
      <el-form-item label="上级分类" prop="parentId">
        <el-select v-model="formData.parentId" placeholder="请选择上级分类">
          <el-option v-if="!props.navigationCreate" :key="0" label="顶级分类" :value="0" />
          <el-option
            v-for="item in categoryList"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="分类名称" prop="name">
        <div class="category-name-field">
          <el-input
            v-model="formData.name"
            maxlength="64"
            show-word-limit
            placeholder="请输入分类名称"
          />
          <span>如果该分类已加入官网 Products 导航，确认修改后已发布官网会立即同步。</span>
        </div>
      </el-form-item>
      <el-form-item label="移动端分类图" prop="picUrl">
        <UploadImg v-model="formData.picUrl" :limit="1" :is-show-tip="false" />
        <div style="font-size: 10px" class="pl-10px">推荐 180x180 图片分辨率</div>
      </el-form-item>
      <el-form-item v-if="!props.navigationCreate" label="分类排序" prop="sort">
        <el-input-number v-model="formData.sort" controls-position="right" :min="0" />
      </el-form-item>
      <el-form-item v-if="!props.navigationCreate" label="开启状态" prop="status">
        <el-radio-group v-model="formData.status">
          <el-radio
            v-for="dict in getIntDictOptions(DICT_TYPE.COMMON_STATUS)"
            :key="dict.value"
            :value="dict.value"
          >
            {{ dict.label }}
          </el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="submitForm" type="primary" :disabled="formLoading">
        {{ props.navigationCreate && formType === 'create' ? '创建并加入导航' : '确 定' }}
      </el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import { reactive, ref } from 'vue'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { CommonStatusEnum } from '@/utils/constants'
import * as ProductCategoryApi from '@/api/mall/product/category'
import { useI18n } from '@/hooks/web/useI18n'
import { useMessage } from '@/hooks/web/useMessage'

defineOptions({ name: 'ProductCategory' })

const props = withDefaults(
  defineProps<{
    navigationCreate?: boolean
  }>(),
  {
    navigationCreate: false
  }
)

const { t } = useI18n() // 国际化
const message = useMessage() // 消息弹窗

const dialogVisible = ref(false) // 弹窗的是否展示
const dialogTitle = ref('') // 弹窗的标题
const formLoading = ref(false) // 表单的加载中：1）修改时的数据加载；2）提交的按钮禁用
const formType = ref('') // 表单的类型：create - 新增；update - 修改
const originalCategoryName = ref('')
const formData = ref<ProductCategoryApi.CategoryVO>({
  id: undefined,
  parentId: props.navigationCreate ? undefined : 0,
  name: '',
  picUrl: '',
  sort: 0,
  status: CommonStatusEnum.ENABLE
})
const formRules = reactive({
  parentId: [{ required: true, message: '请选择上级分类', trigger: 'blur' }],
  name: [{ required: true, message: '分类名称不能为空', trigger: 'blur' }],
  picUrl: [{ required: true, message: '分类图片不能为空', trigger: 'blur' }],
  sort: [{ required: true, message: '分类排序不能为空', trigger: 'blur' }],
  status: [{ required: true, message: '开启状态不能为空', trigger: 'blur' }]
})
const formRef = ref() // 表单 Ref
const categoryList = ref<any[]>([]) // 分类树

/** 打开弹窗 */
const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  formType.value = type
  dialogTitle.value =
    props.navigationCreate && type === 'create' ? '新建商品分类并加入导航' : t('action.' + type)
  resetForm()
  // 修改时，设置数据
  if (id) {
    formLoading.value = true
    try {
      formData.value = await ProductCategoryApi.getCategory(id)
      originalCategoryName.value = formData.value.name
    } finally {
      formLoading.value = false
    }
  }
  // 获得分类树
  categoryList.value = await ProductCategoryApi.getCategoryList({ parentId: 0 })
  if (props.navigationCreate && type === 'create' && categoryList.value.length === 1) {
    formData.value.parentId = categoryList.value[0].id
  }
}
defineExpose({ open }) // 提供 open 方法，用于打开弹窗

/** 提交表单 */
const emit = defineEmits(['success']) // 定义 success 事件，用于操作成功后的回调
const submitForm = async () => {
  formData.value.name = formData.value.name.trim()
  // 校验表单
  if (!formRef) return
  const valid = await formRef.value.validate()
  if (!valid) return
  // 提交请求
  formLoading.value = true
  try {
    const data = formData.value as ProductCategoryApi.CategoryVO
    let savedCategoryId = data.id
    const categoryNameChanged =
      formType.value === 'update' && data.name !== originalCategoryName.value.trim()
    if (categoryNameChanged) {
      await message.confirm(
        `确认把分类名称“${originalCategoryName.value}”修改为“${data.name}”吗？\n如果该分类已加入官网 Products 二级导航，已发布官网会立即同步，无需再次发布。`,
        '同步分类名称'
      )
    }
    if (formType.value === 'create') {
      savedCategoryId = await ProductCategoryApi.createCategory(data)
      if (!props.navigationCreate) {
        message.success(t('common.createSuccess'))
      }
    } else {
      await ProductCategoryApi.updateCategory(data)
      message.success(
        categoryNameChanged ? '商品分类与官网二级导航名称已同步' : t('common.updateSuccess')
      )
    }
    dialogVisible.value = false
    // 发送操作成功的事件
    emit('success', { id: savedCategoryId, name: data.name })
  } finally {
    formLoading.value = false
  }
}

/** 重置表单 */
const resetForm = () => {
  originalCategoryName.value = ''
  formData.value = {
    id: undefined,
    parentId: props.navigationCreate ? undefined : 0,
    name: '',
    picUrl: '',
    sort: 0,
    status: CommonStatusEnum.ENABLE
  }
  formRef.value?.resetFields()
}
</script>

<style scoped>
.category-name-field {
  display: grid;
  width: 100%;
  gap: 6px;
}

.category-name-field span {
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
</style>
