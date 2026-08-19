import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const furnitureDetail = await readFile(
  new URL('../src/views/mall/product/spu/form/FurnitureDetailForm.vue', import.meta.url),
  'utf8'
)

assert.match(furnitureDetail, /finish: ''/)
assert.match(furnitureDetail, /finish: typeof config\?\.finish === 'string' \? config\.finish : ''/)
assert.match(furnitureDetail, /normalized\.finish = normalized\.finish\.trim\(\)/)
assert.match(furnitureDetail, /<el-form-item label="Finish" prop="finish">/)
assert.match(furnitureDetail, /资料未提供时可留空/)
assert.doesNotMatch(furnitureDetail, /DEFAULT_FINISH/)
assert.doesNotMatch(furnitureDetail, /\|\|\s*['"]Natural Oak['"]/)
assert.doesNotMatch(furnitureDetail, /默认为 Natural Oak/)

console.log('Finish nullable contract passed.')
