<template>
  <Form ref="formRef" :labelWidth="200" :rules="rules" :schema="schema" />
  <div style="text-align: center">
    <XButton :title="t('common.save')" type="primary" @click="submit()" />
    <XButton :title="t('common.reset')" type="danger" @click="init()" />
  </div>
</template>
<script lang="ts" setup>
import type { FormRules } from 'element-plus'
import { FormSchema } from '@/types/form'
import type { FormExpose } from '@/components/Form'
import { getUserProfile, updateUserProfile } from '@/api/system/user/profile'
import { useUserStore } from '@/store/modules/user'

defineOptions({ name: 'BasicInfo' })

const { t } = useI18n()
const message = useMessage() // 消息弹窗
const userStore = useUserStore()

// 定义事件
const emit = defineEmits<{
  (e: 'success'): void
}>()

// 表单校验
const rules = reactive<FormRules>({
  nickname: [{ required: true, message: t('profile.rules.nickname'), trigger: 'blur' }],
  email: [
    { required: true, message: t('profile.rules.mail'), trigger: 'blur' },
    {
      type: 'email',
      message: t('profile.rules.truemail'),
      trigger: ['blur', 'change']
    }
  ],
  mobile: [
    { required: true, message: t('profile.rules.phone'), trigger: 'blur' },
    {
      pattern: /^1[3-9]\d{9}$/,
      message: t('profile.rules.truephone'),
      trigger: 'blur'
    }
  ]
})
const schema = reactive<FormSchema[]>([
  {
    field: 'nickname',
    label: t('profile.user.nickname'),
    component: 'Input'
  }
])
const formRef = ref<FormExpose>() // 表单 Ref

const submit = () => {
  const elForm = unref(formRef)?.getElFormRef()
  if (!elForm) return
  elForm.validate(async (valid) => {
    if (valid) {
      const formModel = unref(formRef)?.formModel
      await updateUserProfile({ nickname: formModel?.nickname })
      message.success(t('common.updateSuccess'))
      const profile = await init()
      await userStore.setUserNicknameAction(profile.nickname)
      // 发送成功事件
      emit('success')
    }
  })
}

const init = async () => {
  const res = await getUserProfile()
  unref(formRef)?.setValues(res)
  return res
}

onMounted(async () => {
  await init()
})
</script>
