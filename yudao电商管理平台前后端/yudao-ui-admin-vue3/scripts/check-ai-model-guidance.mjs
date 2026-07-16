import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const root = new URL('../', import.meta.url)
const alertComponent = await readFile(
  new URL('src/views/ai/components/AiModelConfigurationAlert.vue', root),
  'utf8'
)

assert.match(alertComponent, /ModelApi\.getModelSimpleList\(props\.modelType\)/)
assert.match(alertComponent, /AI 模型尚未配置/)
assert.match(alertComponent, /请先在 AI 控制台配置模型和 API Key，再使用生成能力。/)
assert.match(alertComponent, /emit\('loaded', configured\.value, models\)/)

const pages = {
  chat: await readFile(new URL('src/views/ai/chat/index/index.vue', root), 'utf8'),
  write: await readFile(new URL('src/views/ai/write/index/index.vue', root), 'utf8'),
  mindmap: await readFile(new URL('src/views/ai/mindmap/index/index.vue', root), 'utf8'),
  image: await readFile(new URL('src/views/ai/image/index/index.vue', root), 'utf8')
}

for (const [name, source] of Object.entries(pages)) {
  assert.match(source, /AiModelConfigurationAlert/, `${name} must render model configuration guidance`)
}
for (const name of ['chat', 'write', 'mindmap']) {
  assert.match(
    pages[name],
    /if \(!modelConfigured\.value\)/,
    `${name} must block generation while no model is configured`
  )
  assert.match(
    pages[name],
    /请先配置 AI 模型和 API Key/,
    `${name} must explain how to enable generation`
  )
}
assert.match(pages.image, /models\.value = loadedModels/)
assert.doesNotMatch(pages.image, /ModelApi\.getModelSimpleList/)

console.log('AI model configuration guidance contract passed.')
