<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="50%">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="80px"
    >
      <el-form-item label="租户名" prop="name">
        <el-input v-model="formData.name" placeholder="请输入租户名" />
      </el-form-item>
      <el-form-item label="租户编码" prop="code">
        <el-input
          v-model="formData.code"
          :disabled="formType === 'update'"
          maxlength="16"
          placeholder="例如 VANZ"
          @input="handleTenantCodeInput"
        />
        <div class="text-12px text-gray-500">
          用于生成 SKU，创建后不可修改
        </div>
      </el-form-item>
      <el-form-item label="租户套餐" prop="packageId">
        <el-select v-model="formData.packageId" clearable placeholder="请选择租户套餐">
          <el-option
            v-for="item in packageList"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="业务模式" prop="businessMode">
        <el-radio-group v-model="formData.businessMode">
          <el-radio v-for="item in businessModeOptions" :key="item.value" :value="item.value">
            {{ item.label }}
          </el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="formData.businessMode === 'B2B'" label="网站字段">
        <div class="tenant-product-field-config">
          <div class="tenant-product-field-config__hint">
            商品名称和图片固定开放；以下配置同时控制公开商品接口和家具 2B 网站展示。
          </div>
          <el-checkbox-group
            v-model="formData.websiteProductFields"
            class="tenant-product-field-config__options"
          >
            <el-checkbox
              v-for="item in websiteProductFieldOptions"
              :key="item.value"
              :value="item.value"
            >
              {{ item.label }}
            </el-checkbox>
          </el-checkbox-group>
          <el-button link type="primary" @click="useRecommendedB2BProductFields">
            恢复 ToB 推荐字段
          </el-button>
        </div>
      </el-form-item>
      <el-form-item label="联系人" prop="contactName">
        <el-input v-model="formData.contactName" placeholder="请输入联系人" />
      </el-form-item>
      <el-form-item label="联系手机" prop="contactMobile">
        <el-input v-model="formData.contactMobile" placeholder="请输入联系手机" />
      </el-form-item>
      <el-form-item v-if="formData.id === undefined" label="用户名称" prop="username">
        <el-input v-model="formData.username" placeholder="请输入用户名称" />
      </el-form-item>
      <el-form-item v-if="formData.id === undefined" label="用户密码" prop="password">
        <el-input
          v-model="formData.password"
          placeholder="请输入用户密码"
          show-password
          type="password"
        />
      </el-form-item>
      <el-form-item label="账号额度" prop="accountCount">
        <el-input-number
          v-model="formData.accountCount"
          :min="0"
          controls-position="right"
          placeholder="请输入账号额度"
        />
      </el-form-item>
      <el-form-item label="过期时间" prop="expireTime">
        <el-date-picker
          v-model="formData.expireTime"
          clearable
          placeholder="请选择过期时间"
          type="date"
          value-format="x"
        />
      </el-form-item>
      <el-form-item label="绑定域名" prop="websites">
        <el-input-tag
          v-model="formData.websites"
          placeholder="请输入绑定域名，按回车添加"
          class="w-full"
        />
      </el-form-item>
      <el-form-item label="租户状态" prop="status">
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
      <el-button :disabled="formLoading" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import * as TenantApi from '@/api/system/tenant'
import { CommonStatusEnum } from '@/utils/constants'
import * as TenantPackageApi from '@/api/system/tenantPackage'

defineOptions({ name: 'SystemTenantForm' })

const { t } = useI18n() // 国际化
const message = useMessage() // 消息弹窗
const dialogVisible = ref(false) // 弹窗的是否展示
const dialogTitle = ref('') // 弹窗的标题
const formLoading = ref(false) // 表单的加载中：1）修改时的数据加载；2）提交的按钮禁用
const formType = ref('') // 表单的类型：create - 新增；update - 修改
const businessModeOptions = [
  { label: 'ToC（零售型）', value: 'B2C' },
  { label: 'ToB（询盘型）', value: 'B2B' }
]
const websiteProductFieldOptions = [
  { label: '商品分类', value: 'category' },
  { label: '新品/精品标识', value: 'badges' },
  { label: '商品简介', value: 'introduction' },
  { label: '销售价格', value: 'price' },
  { label: '市场价格', value: 'marketPrice' },
  { label: '库存/可售状态', value: 'inventory' },
  { label: '内部商品编号', value: 'productId' },
  { label: 'SKU 编码', value: 'skuCode' },
  { label: '系列名称', value: 'collection' },
  { label: '主图说明', value: 'heroNote' },
  { label: '面料/饰面选项', value: 'fabricSelector' },
  { label: '营销选项组', value: 'optionGroups' },
  { label: '商品亮点', value: 'highlights' },
  { label: '商品详情', value: 'description' },
  { label: '规格详情区', value: 'accordions' },
  { label: 'SKU 规格属性', value: 'skuProperties' },
  { label: '重量/体积', value: 'skuMeasurements' },
  { label: '相关商品', value: 'relatedProducts' },
  { label: '相关链接', value: 'relatedLinks' },
  { label: '商品销量', value: 'salesCount' }
]
const recommendedB2BProductFields = [
  'category',
  'badges',
  'introduction',
  'skuCode',
  'collection',
  'heroNote',
  'fabricSelector',
  'optionGroups',
  'highlights',
  'description',
  'accordions',
  'skuProperties',
  'relatedProducts',
  'relatedLinks'
]
const formData = ref({
  id: undefined,
  name: undefined,
  code: '',
  packageId: undefined,
  businessMode: 'B2C',
  websiteProductFields: [...recommendedB2BProductFields],
  contactName: undefined,
  contactMobile: undefined,
  accountCount: undefined,
  expireTime: undefined,
  websites: [],
  status: CommonStatusEnum.ENABLE,
  // 新增专属
  username: undefined,
  password: undefined
})
const formRules = reactive({
  name: [{ required: true, message: '租户名不能为空', trigger: 'blur' }],
  code: [
    { required: true, message: '租户编码不能为空', trigger: 'blur' },
    {
      pattern: /^[A-Z][A-Z0-9]{0,15}$/,
      message: '只能输入大写字母和数字，并以字母开头',
      trigger: 'blur'
    }
  ],
  packageId: [{ required: true, message: '租户套餐不能为空', trigger: 'blur' }],
  businessMode: [{ required: true, message: '业务模式不能为空', trigger: 'change' }],
  contactName: [{ required: true, message: '联系人不能为空', trigger: 'blur' }],
  status: [{ required: true, message: '租户状态不能为空', trigger: 'blur' }],
  accountCount: [{ required: true, message: '账号额度不能为空', trigger: 'blur' }],
  expireTime: [{ required: true, message: '过期时间不能为空', trigger: 'blur' }],
  username: [{ required: true, message: '用户名称不能为空', trigger: 'blur' }],
  password: [{ required: true, message: '用户密码不能为空', trigger: 'blur' }]
})
const formRef = ref() // 表单 Ref
const packageList = ref([] as TenantPackageApi.TenantPackageVO[]) // 租户套餐

const handleTenantCodeInput = (value: string) => {
  formData.value.code = value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 16)
}

const useRecommendedB2BProductFields = () => {
  formData.value.websiteProductFields = [...recommendedB2BProductFields]
}

/** 打开弹窗 */
const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = t('action.' + type)
  formType.value = type
  resetForm()
  // 修改时，设置数据
  if (id) {
    formLoading.value = true
    try {
      const tenant = await TenantApi.getTenant(id)
      formData.value = {
        ...tenant,
        websiteProductFields: Array.isArray(tenant.websiteProductFields)
          ? tenant.websiteProductFields
          : [...recommendedB2BProductFields]
      }
    } finally {
      formLoading.value = false
    }
  }
  // 加载套餐列表
  packageList.value = await TenantPackageApi.getTenantPackageList()
}
defineExpose({ open }) // 提供 open 方法，用于打开弹窗

/** 提交表单 */
const emit = defineEmits(['success']) // 定义 success 事件，用于操作成功后的回调
const submitForm = async () => {
  // 校验表单
  if (!formRef) return
  const valid = await formRef.value.validate()
  if (!valid) return
  // 提交请求
  formLoading.value = true
  try {
    const data = formData.value as unknown as TenantApi.TenantVO
    if (formType.value === 'create') {
      await TenantApi.createTenant(data)
      message.success(t('common.createSuccess'))
    } else {
      await TenantApi.updateTenant(data)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    // 发送操作成功的事件
    emit('success')
  } finally {
    formLoading.value = false
  }
}

/** 重置表单 */
const resetForm = () => {
  formData.value = {
    id: undefined,
    name: undefined,
    code: '',
    packageId: undefined,
    businessMode: 'B2C',
    websiteProductFields: [...recommendedB2BProductFields],
    contactName: undefined,
    contactMobile: undefined,
    accountCount: undefined,
    expireTime: undefined,
    websites: [],
    status: CommonStatusEnum.ENABLE,
    username: undefined,
    password: undefined
  }
  formRef.value?.resetFields()
}
</script>

<style scoped>
.tenant-product-field-config {
  width: 100%;
}

.tenant-product-field-config__hint {
  margin-bottom: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.tenant-product-field-config__options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  width: 100%;
}
</style>
