const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const ts = require('typescript')
const { test } = require('node:test')
const root = path.resolve(__dirname, '..')
function load(file, dependencies) {
  const result = ts.transpileModule(fs.readFileSync(path.join(root, file), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }
  })
  const module = { exports: {} }
  new Function('require', 'module', 'exports', result.outputText)((name) => dependencies[name], module, module.exports)
  return module.exports
}
function setup(visit, fetch = async () => [{ id: 163 }], tenantId = 1) {
  const state = { visit, calls: 0, clearedCache: 0, contextVersion: { value: 0 } }
  const { validateTenantVisit } = load('src/utils/tenantVisit.ts', {
    '@/utils/auth': { authContextVersion: state.contextVersion, getVisitTenantId: () => state.visit, getTenantId: () => tenantId, clearVisitTenantId: () => { state.visit = undefined } },
    '@/api/system/tenant': { getTenantVisitList: async () => { state.calls++; return fetch() } },
    '@/hooks/web/useCache': { deleteUserCache: () => { state.clearedCache++ } }
  })
  return { state, validateTenantVisit }
}
test('protected tenant cached by platform admin is cleared before loading permissions', async () => {
  const { state, validateTenantVisit } = setup('164')
  await validateTenantVisit()
  assert.equal(state.visit, undefined)
  assert.equal(state.clearedCache, 1)
})
test('allowed visited tenant remains selected, including string storage IDs', async () => {
  const { state, validateTenantVisit } = setup('163')
  await validateTenantVisit()
  assert.equal(state.visit, '163')
  assert.equal(state.clearedCache, 0)
})
test('dedicated tenant own account needs no cross-tenant request', async () => {
  for (const visit of [undefined, '164']) {
    const { state, validateTenantVisit } = setup(visit, undefined, 164)
    await validateTenantVisit()
    assert.equal(state.calls, 0)
  }
})
test('ordinary account with a stale visit loses only the visited context', async () => {
  const { state, validateTenantVisit } = setup(164, async () => [], 163)
  await validateTenantVisit()
  assert.equal(state.visit, undefined)
  assert.equal(state.clearedCache, 1)
})
test('network failure cannot authorize a tenant or silently continue loading it', async () => {
  const { state, validateTenantVisit } = setup(164, async () => { throw Error('offline') })
  await assert.rejects(validateTenantVisit, /offline/)
  assert.equal(state.clearedCache, 0)
})
test('a response from a previous login cannot clear the new account context', async () => {
  let resolve
  const { state, validateTenantVisit } = setup(164, () => new Promise((r) => { resolve = r }))
  const pending = validateTenantVisit()
  state.contextVersion.value++
  state.visit = 163
  resolve([])
  await pending
  assert.equal(state.visit, 163)
  assert.equal(state.clearedCache, 0)
})
test('visit options are fetched in the account context while login choices remain public', async () => {
  const requests = []
  const api = load('src/api/system/tenant/index.ts', { '@/config/axios': { __esModule: true, default: { get: async (req) => { requests.push(req); return [] } } } })
  await api.getTenantVisitList()
  await api.getTenantList()
  assert.deepEqual(requests, [
    { url: '/system/tenant/visit-list', headers: { skipTenantVisit: true } },
    { url: '/system/tenant/simple-list' }
  ])
})
