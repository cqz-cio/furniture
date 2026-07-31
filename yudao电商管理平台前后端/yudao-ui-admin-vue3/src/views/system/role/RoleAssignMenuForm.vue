<template>
  <Dialog v-model="dialogVisible" title="菜单权限">
    <el-form ref="formRef" v-loading="formLoading" :model="formData" label-width="80px">
      <el-form-item label="角色名称">
        <el-tag>{{ formData.name }}</el-tag>
      </el-form-item>
      <el-form-item label="角色标识">
        <el-tag>{{ formData.code }}</el-tag>
      </el-form-item>
      <el-form-item label="菜单权限">
        <el-card class="h-400px w-full" shadow="never">
          <template #header>
            全选/全不选:
            <el-switch
              v-model="treeNodeAll"
              active-text="是"
              inactive-text="否"
              inline-prompt
              @change="handleCheckedTreeNodeAll"
            />
            全部展开/折叠:
            <el-switch
              v-model="menuExpand"
              active-text="展开"
              inactive-text="折叠"
              inline-prompt
              @change="handleCheckedTreeExpand"
            />
          </template>
          <el-tree-v2
            ref="treeRef"
            :data="menuOptions"
            :props="defaultProps"
            :height="300"
            :check-on-click-leaf="false"
            empty-text="加载中，请稍候"
            show-checkbox
          />
        </el-card>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import type { TreeV2Instance } from 'element-plus'
import { defaultProps, handleTree } from '@/utils/tree'
import * as RoleApi from '@/api/system/role'
import * as MenuApi from '@/api/system/menu'
import * as PermissionApi from '@/api/system/permission'

defineOptions({ name: 'SystemRoleAssignMenuForm' })

const { t } = useI18n() // 国际化
const message = useMessage() // 消息弹窗

const dialogVisible = ref(false) // 弹窗的是否展示
const formLoading = ref(false) // 表单的加载中：1）修改时的数据加载；2）提交的按钮禁用
const formData = reactive({
  id: undefined as number | undefined,
  name: '',
  code: '',
  menuIds: [] as number[]
})
const formRef = ref() // 表单 Ref
const menuOptions = ref<any[]>([]) // 菜单树形结构
const menuExpandableIds = ref<number[]>([]) // 可展开的菜单节点
const menuLeafIds = ref<number[]>([]) // 叶子菜单节点，用于恢复勾选状态
const menuExpand = ref(false) // 展开/折叠
const treeRef = ref<TreeV2Instance>() // 菜单树组件 Ref
const treeNodeAll = ref(false) // 全选/全不选

/** 打开弹窗 */
const open = async (row: RoleApi.RoleVO) => {
  dialogVisible.value = true
  resetForm()
  Object.assign(formData, {
    id: row.id,
    name: row.name,
    code: row.code
  })
  formLoading.value = true
  try {
    // 菜单和角色权限互不依赖，并行加载可减少弹窗等待时间
    const [menus, roleMenuIds] = await Promise.all([
      MenuApi.getSimpleMenusList(),
      PermissionApi.getRoleMenuList(row.id)
    ])
    menuOptions.value = handleTree(menus)
    formData.menuIds = roleMenuIds

    const { expandableIds, leafIds } = collectMenuTreeIds(menuOptions.value)
    menuExpandableIds.value = expandableIds
    menuLeafIds.value = leafIds

    await nextTick()
    // 角色权限包含半选父节点，只用叶子节点恢复选择，避免父节点误选全部子菜单
    const roleMenuIdSet = new Set(roleMenuIds)
    treeRef.value?.setCheckedKeys(leafIds.filter((id) => roleMenuIdSet.has(id)))
  } finally {
    formLoading.value = false
  }
}
defineExpose({ open }) // 提供 open 方法，用于打开弹窗

/** 提交表单 */
const emit = defineEmits(['success']) // 定义 success 事件，用于操作成功后的回调
const submitForm = async () => {
  // 校验表单
  if (!formRef.value || formData.id === undefined || !treeRef.value) return
  const valid = await formRef.value.validate()
  if (!valid) return
  // 提交请求
  formLoading.value = true
  try {
    const data = {
      roleId: formData.id,
      menuIds: [
        ...(treeRef.value.getCheckedKeys(false) as number[]), // 获得当前选中节点
        ...(treeRef.value.getHalfCheckedKeys() as number[]) // 获得半选中的父节点
      ]
    }
    await PermissionApi.assignRoleMenu(data)
    message.success(t('common.updateSuccess'))
    dialogVisible.value = false
    // 发送操作成功的事件
    emit('success')
  } finally {
    formLoading.value = false
  }
}

/** 重置表单 */
const resetForm = () => {
  // 重置选项
  treeNodeAll.value = false
  menuExpand.value = false
  menuOptions.value = []
  menuExpandableIds.value = []
  menuLeafIds.value = []
  // 重置表单
  Object.assign(formData, {
    id: undefined,
    name: '',
    code: '',
    menuIds: []
  })
  treeRef.value?.setCheckedKeys([])
  treeRef.value?.setExpandedKeys([])
  formRef.value?.resetFields()
}

/** 全选/全不选 */
const handleCheckedTreeNodeAll = () => {
  treeRef.value?.setCheckedKeys(treeNodeAll.value ? menuLeafIds.value : [])
}

/** 展开/折叠全部 */
const handleCheckedTreeExpand = () => {
  treeRef.value?.setExpandedKeys(menuExpand.value ? menuExpandableIds.value : [])
}

/** 一次遍历收集树节点，避免每次切换时重复扫描整棵菜单树 */
const collectMenuTreeIds = (tree: any[]) => {
  const expandableIds: number[] = []
  const leafIds: number[] = []
  const stack = [...tree]

  while (stack.length > 0) {
    const node = stack.pop()
    if (!node) continue
    const children = Array.isArray(node.children) ? node.children : []
    if (children.length > 0) {
      expandableIds.push(node.id)
      stack.push(...children)
    } else {
      leafIds.push(node.id)
    }
  }

  return { expandableIds, leafIds }
}
</script>
