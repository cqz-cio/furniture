# ERP AI Error Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run ERP and AI from one Java 17 monolith, replace generic errors for missing external model configuration with actionable guidance, and remove the confirmed AI music and mall dashboard warnings.

**Architecture:** Add the AI server module to the existing `yudao-server` monolith and route every admin API call to port 48080. Keep standalone microservice launchers intact for full-cluster deployments, but simplify the root local lifecycle scripts to one process and disable XXL-Job only in the local profile. Add small contract scripts for frontend behavior because this UI repository has no Vitest/Jest runner.

**Tech Stack:** Java 17, Spring Boot 3, Maven, PowerShell, Vue 3, TypeScript, Vite, Element Plus, Node.js contract checks.

## Global Constraints

- Work only on `codex/agent-rag`; do not modify `main`.
- Preserve the existing uncommitted SEO additions in the root and `yudao-server` POM files.
- Preserve the user's uncommitted `pnpm-lock.yaml`; do not regenerate dependencies.
- Never add a real or placeholder model API key to source, tests, logs, or documentation.
- Local startup must expose all ERP and AI admin APIs on port 48080 with one Java process.
- Missing external credentials must produce an actionable configuration message, not a false success result.
- Address verification remains in truthful fallback mode until the user supplies a provider credential.

---

## File Structure

- `yudao-cloud/yudao-server/pom.xml`: compose the AI module into the monolith while retaining the user's SEO module dependency.
- `yudao-cloud/yudao-server/src/main/resources/application-local.yaml`: turn off the unavailable XXL-Job executor only for local development.
- `start-yudao-all-backend.ps1`: build, start, verify, and report the single monolith.
- `stop-yudao-all-backend.ps1`: stop the monolith and clean up state from the former standalone AI process.
- `yudao-cloud/script/jdk17/tests/MonolithAiIntegration.Tests.ps1`: structural contract for monolith composition and local scheduler behavior.
- `yudao-cloud/script/jdk17/tests/AllBackendLauncher.Tests.ps1`: one-command lifecycle contract.
- `yudao-ui-admin-vue3/.env.local`: remove the standalone AI base URL.
- `yudao-ui-admin-vue3/src/config/axios/config.ts`: expose only the unified base URL.
- `yudao-ui-admin-vue3/src/config/axios/service.ts`: stop overriding `/ai/` request destinations.
- `yudao-ui-admin-vue3/scripts/check-local-ai-routing.mjs`: assert unified local routing.
- `yudao-ui-admin-vue3/src/views/ai/components/AiModelConfigurationAlert.vue`: reusable model availability check and guidance.
- `yudao-ui-admin-vue3/src/views/ai/{chat,image,write,mindmap}/index/index.vue`: display/consume model availability without generic errors.
- `yudao-ui-admin-vue3/scripts/check-ai-model-guidance.mjs`: model-guidance contract.
- `yudao-ui-admin-vue3/src/views/ai/music/index/list/audioBar/index.vue`: valid media state and local cover handling.
- `yudao-ui-admin-vue3/scripts/check-ai-music-player.mjs`: player contract.
- `yudao-ui-admin-vue3/src/views/mall/home/index.vue`: pass numeric currency values to comparison cards.
- `yudao-ui-admin-vue3/src/views/mall/statistics/product/components/ProductSummary.vue`: pass numeric values and use current ECharts axis label syntax.
- `yudao-ui-admin-vue3/scripts/check-mall-warning-contract.mjs`: regression contract for the warning fixes.
- `yudao-ui-admin-vue3/package.json`: expose the new contract checks without changing dependencies.

---

### Task 1: Compose AI into the local monolith

**Files:**
- Create: `yudao电商管理平台前后端/yudao-cloud/script/jdk17/tests/MonolithAiIntegration.Tests.ps1`
- Modify: `yudao电商管理平台前后端/yudao-cloud/script/jdk17/tests/AllBackendLauncher.Tests.ps1`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-server/pom.xml`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-server/src/main/resources/application-local.yaml`
- Modify: `start-yudao-all-backend.ps1`
- Modify: `stop-yudao-all-backend.ps1`
- Modify: `yudao电商管理平台前后端/yudao-ui-admin-vue3/.env.local`
- Modify: `yudao电商管理平台前后端/yudao-ui-admin-vue3/src/config/axios/config.ts`
- Modify: `yudao电商管理平台前后端/yudao-ui-admin-vue3/src/config/axios/service.ts`
- Modify: `yudao电商管理平台前后端/yudao-ui-admin-vue3/scripts/check-local-ai-routing.mjs`

**Interfaces:**
- Produces: one `yudao-server` process on 48080 serving `/admin-api/ai/**`, with `xxl.job.enabled=false` only under `local`.
- Preserves: standalone `Start-Jdk17Backend.ps1` support for explicit microservice launches.

- [ ] **Step 1: Write the failing monolith contract**

Create a PowerShell contract that reads the monolith POM and local YAML and asserts:

```powershell
$serverPom = Get-Content -LiteralPath (Join-Path $cloudRoot 'yudao-server\pom.xml') -Raw
$localConfig = Get-Content -LiteralPath (Join-Path $cloudRoot 'yudao-server\src\main\resources\application-local.yaml') -Raw

if ($serverPom -notmatch '<artifactId>yudao-module-ai-server</artifactId>') {
    throw 'The local monolith must include yudao-module-ai-server.'
}
if ($localConfig -notmatch '(?ms)^xxl:\s*\r?\n\s+job:\s*\r?\n\s+enabled:\s*false') {
    throw 'The local profile must disable XXL-Job when no admin service is installed.'
}
```

Update `AllBackendLauncher.Tests.ps1` so verification requires `YUDAO_SERVER_PORT=48080` and rejects `AI_SERVER_PORT`, `Starting ai-server`, and an AI health probe.

- [ ] **Step 2: Run the contracts and verify RED**

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\yudao电商管理平台前后端\yudao-cloud\script\jdk17\tests\MonolithAiIntegration.Tests.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\yudao电商管理平台前后端\yudao-cloud\script\jdk17\tests\AllBackendLauncher.Tests.ps1
pnpm --dir .\yudao电商管理平台前后端\yudao-ui-admin-vue3 check:local-ai-routing
```

Expected: all three fail because the AI dependency, local scheduler override, one-process launcher, and unified frontend routing do not exist yet.

- [ ] **Step 3: Add the AI module to the monolith**

Add this dependency next to the other module-server dependencies, without removing the existing SEO dependency:

```xml
<dependency>
    <groupId>cn.iocoder.cloud</groupId>
    <artifactId>yudao-module-ai-server</artifactId>
    <version>${revision}</version>
</dependency>
```

Add the local-only scheduler override under the existing `xxl.job` block:

```yaml
xxl:
  job:
    enabled: false
    admin:
      addresses: http://127.0.0.1:9090/xxl-job-admin
```

- [ ] **Step 4: Simplify the root lifecycle scripts**

Change `start-yudao-all-backend.ps1` to build only `yudao-server`, verify only the monolith artifact, check only port 48080, start one hidden Java process, and print:

```text
All backends started: yudao-server=UP (ERP + AI)
```

Keep `stop-yudao-all-backend.ps1` invoking the existing precise stopper for `yudao-server` and `ai-server` so it also removes a stale legacy AI state file/process after an upgrade.

- [ ] **Step 5: Unify frontend routing**

Remove `VITE_AI_BASE_URL` from `.env.local`, remove `ai_base_url` from Axios config, and remove the request-interceptor branch that changes `config.baseURL` for `/ai/`. Rewrite the routing contract to assert absence of all three mechanisms and assert that `.env.local` points `VITE_BASE_URL` to `http://localhost:48080`.

- [ ] **Step 6: Verify GREEN**

Run the three commands from Step 2. Expected: all pass.

Then run:

```powershell
.\yudao电商管理平台前后端\yudao-cloud\script\jdk17\Invoke-MavenJdk17.ps1 -MavenArgs @('-pl','yudao-server','-am','package','-DskipTests')
```

Expected: `BUILD SUCCESS`, and `yudao-server/target/yudao-server.jar` contains AI controller classes.

- [ ] **Step 7: Commit the architecture fix**

```powershell
git add start-yudao-all-backend.ps1 stop-yudao-all-backend.ps1 `
  yudao电商管理平台前后端/yudao-cloud/yudao-server/src/main/resources/application-local.yaml `
  yudao电商管理平台前后端/yudao-cloud/script/jdk17/tests/MonolithAiIntegration.Tests.ps1 `
  yudao电商管理平台前后端/yudao-cloud/script/jdk17/tests/AllBackendLauncher.Tests.ps1 `
  yudao电商管理平台前后端/yudao-ui-admin-vue3/.env.local `
  yudao电商管理平台前后端/yudao-ui-admin-vue3/src/config/axios/config.ts `
  yudao电商管理平台前后端/yudao-ui-admin-vue3/src/config/axios/service.ts `
  yudao电商管理平台前后端/yudao-ui-admin-vue3/scripts/check-local-ai-routing.mjs
git add -p -- yudao电商管理平台前后端/yudao-cloud/yudao-server/pom.xml
git commit -m "fix: run ERP and AI in one local backend"
```

At the interactive POM staging prompt, stage only the hunk adding `yudao-module-ai-server`. Leave the pre-existing `yudao-module-seo-server` hunk unstaged.

---

### Task 2: Give actionable guidance when models are not configured

**Files:**
- Create: `yudao电商管理平台前后端/yudao-ui-admin-vue3/src/views/ai/components/AiModelConfigurationAlert.vue`
- Create: `yudao电商管理平台前后端/yudao-ui-admin-vue3/scripts/check-ai-model-guidance.mjs`
- Modify: `yudao电商管理平台前后端/yudao-ui-admin-vue3/src/views/ai/chat/index/index.vue`
- Modify: `yudao电商管理平台前后端/yudao-ui-admin-vue3/src/views/ai/image/index/index.vue`
- Modify: `yudao电商管理平台前后端/yudao-ui-admin-vue3/src/views/ai/write/index/index.vue`
- Modify: `yudao电商管理平台前后端/yudao-ui-admin-vue3/src/views/ai/mindmap/index/index.vue`
- Modify: `yudao电商管理平台前后端/yudao-ui-admin-vue3/package.json`

**Interfaces:**
- Produces: `loaded(configured: boolean, models: ModelVO[])` from the alert component.
- Consumes: `AiModelTypeEnum.CHAT` or `AiModelTypeEnum.IMAGE` and `ModelApi.getModelSimpleList(type)`.

- [ ] **Step 1: Write the failing model-guidance contract**

Create a Node contract that asserts the component contains the exact user-facing copy and emits model state:

```js
assert.match(component, /AI 模型尚未配置/)
assert.match(component, /请先在 AI 控制台配置模型和 API Key/)
assert.match(component, /emit\('loaded', configured\.value, models\)/)
for (const page of pages) assert.match(page, /AiModelConfigurationAlert/)
```

Add `"check:ai-model-guidance": "node scripts/check-ai-model-guidance.mjs"` to `package.json`.

- [ ] **Step 2: Run the contract and verify RED**

Run:

```powershell
pnpm --dir .\yudao电商管理平台前后端\yudao-ui-admin-vue3 check:ai-model-guidance
```

Expected: fail because the component and page integrations are absent.

- [ ] **Step 3: Implement the reusable alert**

The component loads the requested model type once, emits the full list, and renders:

```vue
<el-alert
  v-if="checked && !configured"
  title="AI 模型尚未配置"
  description="请先在 AI 控制台配置模型和 API Key，再使用生成能力。"
  type="warning"
  show-icon
  :closable="false"
/>
```

On a successful empty response, set `configured=false`. On a request failure, rethrow after emitting no false success state so the existing request layer preserves the real error.

- [ ] **Step 4: Integrate the four generation entry pages**

- Chat, write, and mind-map use `AiModelTypeEnum.CHAT`.
- Image uses `AiModelTypeEnum.IMAGE` and replaces its duplicate `onMounted` model request with the emitted `models` list.
- Write and mind-map store the emitted boolean and stop submit before opening a stream when it is false, showing `请先在 AI 控制台配置模型和 API Key`.
- Chat displays the warning above its main interaction area; the backend keeps returning its specific business error if a send is attempted before configuration.

- [ ] **Step 5: Verify GREEN**

Run:

```powershell
pnpm --dir .\yudao电商管理平台前后端\yudao-ui-admin-vue3 check:ai-model-guidance
pnpm --dir .\yudao电商管理平台前后端\yudao-ui-admin-vue3 ts:check
```

Expected: contract passes and TypeScript reports no new errors.

- [ ] **Step 6: Commit the model guidance**

```powershell
git add yudao电商管理平台前后端/yudao-ui-admin-vue3/package.json `
  yudao电商管理平台前后端/yudao-ui-admin-vue3/scripts/check-ai-model-guidance.mjs `
  yudao电商管理平台前后端/yudao-ui-admin-vue3/src/views/ai/components/AiModelConfigurationAlert.vue `
  yudao电商管理平台前后端/yudao-ui-admin-vue3/src/views/ai/chat/index/index.vue `
  yudao电商管理平台前后端/yudao-ui-admin-vue3/src/views/ai/image/index/index.vue `
  yudao电商管理平台前后端/yudao-ui-admin-vue3/src/views/ai/write/index/index.vue `
  yudao电商管理平台前后端/yudao-ui-admin-vue3/src/views/ai/mindmap/index/index.vue
git commit -m "fix: explain missing AI model configuration"
```

---

### Task 3: Correct the AI music player

**Files:**
- Create: `yudao电商管理平台前后端/yudao-ui-admin-vue3/scripts/check-ai-music-player.mjs`
- Modify: `yudao电商管理平台前后端/yudao-ui-admin-vue3/src/views/ai/music/index/list/audioBar/index.vue`
- Modify: `yudao电商管理平台前后端/yudao-ui-admin-vue3/package.json`

**Interfaces:**
- Media state: `isPaused: boolean`, `currentTime: number`, `duration: number`, `volumePercent: number`, `muted: boolean`.
- DOM volume: `volumePercent / 100`, constrained to `[0, 1]`.

- [ ] **Step 1: Write the failing player contract**

The Node check must reject `v-bind="audioProps"`, string time state, and the external Alipay cover URL, while requiring `@loadedmetadata`, `@timeupdate`, `@play`, `@pause`, numeric slider bounds, and a local imported cover.

- [ ] **Step 2: Run the contract and verify RED**

```powershell
pnpm --dir .\yudao电商管理平台前后端\yudao-ui-admin-vue3 check:ai-music-player
```

Expected: fail on the current invalid media bindings.

- [ ] **Step 3: Implement valid media state**

Replace the current bound object with explicit media attributes and events:

```vue
<audio
  ref="audioRef"
  :src="audioUrl"
  :muted="muted"
  preload="metadata"
  @loadedmetadata="syncDuration"
  @timeupdate="syncCurrentTime"
  @play="isPaused = false"
  @pause="isPaused = true"
/>
```

Use numeric progress and volume sliders. Update `audio.currentTime` only from progress input, update `audio.volume` with `Math.min(1, Math.max(0, volumePercent / 100))`, and format numeric seconds for display. Import `@/assets/imgs/logo.png` as the local fallback cover.

- [ ] **Step 4: Verify GREEN**

```powershell
pnpm --dir .\yudao电商管理平台前后端\yudao-ui-admin-vue3 check:ai-music-player
pnpm --dir .\yudao电商管理平台前后端\yudao-ui-admin-vue3 ts:check
```

Expected: contract passes and the player produces no media-property type errors.

- [ ] **Step 5: Commit the player fix**

```powershell
git add yudao电商管理平台前后端/yudao-ui-admin-vue3/package.json `
  yudao电商管理平台前后端/yudao-ui-admin-vue3/scripts/check-ai-music-player.mjs `
  yudao电商管理平台前后端/yudao-ui-admin-vue3/src/views/ai/music/index/list/audioBar/index.vue
git commit -m "fix: use valid media state in AI music player"
```

---

### Task 4: Remove confirmed mall console warnings

**Files:**
- Create: `yudao电商管理平台前后端/yudao-ui-admin-vue3/scripts/check-mall-warning-contract.mjs`
- Modify: `yudao电商管理平台前后端/yudao-ui-admin-vue3/src/views/mall/home/index.vue`
- Modify: `yudao电商管理平台前后端/yudao-ui-admin-vue3/src/views/mall/statistics/product/components/ProductSummary.vue`
- Modify: `yudao电商管理平台前后端/yudao-ui-admin-vue3/package.json`

**Interfaces:**
- `ComparisonCard.value`, `ComparisonCard.reference`, and `SummaryCard.value` receive `number`.
- ECharts `axisLabel` receives `color` and `fontSize` directly.

- [ ] **Step 1: Write the failing warning contract**

Require `Number(fenToYuan(...))` at monetary card boundaries and reject `axisLabel: { textStyle: { ... } }` in `ProductSummary.vue`.

- [ ] **Step 2: Run the contract and verify RED**

```powershell
pnpm --dir .\yudao电商管理平台前后端\yudao-ui-admin-vue3 check:mall-warnings
```

Expected: fail because formatted currency strings and deprecated axis configuration remain.

- [ ] **Step 3: Implement numeric boundaries and current chart syntax**

Wrap every monetary `fenToYuan` value passed into a numeric card with `Number(...)`. Replace:

```ts
axisLabel: {
  textStyle: { color: '#909399', fontSize: 12 }
}
```

with:

```ts
axisLabel: {
  color: '#909399',
  fontSize: 12
}
```

Keep currency display precision at two decimals.

- [ ] **Step 4: Verify GREEN**

```powershell
pnpm --dir .\yudao电商管理平台前后端\yudao-ui-admin-vue3 check:mall-warnings
pnpm --dir .\yudao电商管理平台前后端\yudao-ui-admin-vue3 ts:check
```

Expected: both pass without introducing new warnings.

- [ ] **Step 5: Commit the mall warning fix**

```powershell
git add yudao电商管理平台前后端/yudao-ui-admin-vue3/package.json `
  yudao电商管理平台前后端/yudao-ui-admin-vue3/scripts/check-mall-warning-contract.mjs `
  yudao电商管理平台前后端/yudao-ui-admin-vue3/src/views/mall/home/index.vue `
  yudao电商管理平台前后端/yudao-ui-admin-vue3/src/views/mall/statistics/product/components/ProductSummary.vue
git commit -m "fix: normalize mall dashboard display values"
```

---

### Task 5: Full build, runtime, and browser regression

**Files:**
- No production file changes expected.
- Read: `.local-run/jdk17/logs/yudao-server.out.log`

**Interfaces:**
- Verifies the outputs produced by Tasks 1 through 4.

- [ ] **Step 1: Run all repository-scoped contracts**

```powershell
Get-ChildItem .\yudao电商管理平台前后端\yudao-cloud\script\jdk17\tests\*.Tests.ps1 | ForEach-Object {
  powershell -NoProfile -ExecutionPolicy Bypass -File $_.FullName
  if ($LASTEXITCODE -ne 0) { throw "Failed: $($_.Name)" }
}
pnpm --dir .\yudao电商管理平台前后端\yudao-ui-admin-vue3 check:local-ai-routing
pnpm --dir .\yudao电商管理平台前后端\yudao-ui-admin-vue3 check:ai-model-guidance
pnpm --dir .\yudao电商管理平台前后端\yudao-ui-admin-vue3 check:ai-music-player
pnpm --dir .\yudao电商管理平台前后端\yudao-ui-admin-vue3 check:mall-warnings
```

Expected: every command exits zero.

- [ ] **Step 2: Run build verification**

```powershell
.\yudao电商管理平台前后端\yudao-cloud\script\jdk17\Invoke-MavenJdk17.ps1 -MavenArgs @('-pl','yudao-server','-am','test')
pnpm --dir .\yudao电商管理平台前后端\yudao-ui-admin-vue3 ts:check
pnpm --dir .\yudao电商管理平台前后端\yudao-ui-admin-vue3 build:local
```

Expected: Maven `BUILD SUCCESS`, TypeScript success, and Vite build success.

- [ ] **Step 3: Restart the local backend through the supported scripts**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\code\stop-yudao-all-backend.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File D:\code\start-yudao-all-backend.ps1 -Build
```

Expected: one Java listener on 48080, no listener on 48090, health `UP`.

- [ ] **Step 4: Verify logs stay clean**

After loading representative AI pages, scan the new monolith log and require zero matches for:

```text
UnknownHostException: system-server
UnknownHostException: infra-server
xxl-job registry fail
Connection refused.*9090
```

- [ ] **Step 5: Browser smoke test**

With the existing signed-in Chrome session, inspect AI chat, image, write, music, knowledge, workflow, mind-map, all AI management pages, mall home, product pages, orders, after-sale, and product statistics. Do not trigger paid generation, data sync, delete, or save actions.

Expected:

- No “服务器错误，请联系管理员” toast on page load.
- Empty model state shows “AI 模型尚未配置”.
- AI music page has no invalid media property warnings.
- Mall pages have no numeric prop or deprecated `axisLabel.textStyle` warnings.
- Order address fallback warning remains visible and truthful.

- [ ] **Step 6: Review final diff and repository state**

Run `git diff --check`, `git status --short`, and verify only planned files plus pre-existing user files are modified. Do not stage the user's `pnpm-lock.yaml`, SEO module files, or unrelated untracked content unless a planned edit intentionally overlaps and preserves them.
