<template>
  <div @click.stop>
    <Dialog
      v-model="dialogVisible"
      :canFullscreen="false"
      :title="t('cropper.modalTitle')"
      maxHeight="380px"
      width="800px"
      @opened="dialogReady = true"
    >
      <div :class="prefixCls">
        <div :class="`${prefixCls}-left`">
          <div :class="`${prefixCls}-cropper`">
            <CropperImage
              v-if="src && dialogVisible && dialogReady"
              ref="cropperImageRef"
              :key="src"
              :circled="circled"
              :src="src"
              :outputSize="512"
              crossorigin="anonymous"
              height="300px"
              @cropend="handleCropend"
              @ready="handleReady"
              @cropend-error="handleCropError"
            />
          </div>

          <div :class="`${prefixCls}-toolbar`">
            <el-upload
              :disabled="loading || exporting"
              :beforeUpload="handleBeforeUpload"
              :fileList="[]"
              accept="image/*"
            >
              <el-tooltip :content="t('cropper.selectImage')" placement="bottom">
                <XButton preIcon="ant-design:upload-outlined" type="primary" />
              </el-tooltip>
            </el-upload>
            <el-space>
              <el-tooltip :content="t('cropper.btn_reset')" placement="bottom">
                <XButton
                  :disabled="!cropper || loading || exporting"
                  preIcon="ant-design:reload-outlined"
                  size="small"
                  type="primary"
                  @click="handlerToolbar('reset')"
                />
              </el-tooltip>
              <el-tooltip :content="t('cropper.btn_rotate_left')" placement="bottom">
                <XButton
                  :disabled="!cropper || loading || exporting"
                  preIcon="ant-design:rotate-left-outlined"
                  size="small"
                  type="primary"
                  @click="handlerToolbar('rotate', -45)"
                />
              </el-tooltip>
              <el-tooltip :content="t('cropper.btn_rotate_right')" placement="bottom">
                <XButton
                  :disabled="!cropper || loading || exporting"
                  preIcon="ant-design:rotate-right-outlined"
                  size="small"
                  type="primary"
                  @click="handlerToolbar('rotate', 45)"
                />
              </el-tooltip>
              <el-tooltip :content="t('cropper.btn_scale_x')" placement="bottom">
                <XButton
                  :disabled="!cropper || loading || exporting"
                  preIcon="vaadin:arrows-long-h"
                  size="small"
                  type="primary"
                  @click="handlerToolbar('scaleX')"
                />
              </el-tooltip>
              <el-tooltip :content="t('cropper.btn_scale_y')" placement="bottom">
                <XButton
                  :disabled="!cropper || loading || exporting"
                  preIcon="vaadin:arrows-long-v"
                  size="small"
                  type="primary"
                  @click="handlerToolbar('scaleY')"
                />
              </el-tooltip>
              <el-tooltip :content="t('cropper.btn_zoom_in')" placement="bottom">
                <XButton
                  :disabled="!cropper || loading || exporting"
                  preIcon="ant-design:zoom-in-outlined"
                  size="small"
                  type="primary"
                  @click="handlerToolbar('zoom', 0.1)"
                />
              </el-tooltip>
              <el-tooltip :content="t('cropper.btn_zoom_out')" placement="bottom">
                <XButton
                  :disabled="!cropper || loading || exporting"
                  preIcon="ant-design:zoom-out-outlined"
                  size="small"
                  type="primary"
                  @click="handlerToolbar('zoom', -0.1)"
                />
              </el-tooltip>
            </el-space>
          </div>
        </div>
        <div :class="`${prefixCls}-right`">
          <div :class="`${prefixCls}-preview`">
            <img v-if="previewSource" :alt="t('cropper.preview')" :src="previewSource" />
          </div>
          <template v-if="previewSource">
            <div :class="`${prefixCls}-group`">
              <el-avatar :src="previewSource" size="large" />
              <el-avatar :size="48" :src="previewSource" />
              <el-avatar :size="64" :src="previewSource" />
              <el-avatar :size="80" :src="previewSource" />
            </div>
          </template>
        </div>
      </div>
      <template #footer>
        <el-button
          type="primary"
          :disabled="!previewSource || !cropper"
          :loading="loading || exporting"
          @click="handleOk"
        >{{ t('cropper.okText') }}</el-button
        >
      </template>
    </Dialog>
  </div>
</template>
<script lang="ts" setup>
import { useDesign } from '@/hooks/web/useDesign'
import { dataURLtoBlob } from '@/utils/filt'
import { useI18n } from 'vue-i18n'
import type { CropendResult, Cropper } from './types'
import { propTypes } from '@/utils/propTypes'
import { CropperImage } from '@/components/Cropper'

defineOptions({ name: 'CopperModal' })

const props = defineProps({
  srcValue: propTypes.string.def(''),
  loading: propTypes.bool.def(false),
  circled: propTypes.bool.def(true)
})
const emit = defineEmits(['uploadSuccess'])
const { t } = useI18n()
const { getPrefixCls } = useDesign()
const prefixCls = getPrefixCls('cropper-am')

const src = ref(props.srcValue)
const previewSource = ref('')
const cropper = shallowRef<Cropper>()
const cropperImageRef = ref<InstanceType<typeof CropperImage>>()
const exporting = ref(false)
const message = useMessage()
const dialogVisible = ref(false)
const dialogReady = ref(false)
let filename = ''
let readVersion = 0

// Block upload
function handleBeforeUpload(file: File) {
  if (props.loading || exporting.value) return false
  if (!file.type.startsWith('image/')) {
    message.error('请选择有效的图片文件')
    return false
  }
  const version = ++readVersion
  const reader = new FileReader()
  src.value = ''
  previewSource.value = ''
  cropper.value = undefined
  reader.onload = function (e) {
    if (version !== readVersion || !dialogVisible.value) return
    src.value = (e.target?.result as string) ?? ''
    filename = file.name
  }
  reader.onerror = () => {
    if (version === readVersion) handleCropError()
  }
  reader.readAsDataURL(file)
  return false
}

function handleCropError() {
  previewSource.value = ''
  cropper.value = undefined
  message.error('图片读取或裁剪失败，请重新选择图片')
}

function handleCropend({ imgBase64 }: CropendResult) {
  previewSource.value = imgBase64
}

function handleReady(cropperInstance: Cropper) {
  cropper.value = cropperInstance
}

function handlerToolbar(event: string, arg?: number) {
  if (!cropper.value || props.loading || exporting.value) return
  const cropperImage = cropper.value.getCropperImage()
  const cropperSelection = cropper.value.getCropperSelection()

  if (event === 'reset') {
    cropperImage?.$resetTransform().$center('contain')
    cropperSelection?.$initSelection(true, true)
  } else if (event === 'rotate') {
    cropperImage?.$rotate(`${arg}deg`)
  } else if (event === 'scaleX') {
    cropperImage?.$scale(-1, 1)
  } else if (event === 'scaleY') {
    cropperImage?.$scale(1, -1)
  } else if (event === 'zoom') {
    cropperImage?.$zoom(arg!)
  }
}

async function handleOk() {
  if (!cropper.value || !previewSource.value || props.loading || exporting.value) return
  exporting.value = true
  try {
    // Export the current selection, including edits made before the preview debounce runs.
    const result = await cropperImageRef.value!.getCropResult()
    if (!dialogVisible.value) return
    const blob = dataURLtoBlob(result.imgBase64)
    emit('uploadSuccess', { source: result.imgBase64, data: blob, filename })
  } catch {
    handleCropError()
  } finally {
    exporting.value = false
  }
}

function openModal() {
  if (dialogVisible.value || props.loading) return
  dialogReady.value = false
  readVersion++
  src.value = props.srcValue
  previewSource.value = ''
  cropper.value = undefined
  filename = 'avatar.png'
  dialogVisible.value = true
}

watch(dialogVisible, (visible) => {
  if (!visible) {
    dialogReady.value = false
    readVersion++
    cropper.value = undefined
    previewSource.value = ''
  }
})

function closeModal() {
  dialogVisible.value = false
}

defineExpose({ openModal, closeModal })
</script>
<style lang="scss">
$prefix-cls: #{$namespace}-cropper-am;

.#{$prefix-cls} {
  display: flex;

  &-left,
  &-right {
    height: 340px;
  }

  &-left {
    width: 55%;
  }

  &-right {
    width: 45%;
  }

  &-cropper {
    height: 300px;
    background: #eee;
    background-image:
      linear-gradient(
        45deg,
        rgb(0 0 0 / 25%) 25%,
        transparent 0,
        transparent 75%,
        rgb(0 0 0 / 25%) 0
      ),
      linear-gradient(
        45deg,
        rgb(0 0 0 / 25%) 25%,
        transparent 0,
        transparent 75%,
        rgb(0 0 0 / 25%) 0
      );
    background-position:
      0 0,
      12px 12px;
    background-size: 24px 24px;
  }

  &-toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: 10px;
  }

  &-preview {
    width: 220px;
    height: 220px;
    margin: 0 auto;
    overflow: hidden;
    border: 1px solid;
    border-radius: 50%;

    img {
      width: 100%;
      height: 100%;
    }
  }

  &-group {
    display: flex;
    padding-top: 8px;
    margin-top: 8px;
    border-top: 1px solid;
    justify-content: space-around;
    align-items: center;
  }
}
</style>
