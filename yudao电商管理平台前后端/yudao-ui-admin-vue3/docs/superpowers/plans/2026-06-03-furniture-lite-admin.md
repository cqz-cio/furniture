# Furniture Lite Admin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a configuration-driven furniture-lite admin mode that hides unused Yudao modules and developer UI while keeping the existing codebase reversible.

**Architecture:** Add a focused config module that owns environment switches and route filtering. Wire it into the existing permission store so backend menus, dynamic routes, sidebar rendering, breadcrumbs, and tags all receive the same filtered route tree. Reuse the existing `DocAlert` component and hide developer links through the same config module.

**Tech Stack:** Vue 3, Vite env variables, Pinia permission store, Vue Router dynamic routes, Node built-in `assert` for a lightweight source verification script.

---

## File Structure

- Modify `.env.local`: enable `furniture-lite` mode and disable documentation/developer UI for local runs.
- Modify `package.json`: add a lightweight verification script entry.
- Create `scripts/check-furniture-lite-config.mjs`: source-level checks for config wiring and env switches.
- Create `src/config/furnitureLite.ts`: central mode, doc alert, developer link, menu route, and fixed route helpers.
- Modify `src/store/modules/permission.ts`: apply furniture-lite filtering before dynamic route generation and before assigning sidebar routers.
- Modify `src/components/DocAlert/index.vue`: use the central doc-alert visibility helper.
- Modify `src/layout/components/UserInfo/src/UserInfo.vue`: hide the documentation dropdown item when developer links are disabled.
- Modify `src/views/Login/components/LoginForm.vue`: hide the developer reading links when developer links are disabled.

## Task 1: Add Environment Switches and Static Check Script

**Files:**
- Modify: `.env.local`
- Modify: `package.json`
- Create: `scripts/check-furniture-lite-config.mjs`

- [ ] **Step 1: Add the failing source check script**

Create `scripts/check-furniture-lite-config.mjs`:

```js
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const read = (path) => readFileSync(path, 'utf8')

const envLocal = read('.env.local')
const packageJson = JSON.parse(read('package.json'))

assert.match(envLocal, /^VITE_ADMIN_MODE=furniture-lite$/m)
assert.match(envLocal, /^VITE_SHOW_DOC_ALERT=false$/m)
assert.match(envLocal, /^VITE_SHOW_DEV_LINKS=false$/m)
assert.match(envLocal, /^VITE_APP_DOCALERT_ENABLE=false$/m)

assert.equal(packageJson.scripts['check:furniture-lite'], 'node scripts/check-furniture-lite-config.mjs')

const configSource = read('src/config/furnitureLite.ts')
assert.match(configSource, /export const isFurnitureLiteMode/)
assert.match(configSource, /export const isDocAlertVisible/)
assert.match(configSource, /export const isDevLinksVisible/)
assert.match(configSource, /export const filterFurnitureLiteMenus/)
assert.match(configSource, /export const filterFurnitureLiteFixedRoutes/)
assert.match(configSource, /\/mall\/product\/category/)
assert.match(configSource, /\/mall\/trade\/order/)
assert.match(configSource, /\/system\/role/)
assert.match(configSource, /deniedFixedRoutePrefixes/)
assert.match(configSource, /\/ai/)
assert.match(configSource, /\/crm/)

const permissionSource = read('src/store/modules/permission.ts')
assert.match(permissionSource, /filterFurnitureLiteMenus/)
assert.match(permissionSource, /filterFurnitureLiteFixedRoutes/)

const docAlertSource = read('src/components/DocAlert/index.vue')
assert.match(docAlertSource, /isDocAlertVisible/)

const userInfoSource = read('src/layout/components/UserInfo/src/UserInfo.vue')
assert.match(userInfoSource, /isDevLinksVisible/)

const loginFormSource = read('src/views/Login/components/LoginForm.vue')
assert.match(loginFormSource, /showDevLinks/)

console.log('Furniture lite config checks passed')
```

- [ ] **Step 2: Run the check and confirm it fails before implementation**

Run:

```powershell
cd "D:\code\yudao电商管理平台前后端\yudao-ui-admin-vue3"
node scripts/check-furniture-lite-config.mjs
```

Expected: FAIL because `.env.local`, `package.json`, and `src/config/furnitureLite.ts` are not wired yet.

- [ ] **Step 3: Add environment variables to `.env.local`**

Append these lines:

```env
# Furniture commerce lightweight admin mode
VITE_ADMIN_MODE=furniture-lite
VITE_SHOW_DOC_ALERT=false
VITE_SHOW_DEV_LINKS=false
VITE_APP_DOCALERT_ENABLE=false
```

- [ ] **Step 4: Add the package script**

In `package.json`, add this script after `preview`:

```json
"check:furniture-lite": "node scripts/check-furniture-lite-config.mjs",
```

- [ ] **Step 5: Run the check and confirm remaining failure points**

Run:

```powershell
pnpm.cmd check:furniture-lite
```

Expected: FAIL because `src/config/furnitureLite.ts` and consuming imports do not exist yet.

- [ ] **Step 6: Commit the test harness and environment switch**

```bash
git add -- yudao电商管理平台前后端/yudao-ui-admin-vue3/.env.local yudao电商管理平台前后端/yudao-ui-admin-vue3/package.json yudao电商管理平台前后端/yudao-ui-admin-vue3/scripts/check-furniture-lite-config.mjs
git commit -m "test: add furniture lite config check"
```

## Task 2: Create the Furniture Lite Config Module

**Files:**
- Create: `src/config/furnitureLite.ts`

- [ ] **Step 1: Create the config module**

Create `src/config/furnitureLite.ts`:

```ts
type RouteLike = {
  path: string
  children?: RouteLike[]
}

const exactMenuPaths = new Set([
  '/index',
  '/mall/home',
  '/mall/product',
  '/mall/product/spu',
  '/mall/product/category',
  '/mall/product/brand',
  '/mall/product/property',
  '/mall/trade',
  '/mall/trade/order',
  '/mall/trade/after-sale',
  '/mall/trade/delivery',
  '/mall/trade/delivery/express',
  '/mall/trade/delivery/express-template',
  '/mall/trade/delivery/pick-up-store',
  '/member',
  '/member/user',
  '/member/level',
  '/member/tag',
  '/member/group',
  '/pay',
  '/pay/app',
  '/pay/order',
  '/pay/refund',
  '/infra/file',
  '/infra/file-config',
  '/system/user',
  '/system/role',
  '/system/menu'
])

const detailRoutePrefixes = [
  '/mall/product/spu/add',
  '/mall/product/spu/edit',
  '/mall/product/spu/detail',
  '/mall/product/property/value',
  '/mall/trade/order/detail',
  '/mall/trade/after-sale/detail',
  '/member/user/detail',
  '/pay/cashier'
]

const deniedFixedRoutePrefixes = ['/bpm', '/crm', '/ai', '/iot', '/mes', '/diy', '/codegen', '/job']

const envFalse = (value: unknown) => String(value).toLowerCase() === 'false'

export const isFurnitureLiteMode = () => import.meta.env.VITE_ADMIN_MODE === 'furniture-lite'

export const isDocAlertVisible = () =>
  !envFalse(import.meta.env.VITE_SHOW_DOC_ALERT) &&
  !envFalse(import.meta.env.VITE_APP_DOCALERT_ENABLE)

export const isDevLinksVisible = () => !envFalse(import.meta.env.VITE_SHOW_DEV_LINKS)

const normalizePath = (path: string) => {
  const normalized = `/${path || ''}`.replace(/\/+/g, '/')
  return normalized.length > 1 && normalized.endsWith('/') ? normalized.slice(0, -1) : normalized
}

const joinPath = (parentPath: string, childPath: string) => {
  if (childPath.startsWith('/')) {
    return normalizePath(childPath)
  }
  return normalizePath(`${parentPath}/${childPath}`)
}

const isAllowedLitePath = (path: string) => {
  const normalizedPath = normalizePath(path)
  return (
    exactMenuPaths.has(normalizedPath) ||
    detailRoutePrefixes.some((prefix) => normalizedPath.startsWith(prefix))
  )
}

const filterRouteTree = <T extends RouteLike>(routes: T[], parentPath = ''): T[] => {
  return routes.flatMap((route) => {
    const fullPath = joinPath(parentPath, route.path)
    const children = route.children ? filterRouteTree(route.children as T[], fullPath) : []
    const keepSelf = isAllowedLitePath(fullPath)

    if (!keepSelf && children.length === 0) {
      return []
    }

    return [
      {
        ...route,
        children: children.length > 0 ? children : route.children && keepSelf ? [] : undefined
      }
    ]
  })
}

export const filterFurnitureLiteMenus = <T extends RouteLike>(routes: T[]): T[] => {
  if (!isFurnitureLiteMode()) {
    return routes
  }
  return filterRouteTree(routes)
}

export const filterFurnitureLiteFixedRoutes = <T extends RouteLike>(routes: T[]): T[] => {
  if (!isFurnitureLiteMode()) {
    return routes
  }

  return routes.filter((route) => {
    const path = normalizePath(route.path)
    return !deniedFixedRoutePrefixes.some((prefix) => path.startsWith(prefix))
  })
}
```

- [ ] **Step 2: Run the check script**

Run:

```powershell
pnpm.cmd check:furniture-lite
```

Expected: FAIL because `permission.ts`, `DocAlert`, `UserInfo`, and `LoginForm` do not consume the helper yet.

- [ ] **Step 3: Commit the config module**

```bash
git add -- yudao电商管理平台前后端/yudao-ui-admin-vue3/src/config/furnitureLite.ts
git commit -m "feat: add furniture lite admin config"
```

## Task 3: Wire Dynamic Menu and Route Filtering

**Files:**
- Modify: `src/store/modules/permission.ts`

- [ ] **Step 1: Import the furniture-lite helpers**

Add this import:

```ts
import {
  filterFurnitureLiteFixedRoutes,
  filterFurnitureLiteMenus
} from '@/config/furnitureLite'
```

- [ ] **Step 2: Filter backend menu routes before generating dynamic routes**

Replace:

```ts
const routerMap: AppRouteRecordRaw[] = generateRoute(res)
```

with:

```ts
const filteredRoutes = filterFurnitureLiteMenus(res)
const routerMap: AppRouteRecordRaw[] = generateRoute(filteredRoutes)
```

- [ ] **Step 3: Filter fixed routes before assigning sidebar routers**

Replace:

```ts
this.routers = cloneDeep(remainingRouter).concat(routerMap)
```

with:

```ts
const baseRouters = filterFurnitureLiteFixedRoutes(cloneDeep(remainingRouter))
this.routers = baseRouters.concat(routerMap)
```

- [ ] **Step 4: Run the check script**

Run:

```powershell
pnpm.cmd check:furniture-lite
```

Expected: FAIL because display components are not wired yet.

- [ ] **Step 5: Run a build check**

Run:

```powershell
pnpm.cmd build:local
```

Expected: PASS. Any TypeScript error from `src/config/furnitureLite.ts` must be fixed before proceeding.

- [ ] **Step 6: Commit menu filtering**

```bash
git add -- yudao电商管理平台前后端/yudao-ui-admin-vue3/src/store/modules/permission.ts
git commit -m "feat: filter admin routes for furniture lite mode"
```

## Task 4: Hide Documentation Alerts and Developer Links

**Files:**
- Modify: `src/components/DocAlert/index.vue`
- Modify: `src/layout/components/UserInfo/src/UserInfo.vue`
- Modify: `src/views/Login/components/LoginForm.vue`

- [ ] **Step 1: Update `DocAlert` to use the central helper**

In `src/components/DocAlert/index.vue`, add:

```ts
import { isDocAlertVisible } from '@/config/furnitureLite'
```

Replace:

```ts
const getEnable = () => {
  return import.meta.env.VITE_APP_DOCALERT_ENABLE !== 'false'
}
```

with:

```ts
const getEnable = () => isDocAlertVisible()
```

- [ ] **Step 2: Hide the user dropdown documentation link**

In `src/layout/components/UserInfo/src/UserInfo.vue`, add:

```ts
import { isDevLinksVisible } from '@/config/furnitureLite'
```

Add:

```ts
const showDevLinks = computed(() => isDevLinksVisible())
```

Replace the documentation dropdown item with:

```vue
<ElDropdownItem v-if="showDevLinks">
  <Icon icon="ep:menu" />
  <div @click="toDocument">{{ t('common.document') }}</div>
</ElDropdownItem>
```

- [ ] **Step 3: Hide login page developer links**

In `src/views/Login/components/LoginForm.vue`, add:

```ts
import { isDevLinksVisible } from '@/config/furnitureLite'
```

Add:

```ts
const showDevLinks = computed(() => isDevLinksVisible())
```

Wrap the developer divider and link block:

```vue
<template v-if="showDevLinks">
  <el-divider content-position="center">钀屾柊蹇呰</el-divider>
  <el-col :span="24" class="px-10px">
    <el-form-item>
      <div class="w-full flex justify-between">
        <el-link href="https://doc.iocoder.cn/" target="_blank">馃摎寮€鍙戞寚鍗?/el-link>
        <el-link href="https://doc.iocoder.cn/video/" target="_blank">馃敟瑙嗛鏁欑▼</el-link>
        <el-link href="https://www.iocoder.cn/Interview/good-collection/" target="_blank">
          鈿￠潰璇曟墜鍐?
        </el-link>
        <el-link href="http://static.yudao.iocoder.cn/mp/Aix9975.jpeg" target="_blank">
          馃澶栧寘鍜ㄨ
        </el-link>
      </div>
    </el-form-item>
  </el-col>
</template>
```

- [ ] **Step 4: Run the check script**

Run:

```powershell
pnpm.cmd check:furniture-lite
```

Expected: PASS and print:

```text
Furniture lite config checks passed
```

- [ ] **Step 5: Run a build check**

Run:

```powershell
pnpm.cmd build:local
```

Expected: PASS.

- [ ] **Step 6: Commit display cleanup**

```bash
git add -- yudao电商管理平台前后端/yudao-ui-admin-vue3/src/components/DocAlert/index.vue yudao电商管理平台前后端/yudao-ui-admin-vue3/src/layout/components/UserInfo/src/UserInfo.vue yudao电商管理平台前后端/yudao-ui-admin-vue3/src/views/Login/components/LoginForm.vue
git commit -m "feat: hide developer chrome in furniture lite mode"
```

## Task 5: Browser Verification and Rollback Check

**Files:**
- No source files unless verification reveals a defect.

- [ ] **Step 1: Start or restart the admin frontend**

Run:

```powershell
cd "D:\code\yudao电商管理平台前后端\yudao-ui-admin-vue3"
pnpm.cmd dev
```

Expected: Vite serves the admin at `http://127.0.0.1/` or prints the local URL.

- [ ] **Step 2: Login and check visible menus**

Open:

```text
http://127.0.0.1/
```

Login:

```text
admin / admin123
```

Expected visible business areas:

```text
商城系统
商品中心
订单中心
会员中心
支付管理
基础设施
系统管理
```

Expected hidden areas:

```text
AI
工作流程
CRM
ERP
IoT
MES
公众号
报表管理
WMS
Boot 开发文档
Cloud 开发文档
```

- [ ] **Step 3: Check kept pages still open**

Open these routes from the sidebar:

```text
商城系统 -> 商品中心 -> 商品分类
商城系统 -> 商品中心 -> 商品列表
商城系统 -> 订单中心 -> 订单列表
会员中心 -> 会员用户
支付管理 -> 支付应用
基础设施 -> 文件管理
系统管理 -> 角色管理
```

Expected: each page renders without `系统异常`, route import failure, or console error.

- [ ] **Step 4: Check direct hidden route access**

Navigate directly to:

```text
http://127.0.0.1/ai/model/model
http://127.0.0.1/crm/customer
http://127.0.0.1/wms/home
```

Expected: the hidden pages do not render. The app should show 404, redirect, or permission handling instead.

- [ ] **Step 5: Check rollback behavior**

Temporarily set `.env.local` values:

```env
VITE_ADMIN_MODE=full
VITE_SHOW_DOC_ALERT=true
VITE_SHOW_DEV_LINKS=true
VITE_APP_DOCALERT_ENABLE=true
```

Restart:

```powershell
pnpm.cmd dev
```

Expected: the full Yudao admin menu and documentation links return.

Restore `.env.local` to furniture-lite values before committing final verification:

```env
VITE_ADMIN_MODE=furniture-lite
VITE_SHOW_DOC_ALERT=false
VITE_SHOW_DEV_LINKS=false
VITE_APP_DOCALERT_ENABLE=false
```

- [ ] **Step 6: Final verification**

Run:

```powershell
pnpm.cmd check:furniture-lite
pnpm.cmd build:local
git status --short
```

Expected:

```text
Furniture lite config checks passed
build:local exits 0
Only intentional source/doc/env changes are listed
```

- [ ] **Step 7: Commit final verification adjustments if any were needed**

If any source file changed during verification, commit it:

```bash
git add -- yudao电商管理平台前后端/yudao-ui-admin-vue3
git commit -m "fix: stabilize furniture lite admin verification"
```

If no source file changed, no commit is needed for this task.

## Self Review

- Spec coverage: environment mode, doc alerts, developer links, dynamic menus, fixed routes, rollback, and verification are all covered by Tasks 1 through 5.
- Placeholder scan: no placeholder markers or unspecified implementation steps remain.
- Type consistency: the helper names used in the plan match the helper names required by the source check script.
- Scope check: this plan implements only first-version frontend display control. Backend Maven module pruning and database menu seed pruning remain outside this implementation.
