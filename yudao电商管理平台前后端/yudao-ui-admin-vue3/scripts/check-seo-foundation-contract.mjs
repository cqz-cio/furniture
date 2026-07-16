import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'

const readRequired = (relativePath) => {
  const url = new URL(relativePath, import.meta.url)
  assert.ok(existsSync(url), `missing SEO foundation file: ${relativePath}`)
  return readFileSync(url, 'utf8')
}

const escapeRegExp = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

const extractBalancedBlock = (source, signature, label) => {
  const match = source.match(signature)
  assert.ok(match, `missing ${label}`)
  const start = match.index + match[0].length
  const open = source.indexOf('{', start)
  assert.notEqual(open, -1, `missing opening brace for ${label}`)
  let depth = 0
  for (let index = open; index < source.length; index += 1) {
    if (source[index] === '{') depth += 1
    if (source[index] === '}') {
      depth -= 1
      if (depth === 0) return source.slice(open + 1, index)
    }
  }
  assert.fail(`missing closing brace for ${label}`)
}

const extractInterface = (source, name) =>
  extractBalancedBlock(source, new RegExp(`export interface ${name}\\b[^\\{]*`), `${name} interface`)

const extractFunction = (source, name) =>
  extractBalancedBlock(source, new RegExp(`const ${name}\\s*=\\s*(?:async\\s*)?\\([^)]*\\)\\s*=>\\s*`), `${name} function`)

const extractOpeningTag = (source, tag, requiredToken) => {
  const pattern = new RegExp(`<${tag}\\b[^>]*${escapeRegExp(requiredToken)}[^>]*>`, 'm')
  const match = source.match(pattern)
  assert.ok(match, `missing <${tag}> containing ${requiredToken}`)
  return match[0]
}

const assertRequiredFields = (block, interfaceName, fields) => {
  for (const field of fields) {
    assert.match(
      block,
      new RegExp(`(?:^|\\n)\\s*${field}:\\s*[^\\n]+`),
      `${interfaceName}.${field} must be required`
    )
  }
}

const assertExactPermission = (source, clickHandler, permission) => {
  const tag = extractOpeningTag(source, 'el-button', clickHandler)
  assert.ok(
    tag.includes(`v-hasPermi="['${permission}']"`),
    `${clickHandler} must use exact permission ${permission}`
  )
}

const siteApi = readRequired('../src/api/seo/siteConfig/index.ts')
const metadataApi = readRequired('../src/api/seo/metadata/index.ts')
const sitePage = readRequired('../src/views/seo/site-config/index.vue')
const metadataPage = readRequired('../src/views/seo/metadata/index.vue')
const metadataForm = readRequired('../src/views/seo/metadata/MetadataForm.vue')
const packageJson = JSON.parse(readRequired('../package.json'))

for (const path of ['/seo/site-config/get', '/seo/site-config/save']) {
  assert.ok(siteApi.includes(path), `missing site-config API path: ${path}`)
}
for (const path of [
  '/seo/metadata/page',
  '/seo/metadata/get',
  '/seo/metadata/create',
  '/seo/metadata/update',
  '/seo/metadata/delete',
  '/seo/metadata/publish'
]) {
  assert.ok(metadataApi.includes(path), `missing metadata API path: ${path}`)
}

const siteSaveFields = [
  'siteId',
  'siteName',
  'siteUrl',
  'defaultTitleSuffix',
  'defaultDescription',
  'defaultRobots',
  'defaultOgImage',
  'defaultLocale'
]
const metadataSaveFields = [
  'siteId',
  'entityType',
  'entityId',
  'locale',
  'seoTitle',
  'metaDescription',
  'focusKeyphrase',
  'relatedKeyphrases',
  'canonicalUrl',
  'robotsIndex',
  'robotsFollow',
  'ogTitle',
  'ogDescription',
  'ogImage',
  'schemaType'
]

const siteSave = extractInterface(siteApi, 'SeoSiteConfigSaveReqVO')
const siteResp = extractInterface(siteApi, 'SeoSiteConfigRespVO')
assertRequiredFields(siteSave, 'SeoSiteConfigSaveReqVO', siteSaveFields)
assertRequiredFields(siteResp, 'SeoSiteConfigRespVO', ['id', 'createTime', 'updateTime'])
assert.match(siteApi, /request\.get<SeoSiteConfigRespVO \| null>\(\{[\s\S]*?params: \{ siteId \}[\s\S]*?\}\)/, 'site GET must be nullable and pass siteId in params')
assert.match(siteApi, /request\.put<boolean>\(\{ url: '\/seo\/site-config\/save', data \}\)/, 'site save must use PUT data')

assert.ok(metadataApi.includes("export type SeoEntityType = 'PRODUCT' | 'CATEGORY' | 'ARTICLE' | 'PAGE'"), 'entity type union must be exact')
assert.ok(metadataApi.includes("export type SeoPublishStatus = 'DRAFT' | 'PUBLISHED'"), 'publish status union must be exact')
const createReq = extractInterface(metadataApi, 'SeoMetadataCreateReqVO')
const updateReq = extractInterface(metadataApi, 'SeoMetadataUpdateReqVO')
const metadataResp = extractInterface(metadataApi, 'SeoMetadataRespVO')
const pageReq = extractInterface(metadataApi, 'SeoMetadataPageReqVO')
assertRequiredFields(createReq, 'SeoMetadataCreateReqVO', metadataSaveFields)
assert.match(createReq, /(?:^|\n)\s*relatedKeyphrases:\s*string\[\]/, 'Create.relatedKeyphrases must be string[]')
assert.doesNotMatch(createReq, /(?:^|\n)\s*(?:id|version|tenantId)\??\s*:/, 'Create must exclude id, version, and tenantId')
assert.match(metadataApi, /export interface SeoMetadataUpdateReqVO extends SeoMetadataCreateReqVO/, 'Update must extend Create')
assertRequiredFields(updateReq, 'SeoMetadataUpdateReqVO', ['id', 'version'])
assert.match(metadataApi, /export interface SeoMetadataRespVO extends SeoMetadataUpdateReqVO/, 'Resp must extend Update')
assertRequiredFields(metadataResp, 'SeoMetadataRespVO', ['publishStatus'])
assertRequiredFields(pageReq, 'SeoMetadataPageReqVO', ['pageNo', 'pageSize'])
for (const field of ['siteId', 'entityType', 'entityId', 'locale', 'publishStatus', 'keyword']) {
  assert.match(pageReq, new RegExp(`(?:^|\\n)\\s*${field}\\?:`), `PageReq.${field} must be optional`)
}
assert.match(metadataApi, /request\.get<SeoMetadataPageResult>\(\{[^}]*params \}\)/, 'page query must use params')
assert.match(metadataApi, /request\.post<number>\(\{ url: '\/seo\/metadata\/create', data \}\)/, 'create must use data')
assert.match(metadataApi, /request\.put<boolean>\(\{ url: '\/seo\/metadata\/update', data \}\)/, 'update must use data')
assert.match(metadataApi, /publishSeoMetadata\s*=\s*async\s*\(id:\s*number,\s*version:\s*number\)/, 'publish must require id and version')
assert.match(metadataApi, /request\.put<boolean>\(\{ url: '\/seo\/metadata\/publish', params: \{ id, version \} \}\)/, 'publish must send id and version as params')

assert.ok(sitePage.includes("name: 'SeoSiteConfig'"), 'missing SeoSiteConfig component name')
assert.ok(metadataPage.includes("name: 'SeoMetadata'"), 'missing SeoMetadata component name')
for (const token of ['内容类型', '发布状态']) {
  assert.ok(metadataPage.includes(token), `missing metadata list label: ${token}`)
}
for (const token of ['焦点关键词', 'Canonical URL', '保存后将影响线上版本']) {
  assert.ok(metadataForm.includes(token), `missing metadata form label: ${token}`)
}

assertExactPermission(sitePage, '@click="submitForm"', 'seo:site-config:update')
assertExactPermission(metadataPage, '@click="handleQuery"', 'seo:metadata:query')
assertExactPermission(metadataPage, "@click=\"openForm('create')\"", 'seo:metadata:create')
assertExactPermission(metadataPage, "@click=\"openForm('update', scope.row.id)\"", 'seo:metadata:update')
assertExactPermission(metadataPage, '@click="handleDelete(scope.row.id)"', 'seo:metadata:delete')
assertExactPermission(metadataPage, '@click="handlePublish(scope.row)"', 'seo:metadata:publish')

for (const field of siteSaveFields) {
  assert.ok(sitePage.includes(`v-model="formData.${field}"`), `site form must bind save field ${field}`)
}
for (const field of metadataSaveFields) {
  assert.ok(metadataForm.includes(`v-model="formData.${field}"`), `metadata form must bind save field ${field}`)
}
const keyphraseSelect = extractOpeningTag(metadataForm, 'el-select', 'v-model="formData.relatedKeyphrases"')
assert.match(keyphraseSelect, /(?:^|\s)multiple(?:\s|>)/, 'relatedKeyphrases select must be multiple')
assert.match(keyphraseSelect, /(?:^|\s)allow-create(?:\s|>)/, 'relatedKeyphrases select must allow creating tags')

for (const field of ['siteId', 'entityType', 'entityId', 'locale']) {
  const identityControl = extractOpeningTag(metadataForm, field === 'entityType' ? 'el-select' : field === 'locale' ? 'el-input' : 'el-input-number', `v-model="formData.${field}"`)
  assert.ok(identityControl.includes(':disabled="isEdit"'), `${field} must be disabled while editing`)
}

const submitForm = extractFunction(metadataForm, 'submitForm')
for (const field of metadataSaveFields) {
  assert.match(
    submitForm,
    new RegExp(`${field}:\\s*formData\\.value\\.${field}`),
    `metadata save payload must include ${field}`
  )
}
assert.match(submitForm, /version:\s*formData\.value\.version!/, 'update must retain the GET-loaded version')
assert.match(submitForm, /await updateSeoMetadata\(data\)/, 'update API completion must be awaited')
assert.match(submitForm, /await createSeoMetadata\(baseData\)/, 'create API completion must be awaited')
assert.equal((submitForm.match(/dialogVisible\.value = false/g) || []).length, 1, 'submit must close the dialog exactly once')
assert.ok(
  submitForm.indexOf('dialogVisible.value = false') > submitForm.indexOf('await updateSeoMetadata(data)') &&
    submitForm.indexOf('dialogVisible.value = false') > submitForm.indexOf('await createSeoMetadata(baseData)'),
  'metadata dialog may close only after the save API resolves'
)
const submitCatchIndex = submitForm.search(/catch(?:\s*\([^)]*\))?\s*\{/)
assert.notEqual(submitCatchIndex, -1, 'submit must handle save failures')
const submitCatch = submitForm.slice(submitCatchIndex)
assert.ok(!submitCatch.includes('dialogVisible.value = false'), 'save failures must leave the metadata dialog open')

const handlePublish = extractFunction(metadataPage, 'handlePublish')
assert.match(handlePublish, /await ElMessageBox\.confirm\(/, 'publish must await confirmation')
assert.match(handlePublish, /await publishSeoMetadata\(row\.id, row\.version\)/, 'publish must pass the row version and await completion')
assert.ok(handlePublish.indexOf('message.success') > handlePublish.indexOf('await publishSeoMetadata'), 'publish success may appear only after API completion')

const siteUrlValidator = extractFunction(sitePage, 'isAbsoluteHttpUrl')
assert.ok(siteUrlValidator.includes("value.includes('\\\\')"), 'site URL must reject raw backslashes before URL parsing')
for (const property of ['username', 'password', 'search', 'hash']) {
  assert.ok(siteUrlValidator.includes(`url.${property}`), `site URL must reject URL ${property}`)
}
assert.match(siteUrlValidator, /\['http:', 'https:'\]\.includes\(url\.protocol\)/, 'site URL must restrict protocols')
assert.ok(siteUrlValidator.includes('Boolean(url.hostname)'), 'site URL must require a hostname')

assert.match(sitePage, /const loadRequestId = ref\(0\)/, 'site loads must use a monotonically increasing request token')
const loadConfig = extractFunction(sitePage, 'loadConfig')
assert.match(loadConfig, /const requestId = \+\+loadRequestId\.value/, 'each site load must claim a new request token')
assert.match(loadConfig, /formData\.value = createDefaultForm\(siteId\)[\s\S]*?await getSeoSiteConfig\(siteId\)/, 'site data must clear before loading a new site')
assert.match(loadConfig, /requestId !== loadRequestId\.value \|\| formData\.value\.siteId !== siteId/, 'site loads must discard stale token or site responses')
assert.match(loadConfig, /catch[\s\S]*?loadError\.value\s*=/, 'site load failure must expose an actionable error state')
assert.match(sitePage, /@click="loadConfig\(\)"/, 'site load failure must expose a retry action')
assert.match(sitePage, /const editorDisabled = computed\(\(\) => loading\.value \|\| Boolean\(loadError\.value\)\)/, 'site editor must disable while loading or failed')
for (const field of siteSaveFields.filter((field) => field !== 'siteId')) {
  const editor = extractOpeningTag(sitePage, field === 'defaultRobots' ? 'el-select' : 'el-input', `v-model="formData.${field}"`)
  assert.ok(editor.includes(':disabled="editorDisabled"'), `${field} must disable while site data is unavailable`)
}
const siteSubmit = extractFunction(sitePage, 'submitForm')
assert.match(siteSubmit, /if \(editorDisabled\.value \|\| saving\.value\) return/, 'site save must be blocked while loading, failed, or already saving')
const siteSubmitButton = extractOpeningTag(sitePage, 'el-button', '@click="submitForm"')
assert.ok(siteSubmitButton.includes(':disabled="saving || editorDisabled"'), 'site save button must disable while loading or failed')

const canonicalValidator = extractFunction(metadataForm, 'validateCanonicalUrl')
assert.match(canonicalValidator, /if \(!value\.trim\(\)\)/, 'Canonical URL must explicitly allow blank input')
const canonicalUrlParser = extractFunction(metadataForm, 'isAbsoluteHttpUrl')
assert.ok(canonicalUrlParser.includes("value.includes('\\\\')"), 'Canonical URL must reject raw backslashes before URL parsing')
for (const property of ['username', 'password', 'hash']) {
  assert.ok(canonicalUrlParser.includes(`url.${property}`), `Canonical URL must reject URL ${property}`)
}
assert.ok(!canonicalUrlParser.includes('url.search'), 'Canonical URL must preserve backend-supported query strings')
assert.match(canonicalUrlParser, /\['http:', 'https:'\]\.includes\(url\.protocol\)/, 'Canonical URL must restrict protocols')
assert.ok(canonicalUrlParser.includes('Boolean(url.hostname)'), 'Canonical URL must require a hostname')

assert.ok(!metadataForm.includes('VERSION_CONFLICT_CODE'), 'UI must not claim access to business codes hidden by the axios wrapper')
assert.ok(!metadataForm.includes('isVersionConflict'), 'dead numeric version-conflict detection must be removed')
assert.match(submitCatch, /isEdit\.value[\s\S]*?版本冲突[\s\S]*?重新加载/, 'any update failure must truthfully warn about possible conflict and request reload')
assert.ok(!metadataForm.includes('v-html'), 'metadata user input must not be rendered as HTML')

const metadataOpen = extractFunction(metadataForm, 'open')
const metadataOpenCatchIndex = metadataOpen.search(/catch(?:\s*\([^)]*\))?\s*\{/)
assert.notEqual(metadataOpenCatchIndex, -1, 'metadata edit load must catch get-by-id failures')
const metadataOpenCatch = metadataOpen.slice(metadataOpenCatchIndex)
assert.match(metadataOpenCatch, /message\.error\([^)]*重试[^)]*\)/, 'metadata edit load failure must show a retry message')
assert.match(metadataOpenCatch, /dialogVisible\.value = false/, 'failed metadata edit load must close the unusable dialog')

assert.equal(packageJson.packageManager, 'pnpm@8.15.9', 'packageManager must pin the supported pnpm 8 release')
assert.equal(packageJson.engines?.pnpm, '>=8.6.0 <9', 'pnpm engine must reject incompatible major releases')

console.log('seo foundation contract: OK')
