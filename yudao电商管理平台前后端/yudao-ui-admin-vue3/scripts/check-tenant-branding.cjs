const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const ts = require('typescript')
const vue = require('vue')
const { test } = require('node:test')
const root = path.resolve(__dirname, '..')

// Execute the production TypeScript with only the network and browser cache replaced.
function load(relativePath, dependencies = {}) {
  const source = fs.readFileSync(path.join(root, relativePath), 'utf8')
  const result = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }
  })
  const module = { exports: {} }
  new Function('require', 'module', 'exports', result.outputText)(
    (name) => dependencies[name] || require(name), module, module.exports
  )
  return module.exports
}

const branding = load('src/utils/tenantBranding.ts')
const tenants = [{ id: 1, name: '芋道源码' }, { id: 163, name: 'TRIPEER' }, { id: 164, name: 'Vanz家具' }]
const flush = async () => { await Promise.resolve(); await vue.nextTick() }

function setup(t, initial = { tenantId: 163, ACCESS_TOKEN: 'account-a' }, fetchTenants = async () => tenants) {
  const cache = new Map(Object.entries(initial))
  const auth = load('src/utils/auth.ts', {
    '@/hooks/web/useCache': {
      CACHE_KEY: { TenantId: 'tenantId', VisitTenantId: 'visitTenantId' },
      useCache: () => ({ wsCache: { get: (key) => cache.get(key), set: (key, value) => cache.set(key, value), delete: (key) => cache.delete(key) } })
    },
    '@/utils/jsencrypt': { encrypt: (value) => value, decrypt: (value) => value }
  })
  const { useTenantBranding } = load('src/hooks/web/useTenantBranding.ts', {
    '@/api/login': { getTenantSimpleList: fetchTenants },
    '@/utils/auth': auth,
    '@/utils/tenantBranding': branding
  })
  const scope = vue.effectScope()
  const state = scope.run(useTenantBranding)
  t.after(() => scope.stop())
  return { auth, state, cache, scope }
}

test('display alias preserves original tenant values', () => {
  assert.equal(branding.getTenantDisplayName(tenants[0].name), '超级管理员')
  assert.equal(tenants[0].name, '芋道源码')
  assert.equal(branding.getTenantDisplayName('TRIPEER'), 'TRIPEER')
})

test('initial load, tenant visit, and clearing visit follow the effective tenant', async (t) => {
  const { auth, state } = setup(t)
  await flush()
  assert.equal(state.tenantName.value, 'TRIPEER')
  auth.setVisitTenantId(1)
  assert.equal(state.tenantName.value, '全品轩')
  await flush()
  assert.equal(state.tenantName.value, '超级管理员')
  auth.setVisitTenantId('')
  await flush()
  assert.equal(state.tenantName.value, 'TRIPEER')
})

test('logout and another account login discard the previous visit tenant', async (t) => {
  const { auth, state, cache } = setup(t, { tenantId: 163, visitTenantId: 1, ACCESS_TOKEN: 'account-a' })
  await flush()
  auth.removeToken()
  assert.equal(state.tenantName.value, '全品轩')
  assert.equal(cache.has('visitTenantId'), false)
  auth.setTenantId(164)
  auth.setToken({ accessToken: 'account-b', refreshToken: 'refresh-b' })
  await flush()
  assert.equal(state.tenantName.value, 'Vanz家具')
})

test('changing login tenant clears a prior visit and handles string IDs from storage', async (t) => {
  const { auth, state, cache } = setup(t, { tenantId: '163', visitTenantId: 1, ACCESS_TOKEN: 'account-a' })
  auth.setTenantId(164)
  await flush()
  assert.equal(cache.has('visitTenantId'), false)
  assert.equal(state.tenantName.value, 'Vanz家具')
})

test('late responses cannot replace a newer tenant or logged-out state', async (t) => {
  const pending = []
  const { auth, state } = setup(t, undefined, () => new Promise((resolve) => pending.push(resolve)))
  auth.setVisitTenantId(164)
  pending[1](tenants)
  await flush()
  assert.equal(state.tenantName.value, 'Vanz家具')
  pending[0](tenants)
  await flush()
  assert.equal(state.tenantName.value, 'Vanz家具')
  auth.setVisitTenantId(1)
  auth.removeToken()
  pending[2](tenants)
  await flush()
  assert.equal(state.tenantName.value, '全品轩')
})

test('missing tenant and network failure use the system name, never another tenant', async (t) => {
  const { state, auth } = setup(t, undefined, async () => { throw Error('offline') })
  await flush()
  assert.equal(state.tenantName.value, '全品轩')
  auth.setVisitTenantId(164)
  await flush()
  assert.equal(state.tenantName.value, '全品轩')
  const missing = setup(t, { tenantId: 999, ACCESS_TOKEN: 'account-a' })
  await flush()
  assert.equal(missing.state.tenantName.value, '全品轩')
})

test('remount reads the persisted active tenant; no token means no request', async (t) => {
  const current = setup(t, { tenantId: 163, visitTenantId: '164', ACCESS_TOKEN: 'account-a' })
  await flush()
  assert.equal(current.state.tenantName.value, 'Vanz家具')
  let calls = 0
  const loggedOut = setup(t, { tenantId: 163 }, async () => { calls++; return tenants })
  await flush()
  assert.equal(calls, 0)
  assert.equal(loggedOut.state.tenantName.value, '全品轩')
})
