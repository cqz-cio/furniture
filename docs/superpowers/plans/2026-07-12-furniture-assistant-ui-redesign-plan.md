# Furniture Assistant UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有家具导购浮层改造成 B 方案“高级空间顾问”，并完善默认停靠、边缘吸附、快捷问题、消息层级、输入反馈和移动端底部抽屉。

**Architecture:** 保留 `FurnitureAssistantPanel.vue` 的聊天 API、会话恢复和商品行为，将可独立测试的位置计算抽到纯函数模块。组件负责状态、焦点、滚动和模板结构，`styles.css` 负责桌面/移动端视觉与动效，`i18n.js` 负责所有用户可见文案。

**Tech Stack:** Vue 3 Composition API、Vite、Vitest、Lucide Vue Next、原生 Pointer Events、CSS media queries。

## Global Constraints

- 不改变后端聊天 API、Redis 会话恢复协议、商品推荐响应结构和 `add-to-cart` 事件接口。
- 桌面默认面板约 460 × 660px，右下安全距离 24px；交互目标不小于 40 × 40px。
- 320px 宽度下不得横向溢出；移动端使用底部抽屉并禁用自由拖动和八方向缩放。
- 视觉使用深橄榄、暖白和灰褐中性色，不引入新的 UI 框架。
- 动画必须遵循 `prefers-reduced-motion`。

---

### Task 1: 可测试的默认停靠与边缘吸附

**Files:**
- Create: `furniture web/src/utils/assistantFloatingPosition.js`
- Create: `furniture web/tests/assistantFloatingPosition.test.js`
- Modify: `furniture web/src/components/FurnitureAssistantPanel.vue`

**Interfaces:**
- Produces: `getDefaultLauncherPosition(viewport, size, margin)`、`getDefaultPanelState(viewport, options)`、`snapHorizontalPosition(left, width, viewportWidth, margin)`。
- Consumes: `{ width: number, height: number }` 视口对象和面板/按钮尺寸。

- [ ] **Step 1: 写默认位置和吸附的失败测试**

```js
import { describe, expect, it } from "vitest";
import {
  getDefaultLauncherPosition,
  getDefaultPanelState,
  snapHorizontalPosition,
} from "../src/utils/assistantFloatingPosition.js";

describe("assistant floating position", () => {
  it("places launcher and panel in the bottom-right safe area", () => {
    expect(getDefaultLauncherPosition({ width: 1365, height: 918 }, 56, 24))
      .toEqual({ left: 1285, top: 838 });
    expect(getDefaultPanelState({ width: 1365, height: 918 }, {
      width: 460, height: 660, margin: 24,
    })).toEqual({ left: 881, top: 234, width: 460, height: 660 });
  });

  it("snaps to the nearest horizontal edge", () => {
    expect(snapHorizontalPosition(120, 460, 1365, 24)).toBe(24);
    expect(snapHorizontalPosition(760, 460, 1365, 24)).toBe(881);
  });
});
```

- [ ] **Step 2: 运行测试并确认因模块不存在而失败**

Run: `npm test -- assistantFloatingPosition.test.js`
Expected: FAIL，提示无法解析 `assistantFloatingPosition.js`。

- [ ] **Step 3: 实现最小位置计算模块**

```js
export const getDefaultLauncherPosition = (viewport, size = 56, margin = 24) => ({
  left: Math.max(margin, viewport.width - size - margin),
  top: Math.max(margin, viewport.height - size - margin),
});

export const getDefaultPanelState = (viewport, options = {}) => {
  const margin = options.margin ?? 24;
  const width = Math.min(options.width ?? 460, viewport.width - margin * 2);
  const height = Math.min(options.height ?? 660, viewport.height - margin * 2);
  return {
    left: Math.max(margin, viewport.width - width - margin),
    top: Math.max(margin, viewport.height - height - margin),
    width,
    height,
  };
};

export const snapHorizontalPosition = (left, width, viewportWidth, margin = 24) => {
  const right = Math.max(margin, viewportWidth - width - margin);
  return left + width / 2 <= viewportWidth / 2 ? margin : right;
};
```

- [ ] **Step 4: 在组件中使用纯函数并在拖动结束时吸附**

```js
import {
  getDefaultLauncherPosition,
  getDefaultPanelState,
  snapHorizontalPosition,
} from "../utils/assistantFloatingPosition.js";

const snapPanelToEdge = () => setPanelState({
  ...panelState.value,
  left: snapHorizontalPosition(
    panelState.value.left,
    panelState.value.width,
    viewportSize().width,
    PANEL_MARGIN,
  ),
});
```

将 `LAUNCHER_SIZE/PANEL_DEFAULT_WIDTH/PANEL_DEFAULT_HEIGHT/PANEL_MARGIN` 分别改为 `56/460/660/24`，默认位置使用纯函数；`endDrag` 和 `endPanelDrag` 在清理状态前执行水平吸附。

- [ ] **Step 5: 运行位置测试和现有面板测试**

Run: `npm test -- assistantFloatingPosition.test.js furnitureAssistantPanel.test.js`
Expected: 两个测试文件全部 PASS。

- [ ] **Step 6: 提交任务**

```bash
git add "furniture web/src/utils/assistantFloatingPosition.js" "furniture web/tests/assistantFloatingPosition.test.js" "furniture web/src/components/FurnitureAssistantPanel.vue"
git commit -m "feat(ui): dock furniture assistant to viewport edges"
```

### Task 2: 高级顾问头部、Lucide 图标和焦点行为

**Files:**
- Modify: `furniture web/package.json`
- Modify: `furniture web/package-lock.json`
- Modify: `furniture web/src/components/FurnitureAssistantPanel.vue`
- Modify: `furniture web/src/i18n.js`
- Modify: `furniture web/tests/furnitureAssistantPanel.test.js`

**Interfaces:**
- Produces: 从 `lucide-vue-next` 使用 `Sofa`、`Sparkles`、`SquarePen`、`ChevronDown`、`ArrowUp` 组件。
- Consumes: 图标仅作装饰时设置 `aria-hidden="true"`，由父按钮提供 `aria-label`。

- [ ] **Step 1: 更新结构测试，使旧机器人和文字按钮先失败**

```js
it("uses the premium concierge identity and compact header actions", () => {
  const source = readSource("../src/components/FurnitureAssistantPanel.vue");
  expect(source).toContain('from "lucide-vue-next"');
  expect(source).toContain("Sofa");
  expect(source).toContain("SquarePen");
  expect(source).toContain("ChevronDown");
  expect(source).toContain('ref="launcherButton"');
  expect(source).toContain('ref="composerInput"');
  expect(source).not.toContain("assistant-avatar-icon");
  expect(source).not.toContain('{{ t("assistant.newConversation") }}');
});
```

- [ ] **Step 2: 运行面板测试并确认新结构缺失而失败**

Run: `npm test -- furnitureAssistantPanel.test.js`
Expected: FAIL，缺少 Lucide 图标和新焦点引用。

- [ ] **Step 3: 安装并引入统一图标库**

```bash
npm install lucide-vue-next
```

```js
import { ArrowUp, ChevronDown, Sofa, Sparkles, SquarePen } from "lucide-vue-next";
```

- [ ] **Step 4: 重构头部和焦点恢复**

在 launcher、头部和发送按钮中使用 Lucide 组件；沙发图标右上角用独立 `Sparkles` 组件形成品牌组合，不绘制自定义 SVG。头部标题改为 `t("assistant.conciergeTitle")`，状态改为 `t("assistant.conciergeStatus")`。添加：

```js
const launcherButton = ref(null);
const composerInput = ref(null);

const openPanel = async () => {
  setPanelState(defaultPanelState());
  open.value = true;
  await nextTick();
  composerInput.value?.focus();
};

const closePanel = async () => {
  open.value = false;
  await nextTick();
  launcherButton.value?.focus();
};
```

- [ ] **Step 5: 为所有语言增加必要键值**

为每个 `assistant` 语言块添加 `conciergeTitle`、`conciergeStatus`、`newConversationLabel`、`collapseLabel`、`launcherHint`；中文分别为“空间设计助手”“AI 家居顾问 · 随时在线”“新建对话”“收起对话”“问问空间顾问”。

- [ ] **Step 6: 运行面板和多语言测试**

Run: `npm test -- furnitureAssistantPanel.test.js i18n.test.js`
Expected: 全部 PASS。

- [ ] **Step 7: 提交任务**

```bash
git add "furniture web/package.json" "furniture web/package-lock.json" "furniture web/src/components/FurnitureAssistantPanel.vue" "furniture web/src/i18n.js" "furniture web/tests/furnitureAssistantPanel.test.js"
git commit -m "feat(ui): add premium assistant identity"
```

### Task 3: 快捷问题、自动滚动和紧凑消息流

**Files:**
- Modify: `furniture web/src/components/FurnitureAssistantPanel.vue`
- Modify: `furniture web/src/i18n.js`
- Modify: `furniture web/tests/furnitureAssistantPanel.test.js`

**Interfaces:**
- Produces: `quickPrompts: Array<{ id: string, label: string, prompt: string }>`、`submitMessage(message: string): Promise<void>`、`scrollThreadToLatest(): Promise<void>`。
- Consumes: 现有 `sendFurnitureAssistantMessage` 和 `conversationId`。

- [ ] **Step 1: 写快捷问题和自动滚动的失败结构测试**

```js
it("offers quick prompts and follows the latest assistant message", () => {
  const source = readSource("../src/components/FurnitureAssistantPanel.vue");
  expect(source).toContain("const quickPrompts = computed(() => [");
  expect(source).toContain("const submitMessage = async (message) =>");
  expect(source).toContain("const scrollThreadToLatest = async () =>");
  expect(source).toContain('ref="threadElement"');
  expect(source).toContain('v-for="prompt in quickPrompts"');
  expect(source).toContain('@click="submitMessage(prompt.prompt)"');
  expect(source).toContain("threadElement.value.scrollTop = threadElement.value.scrollHeight");
});
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `npm test -- furnitureAssistantPanel.test.js`
Expected: FAIL，缺少快捷问题和滚动函数。

- [ ] **Step 3: 抽取统一发送入口并添加快捷问题**

```js
const quickPrompts = computed(() => [
  { id: "living-room", label: t("assistant.quickLivingRoom"), prompt: t("assistant.quickLivingRoomPrompt") },
  { id: "budget", label: t("assistant.quickBudget"), prompt: t("assistant.quickBudgetPrompt") },
  { id: "image", label: t("assistant.quickImage"), prompt: t("assistant.quickImagePrompt") },
]);

const submitDraft = () => submitMessage(draftMessage.value);
```

将现有 `submitDraft` 主体移动到 `submitMessage(message)`，在清空输入、追加用户消息、进入加载、追加助手消息和捕获错误后调用 `scrollThreadToLatest()`。

- [ ] **Step 4: 实现滚动与连续消息简化**

```js
const threadElement = ref(null);
const scrollThreadToLatest = async () => {
  await nextTick();
  if (!threadElement.value) return;
  threadElement.value.scrollTop = threadElement.value.scrollHeight;
};
```

模板仅在助手消息序列的第一条显示头像；移除每条普通消息的名称和“刚刚”，保留错误和推荐区域的可辨识标签。

- [ ] **Step 5: 增加快捷问题文案并运行测试**

为每个语言块增加三个快捷入口标签和 prompt；中文 prompt 使用完整自然问题。
Run: `npm test -- furnitureAssistantPanel.test.js i18n.test.js`
Expected: 全部 PASS。

- [ ] **Step 6: 提交任务**

```bash
git add "furniture web/src/components/FurnitureAssistantPanel.vue" "furniture web/src/i18n.js" "furniture web/tests/furnitureAssistantPanel.test.js"
git commit -m "feat(ui): streamline assistant conversations"
```

### Task 4: 推荐依据折叠区与发送状态

**Files:**
- Modify: `furniture web/src/components/FurnitureAssistantPanel.vue`
- Modify: `furniture web/src/i18n.js`
- Modify: `furniture web/tests/furnitureAssistantPanel.test.js`

**Interfaces:**
- Produces: 原生 `<details class="assistant-evidence">` 和 `assistant-thinking-dots`。
- Consumes: 现有 `assistantSources`、`isSubmitting`、`assistantProducts`。

- [ ] **Step 1: 写折叠依据和思考状态的失败测试**

```js
it("groups recommendation evidence and renders a thinking state", () => {
  const source = readSource("../src/components/FurnitureAssistantPanel.vue");
  expect(source).toContain('<details v-if="assistantSources.length" class="assistant-evidence">');
  expect(source).toContain('t("assistant.evidenceLabel")');
  expect(source).toContain("assistant-thinking-dots");
  expect(source).not.toContain("assistant-chat-sources");
});
```

- [ ] **Step 2: 运行测试并确认旧来源消息导致失败**

Run: `npm test -- furnitureAssistantPanel.test.js`
Expected: FAIL，仍存在 `assistant-chat-sources`。

- [ ] **Step 3: 用原生 details 合并推荐依据**

```vue
<details v-if="assistantSources.length" class="assistant-evidence">
  <summary>{{ t("assistant.evidenceLabel") }}</summary>
  <div class="assistant-source-list">
    <span v-for="source in assistantSources" :key="`${source.type}-${source.name}`" class="assistant-source-chip">
      <span>{{ source.type }}</span>{{ source.name }}
    </span>
  </div>
</details>
```

- [ ] **Step 4: 用三点状态替换文字 loading**

```vue
<span class="assistant-thinking-dots" role="status" :aria-label="t('assistant.loading')">
  <i></i><i></i><i></i>
</span>
```

发送按钮在 `isSubmitting` 时保留禁用状态并显示同一忙碌语义，避免重复提交。

- [ ] **Step 5: 增加 `evidenceLabel` 文案并运行测试**

Run: `npm test -- furnitureAssistantPanel.test.js i18n.test.js`
Expected: 全部 PASS。

- [ ] **Step 6: 提交任务**

```bash
git add "furniture web/src/components/FurnitureAssistantPanel.vue" "furniture web/src/i18n.js" "furniture web/tests/furnitureAssistantPanel.test.js"
git commit -m "feat(ui): condense assistant recommendation evidence"
```

### Task 5: B 方案视觉、移动端抽屉和动效

**Files:**
- Modify: `furniture web/src/styles.css`
- Modify: `furniture web/src/components/FurnitureAssistantPanel.vue`
- Modify: `furniture web/tests/furnitureAssistantPanel.test.js`

**Interfaces:**
- Produces: `.assistant-panel-enter-active`、`.assistant-launcher-hint`、移动端 `@media (max-width: 640px)` 和 `prefers-reduced-motion` 规则。
- Consumes: 前四个任务确定的模板类名。

- [ ] **Step 1: 写视觉契约的失败测试**

```js
it("styles the premium concierge panel and mobile bottom sheet", () => {
  const styles = readSource("../src/styles.css");
  expect(styles).toContain(".assistant-launcher-hint");
  expect(styles).toContain(".assistant-panel-enter-active");
  expect(styles).toContain("@media (max-width: 640px)");
  expect(styles).toContain("height: min(85dvh, 720px);");
  expect(styles).toContain("@media (prefers-reduced-motion: reduce)");
  expect(styles).toContain("min-width: 40px;");
});
```

- [ ] **Step 2: 运行面板测试并确认视觉规则缺失**

Run: `npm test -- furnitureAssistantPanel.test.js`
Expected: FAIL，缺少 B 方案样式契约。

- [ ] **Step 3: 重写对话框核心视觉规则**

将头部设置为深橄榄背景和暖白文字；面板圆角 16px；消息区暖白；用户气泡深橄榄；按钮点击区至少 40px；输入区变为统一圆角条。删除旧头像、时间和来源独立消息样式，保留商品卡片相关样式。

- [ ] **Step 4: 添加展开收起与提示动效**

用 Vue `<Transition name="assistant-panel">` 包裹面板，CSS 设置 160–220ms 的 opacity/transform 过渡；悬浮按钮旁增加 `.assistant-launcher-hint`，只在首次加载的短时间窗口显示。

- [ ] **Step 5: 添加移动端与减少动效规则**

```css
@media (max-width: 640px) {
  .furniture-assistant-panel {
    inset: auto 0 0 !important;
    width: 100% !important;
    height: min(85dvh, 720px);
    max-width: none;
    border-radius: 18px 18px 0 0;
  }
  .furniture-assistant-resize-handle { display: none; }
}

@media (prefers-reduced-motion: reduce) {
  .assistant-panel-enter-active,
  .assistant-panel-leave-active,
  .assistant-thinking-dots i { animation: none; transition: none; }
}
```

移动端通过 `matchMedia("(max-width: 640px)")` 阻止 `startPanelDrag` 和 `startPanelResize`。

- [ ] **Step 6: 运行前端完整测试与生产构建**

Run: `npm test`
Expected: 所有 Vitest 测试 PASS。
Run: `npm run build`
Expected: Vite build exit code 0。

- [ ] **Step 7: 提交任务**

```bash
git add "furniture web/src/styles.css" "furniture web/src/components/FurnitureAssistantPanel.vue" "furniture web/tests/furnitureAssistantPanel.test.js"
git commit -m "feat(ui): polish furniture assistant experience"
```

### Task 6: 浏览器交互与视觉验收

**Files:**
- Modify if defects are found: `furniture web/src/components/FurnitureAssistantPanel.vue`
- Modify if defects are found: `furniture web/src/styles.css`
- Test: `furniture web/tests/furnitureAssistantPanel.test.js`

**Interfaces:**
- Consumes: 运行中的本地前端和后端。
- Produces: 桌面展开、收起/悬浮、移动端展开三种已检查状态。

- [ ] **Step 1: 启动指向可用 Agent API 的前端**

Run: `$env:VITE_FURNITURE_ASSISTANT_MODE="api"; $env:VITE_YUDAO_APP_API_BASE="http://127.0.0.1:48081/app-api"; npm run dev -- --port 5175 --strictPort`
Expected: 本地页面可访问且无启动错误。

- [ ] **Step 2: 在 1365 × 918 视口验证桌面状态**

检查默认右下位置、顶部按钮无重叠、输入焦点、发送消息、模型思考状态、来源折叠、商品轮播、收起后会话保持。

- [ ] **Step 3: 验证拖动与持久化**

拖动悬浮按钮到左半边并松手，确认吸附左侧；刷新后确认位置恢复。拖动面板到右半边，确认吸附右侧且不越界。

- [ ] **Step 4: 在 390 × 844 和 320 × 700 视口验证移动端**

确认面板为底部抽屉、无横向滚动、顶部操作和输入区可见、缩放手柄隐藏、拖动不会移出屏幕。

- [ ] **Step 5: 检查控制台并修复发现的问题**

控制台不得出现 Vue warning、未捕获异常或资源加载失败。若修改代码，先为缺陷补充失败测试，再实现最小修复并重跑相关测试。

- [ ] **Step 6: 最终验证**

Run: `npm test && npm run build`（PowerShell 中分别执行并检查退出码）
Expected: 测试 0 failures，构建 exit code 0。

- [ ] **Step 7: 提交验收修复（仅有修改时）**

```bash
git add "furniture web/src/components/FurnitureAssistantPanel.vue" "furniture web/src/styles.css" "furniture web/tests/furnitureAssistantPanel.test.js"
git commit -m "fix(ui): address assistant visual QA findings"
```
