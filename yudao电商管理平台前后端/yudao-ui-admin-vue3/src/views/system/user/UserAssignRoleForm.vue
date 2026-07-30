<template>
  <Dialog v-model="dialogVisible" title="分配角色">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="80px"
    >
      <el-form-item label="用户名称">
        <el-input v-model="formData.username" :disabled="true" />
      </el-form-item>
      <el-form-item label="用户昵称">
        <el-input v-model="formData.nickname" :disabled="true" />
      </el-form-item>
      <el-form-item label="角色" prop="roleId">
        <el-select
          v-model="formData.roleId"
          :disabled="isCurrentSuperAdmin"
          placeholder="请选择唯一角色"
        >
          <el-option
            v-for="item in assignableRoleList"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import * as PermissionApi from '@/api/system/permission'
import * as UserApi from '@/api/system/user'
import * as RoleApi from '@/api/system/role'

defineOptions({ name: 'SystemUserAssignRoleForm' })

const { t } = useI18n() // 国际化
const message = useMessage() // 消息弹窗

const dialogVisible = ref(false) // 弹窗的是否展示
const formLoading = ref(false) // 表单的加载中：1）修改时的数据加载；2）提交的按钮禁用
const formData = ref<{
  id: number
  nickname: string
  username: string
  roleId?: number
}>({
  id: -1,
  nickname: '',
  username: '',
  roleId: undefined
})
const formRules = {
  roleId: [{ required: true, message: '角色不能为空', trigger: 'change' }]
}
const formRef = ref() // 表单 Ref
const roleList = ref([] as RoleApi.RoleVO[]) // 角色的列表
const isCurrentSuperAdmin = computed(() =>
  roleList.value.some((item) => item.id === formData.value.roleId && item.code === 'super_admin')
)
const assignableRoleList = computed(() =>
  roleList.value.filter((item) => item.code !== 'super_admin' || isCurrentSuperAdmin.value)
)

/** 打开弹窗 */
const open = async (row: UserApi.UserVO) => {
  dialogVisible.value = true
  resetForm()
  // 设置数据
  formData.value.id = row.id
  formData.value.username = row.username
  formData.value.nickname = row.nickname
  // 获得角色拥有的菜单集合
  formLoading.value = true
  try {
    const roleIds = await PermissionApi.getUserRoleList(row.id)
    formData.value.roleId = roleIds.length === 1 ? roleIds[0] : undefined
  } finally {
    formLoading.value = false
  }
  // 获得角色列表
  roleList.value = await RoleApi.getSimpleRoleList()
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
    await PermissionApi.assignUserRole({
      userId: formData.value.id,
      roleIds: [formData.value.roleId!]
    })
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
    id: -1,
    nickname: '',
    username: '',
    roleId: undefined
  }
  formRef.value?.resetFields()
}
</script>
