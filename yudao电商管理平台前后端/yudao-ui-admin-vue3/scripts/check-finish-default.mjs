import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const furnitureDetail = await readFile(
  new URL('../src/views/mall/product/spu/form/FurnitureDetailForm.vue', import.meta.url),
  'utf8'
)

assert.match(furnitureDetail, /const DEFAULT_FINISH = 'Natural Oak'/)
assert.match(furnitureDetail, /finish: DEFAULT_FINISH/)
assert.match(furnitureDetail, /finish: normalizeFinish\(config\?\.finish\)/)
assert.match(furnitureDetail, /normalized\.finish = normalized\.finish\.trim\(\) \|\| DEFAULT_FINISH/)
assert.match(furnitureDetail, /<el-form-item label="Finish" prop="finish">/)
assert.match(furnitureDetail, /finish: \[\{ validator: validateText\('Finish'\)/)
assert.match(furnitureDetail, /Finish 未明确提供时默认为 Natural Oak/)
assert.doesNotMatch(furnitureDetail, /v-if="!isSimplifiedSeating" label="Finish"/)
assert.doesNotMatch(furnitureDetail, /normalized\.finish = ''/)

console.log('Finish default contract passed.')
