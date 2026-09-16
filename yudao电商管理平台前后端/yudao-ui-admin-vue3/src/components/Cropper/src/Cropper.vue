<template>
  <div ref="containerRef" :class="getClass" :style="getWrapperStyle">
    <img v-show="false" ref="imgElRef" :alt="alt" :crossorigin="crossorigin" :src="src" />
  </div>
</template>
<script lang="ts" setup>
import { CSSProperties, PropType } from 'vue'
import Cropper from 'cropperjs'
import { useDesign } from '@/hooks/web/useDesign'
import { propTypes } from '@/utils/propTypes'
import { useDebounceFn } from '@vueuse/core'

defineOptions({ name: 'Cropper' })

const props = defineProps({
  src: propTypes.string.def(''),
  alt: propTypes.string.def(''),
  circled: propTypes.bool.def(false),
  realTimePreview: propTypes.bool.def(true),
  height: propTypes.string.def('360px'),
  outputSize: propTypes.number.def(0),
  crossorigin: {
    type: String as PropType<'' | 'anonymous' | 'use-credentials' | undefined>,
    default: undefined
  },
  imageStyle: { type: Object as PropType<CSSProperties>, default: () => ({}) },
  options: { type: Object as PropType<Record<string, any>>, default: () => ({}) }
})

const emit = defineEmits(['cropend', 'ready', 'cropendError'])
const attrs = useAttrs()
const imgElRef = ref<HTMLImageElement>()
const containerRef = ref<HTMLElement>()
const cropper = shallowRef<Cropper>()
let generation = 0
let previewVersion = 0
let ready = false

const { getPrefixCls } = useDesign()
const prefixCls = getPrefixCls('cropper-image')
const debounceRealTimeCroppered = useDebounceFn(realTimeCroppered, 80)

const getClass = computed(() => {
  return [prefixCls, attrs.class]
})
const getWrapperStyle = computed((): CSSProperties => {
  return { height: `${props.height}`.replace(/px/, '') + 'px' }
})

onMounted(init)
watch(() => props.src, init, { flush: 'post' })

onUnmounted(() => {
  generation++
  ready = false
  cropper.value?.destroy()
})

async function init() {
  const imgEl = unref(imgElRef)
  const containerEl = unref(containerRef)
  if (!imgEl || !containerEl) return

  const currentGeneration = ++generation
  ready = false
  cropper.value?.destroy()
  if (!props.src) return
  const instance = new Cropper(imgEl, {
    container: containerEl,
    ...props.options
  })
  cropper.value = instance
  const canvas = instance.getCropperCanvas()
  if (canvas) {
    canvas.style.width = '100%'
    canvas.style.height = '100%'
  }

  // Wait for custom elements to be ready, then configure
  await nextTick()
  const cropperSelection = instance.getCropperSelection()
  const cropperImage = instance.getCropperImage()

  if (cropperSelection) {
    cropperSelection.initialCoverage = 0.5
    cropperSelection.aspectRatio = 1
    cropperSelection.movable = true
    cropperSelection.resizable = true
    cropperSelection.addEventListener('change', () => {
      debounceRealTimeCroppered()
    })
  }

  if (cropperImage) {
    cropperImage.addEventListener('transform', () => {
      debounceRealTimeCroppered()
    })
    try {
      // The native image lives in a shadow root; its load event does not bubble.
      await cropperImage.$ready()
      if (currentGeneration !== generation) return
      cropperImage.$resetTransform().$center('contain')
      cropperSelection?.$initSelection(true, true)
      ready = true
      emit('ready', instance)
      debounceRealTimeCroppered()
    } catch {
      if (currentGeneration === generation) emit('cropendError')
    }
  }
}

// Real-time display preview
async function realTimeCroppered() {
  if (!props.realTimePreview || !ready) return
  const version = ++previewVersion
  const currentGeneration = generation
  try {
    const result = await getCropResult()
    if (currentGeneration === generation && version === previewVersion) emit('cropend', result)
  } catch {
    if (currentGeneration === generation) emit('cropendError')
  }
}

// event: return base64 and width and height information after cropping
async function getCropResult() {
  const selection = cropper.value?.getCropperSelection()
  if (!ready || !selection || selection.width <= 0 || selection.height <= 0) {
    throw new Error('Image is not ready to crop')
  }

  const imgInfo = {
    x: selection.x,
    y: selection.y,
    width: selection.width,
    height: selection.height
  }

  let canvas = await selection.$toCanvas(
    props.outputSize ? { width: props.outputSize, height: props.outputSize } : undefined
  )
  if (props.circled) {
    canvas = getRoundedCanvas(canvas)
  }
  return { imgBase64: canvas.toDataURL('image/png'), imgInfo }
}

defineExpose({ getCropResult })

// Get a circular picture canvas
function getRoundedCanvas(sourceCanvas: HTMLCanvasElement) {
  const canvas = document.createElement('canvas')
  const context = canvas.getContext('2d')!
  const width = sourceCanvas.width
  const height = sourceCanvas.height
  canvas.width = width
  canvas.height = height
  context.imageSmoothingEnabled = true
  context.drawImage(sourceCanvas, 0, 0, width, height)
  context.globalCompositeOperation = 'destination-in'
  context.beginPath()
  context.arc(width / 2, height / 2, Math.min(width, height) / 2, 0, 2 * Math.PI, true)
  context.fill()
  return canvas
}
</script>
