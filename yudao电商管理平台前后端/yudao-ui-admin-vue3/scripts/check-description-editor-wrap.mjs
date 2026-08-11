import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const editor = await readFile(
  new URL('../src/components/Editor/src/Editor.vue', import.meta.url),
  'utf8'
)
const productDescription = await readFile(
  new URL('../src/views/mall/product/spu/form/DescriptionForm.vue', import.meta.url),
  'utf8'
)

assert.match(editor, /forceWrap:\s*propTypes\.bool\.def\(false\)/)
assert.match(editor, /class="rich-text-editor[^\"]*"/)
assert.match(editor, /width:\s*100%/)
assert.match(editor, /min-width:\s*0/)
assert.match(editor, /overflow-wrap:\s*anywhere/)
assert.match(editor, /white-space:\s*pre-wrap\s*!important/)
assert.match(editor, /\[data-slate-editor\]\s+pre \*/)
assert.match(productDescription, /<Editor\s+force-wrap/)

console.log('Description editor wrapping contract passed.')
