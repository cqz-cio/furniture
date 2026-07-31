<template>
  <div v-if="loaded && !configured" class="ai-model-configuration-alert">
    <el-alert
      :closable="false"
      :description="
        unavailable
          ? '后台 AI 模块未启用或服务暂时不可达。页面已暂停相关请求，恢复服务后可重新检测。'
          : '请先在 AI 控制台配置模型和 API Key，再使用生成能力。'
      "
      show-icon
      :title="unavailable ? 'AI 服务当前不可用' : 'AI 模型尚未配置'"
      :type="unavailable ? 'error' : 'warning'"
    />
    <el-button class="ai-model-configuration-alert__retry" link type="primary" @click="loadModels">
      重新检测
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { ModelApi, ModelVO } from '@/api/ai/model/model'

const props = defineProps<{
  modelType: number
}>()
const emit = defineEmits<{
  loaded: [configured: boolean, models: ModelVO[], unavailable: boolean]
}>()

const loaded = ref(false)
const configured = ref(false)
const unavailable = ref(false)

const loadModels = async () => {
  loaded.value = false
  configured.value = false
  unavailable.value = false
  let models: ModelVO[] = []
  try {
    models = await ModelApi.getModelSimpleList(props.modelType, { hideErrorMessage: true })
    configured.value = models.length > 0
  } catch {
    unavailable.value = true
  } finally {
    loaded.value = true
    emit('loaded', configured.value, models, unavailable.value)
  }
}

onMounted(loadModels)
</script>

<style scoped>
.ai-model-configuration-alert {
  position: relative;
}

.ai-model-configuration-alert :deep(.el-alert__content) {
  padding-right: 88px;
}

.ai-model-configuration-alert__retry {
  position: absolute;
  top: 50%;
  right: 14px;
  transform: translateY(-50%);
}
</style>
