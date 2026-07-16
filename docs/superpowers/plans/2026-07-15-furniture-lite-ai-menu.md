# Furniture Lite AI Menu Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show the complete existing AI menu tree in Oakved furniture-lite admin mode without exposing BPM, CRM, IoT, MES, DIY, code generation, or job modules.

**Architecture:** Keep `VITE_ADMIN_MODE=furniture-lite` and its existing allowlist-based filtering. Restore the explicit AI route allowlist already represented by the repository's AI access contract, remove only `/ai` from the fixed-route deny list, and update the general furniture-lite contract so the two contracts describe the same approved behavior.

**Tech Stack:** Vue 3, TypeScript, Vite, Node.js contract scripts, pnpm 8-compatible lockfile.

## Global Constraints

- Keep `VITE_ADMIN_MODE=furniture-lite` unchanged.
- Expose only the existing AI route tree in addition to the current furniture-lite routes.
- Keep `/bpm`, `/crm`, `/iot`, `/mes`, `/diy`, `/codegen`, and `/job` denied.
- Do not modify `pnpm-lock.yaml`; it contains an unrelated local change from the user's package installation.
- Do not add AI controllers to the monolithic `yudao-server` in this plan.
- Do not configure AI provider credentials or models.

---

## File Structure

- `yudao电商管理平台前后端/yudao-ui-admin-vue3/src/config/furnitureLite.ts`: owns the furniture-lite dynamic-menu allowlist and fixed-route deny list.
- `yudao电商管理平台前后端/yudao-ui-admin-vue3/scripts/check-furniture-lite-ai-access.mjs`: existing focused regression contract requiring the representative AI routes and requiring `/ai` not to be denied.
- `yudao电商管理平台前后端/yudao-ui-admin-vue3/scripts/check-furniture-lite-config.mjs`: general furniture-lite contract; must be updated because it currently contradicts the approved AI behavior.

### Task 1: Align the furniture-lite contracts with approved AI access

**Files:**
- Modify: `yudao电商管理平台前后端/yudao-ui-admin-vue3/scripts/check-furniture-lite-config.mjs:121-137`
- Test: `yudao电商管理平台前后端/yudao-ui-admin-vue3/scripts/check-furniture-lite-ai-access.mjs`
- Test: `yudao电商管理平台前后端/yudao-ui-admin-vue3/scripts/check-furniture-lite-config.mjs`

**Interfaces:**
- Consumes: the source-text route blocks `const allowedMenuPaths = new Set([...])` and `const deniedFixedRoutePrefixes = [...]` from `src/config/furnitureLite.ts`.
- Produces: two consistent Node.js contracts that require `/ai`, `/ai/chat`, `/ai/model`, `/ai/knowledge`, and `/ai/workflow`, while preserving the non-AI deny list.

- [ ] **Step 1: Run the existing focused contract and verify the red state**

Run:

```powershell
Set-Location -LiteralPath "D:\code\yudao电商管理平台前后端\yudao-ui-admin-vue3"
node .\scripts\check-furniture-lite-ai-access.mjs
```

Expected: FAIL with `furniture-lite mode must allow AI menu route /ai`. This proves the existing contract detects the current hidden-AI behavior.

- [ ] **Step 2: Replace the obsolete hidden-AI assertions in the general contract**

Replace the loop and deny assertion immediately after `deniedFixedRoutePrefixesBlock` with:

```js
const requiredAiRoutes = ['/ai', '/ai/chat', '/ai/model', '/ai/knowledge', '/ai/workflow']

for (const route of requiredAiRoutes) {
  assert.ok(
    allowedMenuBlock.includes(`'${route}'`) || allowedMenuBlock.includes(`"${route}"`),
    `furniture-lite mode must allow AI menu route ${route}`
  )
}

assert.ok(
  !deniedFixedRoutePrefixesBlock.includes("'/ai'") &&
    !deniedFixedRoutePrefixesBlock.includes('"/ai"'),
  'src/config/furnitureLite.ts must not deny /ai fixed routes in furniture-lite mode'
)

for (const route of ['/bpm', '/crm', '/iot', '/mes', '/diy', '/codegen', '/job']) {
  assert.ok(
    deniedFixedRoutePrefixesBlock.includes(`'${route}'`) ||
      deniedFixedRoutePrefixesBlock.includes(`"${route}"`),
    `src/config/furnitureLite.ts must continue to deny ${route} fixed routes`
  )
}
```

- [ ] **Step 3: Run both contracts and verify they fail for the intended missing configuration**

Run:

```powershell
node .\scripts\check-furniture-lite-ai-access.mjs
node .\scripts\check-furniture-lite-config.mjs
```

Expected: both commands fail because `/ai` is not yet in `allowedMenuPaths`. There must be no syntax or file-not-found error.

### Task 2: Restore the explicit AI route allowlist

**Files:**
- Modify: `yudao电商管理平台前后端/yudao-ui-admin-vue3/src/config/furnitureLite.ts:7-47`
- Test: `yudao电商管理平台前后端/yudao-ui-admin-vue3/scripts/check-furniture-lite-ai-access.mjs`
- Test: `yudao电商管理平台前后端/yudao-ui-admin-vue3/scripts/check-furniture-lite-config.mjs`

**Interfaces:**
- Consumes: backend-provided route objects with `path` and optional `children`, filtered through `filterFurnitureLiteMenus` and `filterFurnitureLiteFixedRoutes`.
- Produces: the existing exported filter functions with an expanded explicit AI allowlist; no public function signature changes.

- [ ] **Step 1: Add the approved AI routes to `allowedMenuPaths`**

Insert the following entries after `'/pay/refund'`:

```ts
  '/ai',
  '/ai/console',
  '/ai/console/chat',
  '/ai/console/image',
  '/ai/console/knowledge',
  '/ai/console/mind-map',
  '/ai/console/model',
  '/ai/console/music',
  '/ai/console/workflow',
  '/ai/console/write',
  '/ai/chat',
  '/ai/chat/index',
  '/ai/chat/manager',
  '/ai/model',
  '/ai/model/model',
  '/ai/model/api-key',
  '/ai/model/apiKey',
  '/ai/model/chat-role',
  '/ai/model/chatRole',
  '/ai/model/tool',
  '/ai/knowledge',
  '/ai/knowledge/knowledge',
  '/ai/knowledge/document',
  '/ai/knowledge/segment',
  '/ai/workflow',
  '/ai/write',
  '/ai/write/index',
  '/ai/write/manager',
  '/ai/image',
  '/ai/image/index',
  '/ai/image/manager',
  '/ai/image/square',
  '/ai/music',
  '/ai/music/index',
  '/ai/music/manager',
  '/ai/mind-map',
  '/ai/mind-map/index',
  '/ai/mind-map/manager',
  '/ai/mindmap',
  '/ai/mindmap/index',
  '/ai/mindmap/manager',
```

- [ ] **Step 2: Remove only `/ai` from `deniedFixedRoutePrefixes`**

Change:

```ts
const deniedFixedRoutePrefixes = ['/ai', '/bpm', '/crm', '/iot', '/mes', '/diy', '/codegen', '/job']
```

to:

```ts
const deniedFixedRoutePrefixes = ['/bpm', '/crm', '/iot', '/mes', '/diy', '/codegen', '/job']
```

- [ ] **Step 3: Run the focused and general contracts**

Run:

```powershell
node .\scripts\check-furniture-lite-ai-access.mjs
node .\scripts\check-furniture-lite-config.mjs
```

Expected:

```text
Furniture lite AI access checks passed
Furniture lite config checks passed
```

- [ ] **Step 4: Run the local admin production build**

Run:

```powershell
pnpm.cmd run build:local
```

Expected: Vite exits with code `0` and produces the local build without TypeScript or bundling errors.

- [ ] **Step 5: Verify the intended Git scope**

Run from `D:\code`:

```powershell
git diff --check -- `
  'yudao电商管理平台前后端/yudao-ui-admin-vue3/src/config/furnitureLite.ts' `
  'yudao电商管理平台前后端/yudao-ui-admin-vue3/scripts/check-furniture-lite-config.mjs'

git status --short
```

Expected: the two implementation files are modified. `pnpm-lock.yaml` may remain modified but must not be staged or committed by this task.

- [ ] **Step 6: Commit only the AI menu configuration and contract**

```powershell
git add -- `
  'yudao电商管理平台前后端/yudao-ui-admin-vue3/src/config/furnitureLite.ts' `
  'yudao电商管理平台前后端/yudao-ui-admin-vue3/scripts/check-furniture-lite-config.mjs'

git commit -m "feat: allow AI menus in furniture lite mode"
```

### Task 3: Verify the running admin menu

**Files:**
- No file changes.

**Interfaces:**
- Consumes: the admin frontend at `http://127.0.0.1`, the logged-in `super_admin` account, and the backend-provided AI menu tree.
- Produces: runtime evidence that the menu filter exposes AI without exposing other denied modules.

- [ ] **Step 1: Restart the admin frontend**

Run:

```powershell
Set-Location -LiteralPath "D:\code\yudao电商管理平台前后端\yudao-ui-admin-vue3"
pnpm.cmd dev
```

Expected: Vite starts the local admin at `http://127.0.0.1`.

- [ ] **Step 2: Refresh the authenticated admin session**

Open `http://127.0.0.1`, sign in as the existing super administrator if necessary, and hard-refresh the page with `Ctrl+F5`.

Expected: the sidebar shows the AI module and its authorized child pages. BPM, CRM, IoT, MES, DIY, code generation, and job modules remain absent.

- [ ] **Step 3: Record the backend follow-up boundary**

Open an AI child page and observe its API result.

Expected: menu and route rendering are part of this plan. If the API returns `404` while running monolithic `yudao-server`, record that as the separate backend AI-module integration task defined as out of scope in the design.
