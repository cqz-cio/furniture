<template>
  <div class="change-avatar">
    <CropperAvatar
      ref="cropperRef"
      :btnProps="{ preIcon: 'ant-design:cloud-upload-outlined' }"
      :showBtn="false"
      :value="img"
      :loading="uploading"
      width="120px"
      @change="handelUpload"
    />
  </div>
</template>
<script lang="ts" setup>
import { propTypes } from '@/utils/propTypes'
import { updateUserProfile } from '@/api/system/user/profile'
import { CropperAvatar } from '@/components/Cropper'
import { resolveAvatarUrl } from '@/utils/avatar'
import { useUserStore } from '@/store/modules/user'
import { useUpload } from '@/components/UploadFile/src/useUpload'
import { UploadRequestOptions } from 'element-plus/es/components/upload/src/upload'

defineOptions({ name: 'UserAvatar' })

defineProps({
  img: propTypes.string.def('')
})

const userStore = useUserStore()

const cropperRef = ref()
const uploading = ref(false)
const message = useMessage()
const { t } = useI18n()
const handelUpload = async ({ data }) => {
  if (uploading.value) return
  uploading.value = true
  try {
    const { httpRequest } = useUpload()
    const uploaded = (
      (await httpRequest({
        file: new File([data], 'avatar.png', { type: 'image/png' }),
        filename: 'avatar.png'
      } as UploadRequestOptions)) as unknown as { data: string }
    ).data
    const avatar = resolveAvatarUrl(uploaded)
    await updateUserProfile({ avatar })

    // 关闭弹窗，并更新 userStore
    await userStore.setUserAvatarAction(avatar)
    cropperRef.value.close()
    message.success(t('cropper.uploadSuccess'))
  } catch {
    message.error('头像上传失败，请重试')
  } finally {
    uploading.value = false
  }
}
</script>

<style lang="scss" scoped>
.change-avatar {
  img {
    display: block;
    margin-bottom: 15px;
    border-radius: 50%;
  }
}
</style>
