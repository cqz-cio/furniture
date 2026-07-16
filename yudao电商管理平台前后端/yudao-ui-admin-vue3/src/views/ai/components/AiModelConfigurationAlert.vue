<template>
  <el-alert
    v-if="loaded && !configured"
    title="AI 模型尚未配置"
    description="请先在 AI 控制台配置模型和 API Key，再使用生成能力。"
    type="warning"
    show-icon
    :closable="false"
  />
</template>

<script setup lang="ts">
import { ModelApi, ModelVO } from '@/api/ai/model/model'

const props = defineProps<{
  modelType: number
}>()
const emit = defineEmits<{
  loaded: [configured: boolean, models: ModelVO[]]
}>()

const loaded = ref(false)
const configured = ref(false)

onMounted(async () => {
  let models: ModelVO[] = []
  try {
    models = await ModelApi.getModelSimpleList(props.modelType)
    configured.value = models.length > 0
  } finally {
    loaded.value = true
    emit('loaded', configured.value, models)
  }
})
</script>
