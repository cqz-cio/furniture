import { createRequire } from 'node:module'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
const require = createRequire(resolve(process.env.TABLEWARE_UI_DEPENDENCIES || '.', 'package.json'))
const { parse, compileScript, compileTemplate } = require('vue/compiler-sfc')
const paths = [
  'src/components/WebsiteMediaLibrary/index.vue',
  'src/views/seo/catalog/index.vue',
  'src/views/seo/operations/index.vue',
  'src/views/seo/blog/BlogBlocksEditor.vue',
  'src/views/seo/blog/BlogEditorDrawer.vue',
  'src/views/seo/blog/index.vue',
  'src/views/seo/site-config/index.vue'
]
for (const filename of paths) {
  const { descriptor, errors } = parse(readFileSync(filename, 'utf8'), { filename })
  if (errors.length) throw Error(`${filename}: ${errors}`)
  const script = compileScript(descriptor, { id: filename })
  const template = compileTemplate({
    id: filename,
    filename,
    source: descriptor.template.content,
    compilerOptions: { bindingMetadata: script.bindings }
  })
  if (template.errors.length) throw Error(`${filename}: ${template.errors}`)
  console.log(`PASS ${filename}`)
}
