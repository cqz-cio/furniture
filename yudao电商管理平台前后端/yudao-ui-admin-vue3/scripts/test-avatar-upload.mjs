// Run with AVATAR_TEST_IMAGE and (if not installed locally) PLAYWRIGHT_MODULE.
// Exercises the real Vue components and Cropper.js; only server APIs are mocked.
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import path from 'node:path'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const imagePath = process.env.AVATAR_TEST_IMAGE
assert.ok(imagePath, 'Set AVATAR_TEST_IMAGE to a local image fixture')
const filePath = '/admin-api/infra/file/4/get/20260916/avatar.png'
let savedImage = await fs.readFile(imagePath)
const artifactDir = process.env.AVATAR_TEST_OUTPUT || path.join(root, 'work/avatar-test')
await fs.mkdir(artifactDir, { recursive: true })

const state = `
import { reactive } from 'vue';
export const state = reactive({ avatar: '', fail: false, uploads: 0, saved: 0, messages: [] });
window.testState = state;
export const useUserStore = () => ({ getUser: state, setUserAvatarAction: async avatar => { state.avatar = avatar } });
export const useMessage = () => ({ success: text => state.messages.push({ type: 'success', text }), error: text => state.messages.push({ type: 'error', text }) });
export const useDesign = () => ({ getPrefixCls: name => 'v-' + name });
export const useUpload = () => ({ httpRequest: async ({file}) => {
  state.uploads++; window.uploadedFile = file;
  await new Promise(resolve => setTimeout(resolve, 250));
  if (state.fail) throw new Error('Simulated upload failure');
  await fetch('/@avatar-upload', { method: 'POST', body: file });
  return { data: 'http://127.0.0.1:48080${filePath}' };
} });
export const updateUserProfile = async ({avatar}) => { state.saved++; window.savedAvatar = avatar; };
`
const aliases = [
  '@/hooks/web/useDesign',
  '@/components/UploadFile/src/useUpload',
  '@/api/system/user/profile',
  '@/store/modules/user',
  '/@avatar-state'
]
const server = await createServer({
  root,
  configFile: false,
  envFile: false,
  define: { 'import.meta.env.VITE_BASE_URL': '""' },
  logLevel: 'error',
  cacheDir: path.join(artifactDir, 'vite-cache'),
  optimizeDeps: {
    include: ['vue', 'element-plus', 'vue-i18n', '@vueuse/core', 'vue-types', 'cropperjs'],
    noDiscovery: true,
    force: true
  },
  server: { host: '127.0.0.1', port: 0 },
  resolve: {
    dedupe: ['vue'],
    alias: [
      ...aliases.map((find) => ({ find, replacement: '\0avatar-state' })),
      { find: '@', replacement: path.join(root, 'src') }
    ]
  },
  css: {
    preprocessorOptions: {
      scss: {
        additionalData: (source, filename) =>
          filename.includes('.vue') ? '$namespace: v; $elNamespace: el;\n' + source : source
      }
    }
  },
  plugins: [
    {
      name: 'avatar-test-fixtures',
      enforce: 'pre',
      resolveId(id) {
        if (id === '\0avatar-state') return id
        if (
          aliases.includes(id) ||
          aliases.some(
            (name) =>
              name.startsWith('@/') &&
              id === path.join(root, 'src', name.slice(2)).replaceAll('\\', '/')
          )
        )
          return '\0avatar-state'
        if (id === '/@avatar-entry') return '\0avatar-entry'
      },
      load(id) {
        if (id === '\0avatar-state') return state
        if (id === '\0avatar-entry')
          return `
import { createApp, h } from 'vue';
import ElementPlus, { ElButton } from 'element-plus';
import 'element-plus/dist/index.css';
import { createI18n } from 'vue-i18n';
import UserAvatar from '/src/views/Profile/components/UserAvatar.vue';
import Dialog from '/src/components/Dialog/src/Dialog.vue';
import { state } from '/@avatar-state';
const app = createApp({ render: () => h(UserAvatar, { img: state.avatar }) });
app.use(ElementPlus).use(createI18n({ legacy: false, locale: 'zh', messages: { zh: { cropper: { modalTitle: '头像上传', okText: '确认并上传', preview: '头像预览', uploadSuccess: '上传成功' } } } }));
app.component('Dialog', Dialog).component('Icon', { render: () => h('span') });
app.component('XButton', { props: ['preIcon'], setup: (props, {attrs}) => () => h(ElButton, {...attrs, 'aria-label': props.preIcon}, () => props.preIcon.split(':')[1]) });
app.mount('#app');`
      },
      configureServer(server) {
        server.middlewares.use((req, res, next) => {
          if (req.url === '/@avatar-upload' && req.method === 'POST') {
            const chunks = []
            req.on('data', (chunk) => chunks.push(chunk))
            req.on('end', () => {
              savedImage = Buffer.concat(chunks)
              res.end('ok')
            })
            return
          }
          if (req.url === filePath) {
            res.setHeader('Content-Type', 'image/png')
            res.end(savedImage)
            return
          }
          if (req.url !== '/') return next()
          res.setHeader('Content-Type', 'text/html')
          res.end(
            '<html><body><div id="app"></div><script type="module" src="/@avatar-entry"></script></body></html>'
          )
        })
      }
    },
    vue(),
    AutoImport({
      imports: ['vue', { 'vue-i18n': ['useI18n'], '/@avatar-state': ['useMessage'] }],
      dts: false
    })
  ]
})
let browser
let page
try {
  await server.listen()
  browser = await chromium.launch({
    headless: true,
    executablePath: process.env.AVATAR_TEST_BROWSER
  })
  page = await browser.newPage({ viewport: { width: 1440, height: 960 } })
  page.setDefaultTimeout(12000)
  const errors = []
  page.on('pageerror', (error) => errors.push(error.stack || error.message))
  await page.goto(`http://127.0.0.1:${server.httpServer.address().port}`)
  await page.getByRole('button', { name: '编辑头像' }).click()
  const confirm = page.getByRole('button', { name: '确认并上传' })
  assert.equal(await confirm.isDisabled(), true, 'Empty selection cannot upload')
  await page.locator('input[type=file]').setInputFiles(imagePath)
  await page.waitForFunction(
    () => document.querySelector('.v-cropper-am-preview img')?.naturalWidth === 512
  )
  const geometry = await page.evaluate(() => {
    const image = document.querySelector('cropper-image')
    const selection = document.querySelector('cropper-selection')
    return {
      height: document.querySelector('cropper-canvas').clientHeight,
      imageHeight: image.getBoundingClientRect().height,
      width: selection.width,
      heightSelection: selection.height
    }
  })
  assert.equal(geometry.height, 300)
  assert.ok(geometry.imageHeight >= 299)
  assert.equal(geometry.width, geometry.heightSelection)
  const initial = await page.locator('.v-cropper-am-preview img').getAttribute('src')
  const cropDifference = await page.evaluate(() => {
    const image = document.querySelector('cropper-image')
    const selection = document.querySelector('cropper-selection').getBoundingClientRect()
    const bounds = image.getBoundingClientRect()
    const source = image.shadowRoot.querySelector('img')
    const expected = document.createElement('canvas')
    expected.width = expected.height = 512
    const ctx = expected.getContext('2d')
    ctx.drawImage(
      source,
      ((selection.x - bounds.x) * source.naturalWidth) / bounds.width,
      ((selection.y - bounds.y) * source.naturalHeight) / bounds.height,
      (selection.width * source.naturalWidth) / bounds.width,
      (selection.height * source.naturalHeight) / bounds.height,
      0,
      0,
      512,
      512
    )
    const actual = document.createElement('canvas')
    actual.width = actual.height = 512
    const out = actual.getContext('2d')
    out.drawImage(document.querySelector('.v-cropper-am-preview img'), 0, 0)
    const a = ctx.getImageData(128, 128, 256, 256).data
    const b = out.getImageData(128, 128, 256, 256).data
    return a.reduce((total, value, index) => total + Math.abs(value - b[index]), 0) / a.length
  })
  assert.ok(cropDifference < 3, `Preview must match the selected image area: ${cropDifference}`)
  const transform = () => page.locator('cropper-image').evaluate((el) => el.$getTransform())
  const first = await transform()
  await page.getByRole('button', { name: 'vaadin:arrows-long-h' }).click({ force: true })
  await page.getByRole('button', { name: 'vaadin:arrows-long-h' }).click({ force: true })
  assert.deepEqual(await transform(), first, 'Two horizontal flips restore the image')
  await page.getByRole('button', { name: 'ant-design:zoom-in-outlined' }).click({ force: true })
  assert.notDeepEqual(await transform(), first)
  await page
    .getByRole('button', { name: 'ant-design:rotate-right-outlined' })
    .click({ force: true })
  await page.getByRole('button', { name: 'ant-design:reload-outlined' }).click({ force: true })
  const reset = await transform()
  first.forEach((value, index) => assert.ok(Math.abs(value - reset[index]) < 0.001))
  await page.waitForFunction(
    (expected) => document.querySelector('.v-cropper-am-preview img')?.src === expected,
    initial
  )
  await page.screenshot({ path: path.join(artifactDir, 'avatar-editor.png') })
  await page.evaluate(() => {
    window.testState.fail = true
  })
  await confirm.click()
  await page.waitForFunction(() => window.testState.messages.some((m) => m.type === 'error'))
  assert.equal(
    await page.evaluate(() => window.testState.messages.filter((m) => m.type === 'success').length),
    0
  )
  assert.equal(await page.evaluate(() => window.testState.avatar), '')
  assert.equal(await confirm.isVisible(), true)
  await page.evaluate(() => {
    window.testState.fail = false
  })
  await page.getByRole('button', { name: 'ant-design:zoom-in-outlined' }).click({ force: true })
  await confirm.click()
  await page.waitForFunction(() => window.testState.saved === 1 && window.testState.avatar)
  assert.equal(
    await page.evaluate(() => window.savedAvatar),
    `http://127.0.0.1:${server.httpServer.address().port}${filePath}`,
    'Save the reachable public URL, never backend loopback'
  )
  const fileInfo = await page.evaluate(async () => {
    const file = window.uploadedFile
    const bitmap = await createImageBitmap(file)
    const canvas = document.createElement('canvas')
    canvas.width = canvas.height = 512
    const ctx = canvas.getContext('2d')
    ctx.drawImage(bitmap, 0, 0)
    return {
      name: file.name,
      type: file.type,
      width: bitmap.width,
      height: bitmap.height,
      alpha: ctx.getImageData(256, 256, 1, 1).data[3],
      size: file.size,
      data: canvas.toDataURL()
    }
  })
  assert.equal(fileInfo.name, 'avatar.png')
  assert.equal(fileInfo.type, 'image/png')
  assert.equal(fileInfo.width, 512)
  assert.equal(fileInfo.height, 512)
  assert.equal(fileInfo.alpha, 255, 'Saved avatar contains image pixels')
  assert.notEqual(fileInfo.data, initial, 'Immediate save captures latest zoom')
  await page.locator('.el-overlay').waitFor({ state: 'hidden' })
  await page.evaluate((path) => {
    window.testState.avatar = 'http://127.0.0.1:48080' + path
  }, filePath)
  await page.waitForFunction(() => document.querySelector('.img-lg img')?.naturalWidth === 512)
  await page.getByRole('button', { name: '编辑头像' }).click()
  await page.waitForFunction(
    () => document.querySelector('.v-cropper-am-preview img')?.naturalWidth === 512
  )
  // Replacing an existing avatar and selecting the same file again both reinitialize.
  await page.locator('input[type=file]').setInputFiles(imagePath)
  await page.waitForFunction(
    (expected) => document.querySelector('.v-cropper-am-preview img')?.src === expected,
    initial
  )
  await page.locator('input[type=file]').setInputFiles({
    name: 'broken.jpg',
    mimeType: 'image/jpeg',
    buffer: Buffer.from('invalid image')
  })
  await page.waitForFunction(() => !document.querySelector('.v-cropper-am-preview img'))
  assert.equal(await confirm.isDisabled(), true)
  await page.locator('input[type=file]').setInputFiles(imagePath)
  await page.waitForFunction(
    (expected) => document.querySelector('.v-cropper-am-preview img')?.src === expected,
    initial
  )
  assert.deepEqual(errors, [])
  console.log(
    JSON.stringify(
      {
        status: 'passed',
        geometry,
        file: { ...fileInfo, data: undefined },
        scenarios: [
          'empty',
          'load',
          'preview',
          'flip twice',
          'zoom',
          'rotate/reset',
          'failure/retry',
          'save latest',
          'reopen',
          'replace',
          'invalid/recover'
        ]
      },
      null,
      2
    )
  )
} catch (error) {
  await page?.screenshot({ path: path.join(artifactDir, 'failure.png') })
  console.error(
    JSON.stringify(
      await page?.evaluate(() => ({
        state: window.testState,
        text: document.body.innerText,
        geometry: Object.fromEntries(
          ['cropper-canvas', 'cropper-image', 'cropper-selection'].map((tag) => {
            const el = document.querySelector(tag)
            return [
              tag,
              el
                ? {
                    bounds: el.getBoundingClientRect().toJSON(),
                    x: el.x,
                    y: el.y,
                    matrix: el.$getTransform?.(),
                    style: el.getAttribute('style')
                  }
                : null
            ]
          })
        )
      })),
      null,
      2
    )
  )
  throw error
} finally {
  await browser?.close()
  await server.close()
}
