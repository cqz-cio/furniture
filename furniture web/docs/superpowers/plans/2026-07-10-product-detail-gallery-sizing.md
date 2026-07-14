# 商品详情页展示图尺寸调整实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将所有商品详情页的桌面端主图高度统一调整为 420-520px，并保持移动端现有的 360px 高度和全部画廊交互。

**Architecture:** 继续复用唯一的商品详情页组件 `SofaPdpPage.vue`，只修改共用样式 `.product-gallery-main`，不增加商品级分支。先以源码约束测试锁定桌面端和移动端尺寸，再修改 CSS，最后通过构建和浏览器截图完成视觉验证。

**Tech Stack:** Vue 3、CSS、Vitest、Vite、Playwright/浏览器验证

## Global Constraints

- 桌面端主图高度必须在 420-520px 之间响应式变化。
- 屏幕宽度不超过 900px 时继续使用 360px 主图高度。
- 主图使用 `object-fit: contain`，不得裁切商品主体。
- 不改变图片切换、箭头、缩略图、商品信息、价格或购买行为。
- 所有通过 `SofaPdpPage.vue` 展示的商品详情页统一生效。

---

### Task 1: 锁定并实现统一的主图尺寸规则

**Files:**
- Create: `tests/productDetailGalleryLayout.test.js`
- Modify: `src/styles.css:4076-4079`
- Reference: `src/pages/SofaPdpPage.vue:357-413`

**Interfaces:**
- Consumes: `.product-gallery-main` 共用选择器和 `@media (max-width: 900px)` 移动端覆盖规则。
- Produces: 桌面端 `height: clamp(420px, 36vw, 520px)` 和保持不变的移动端 `height: 360px`。

- [ ] **Step 1: 编写失败测试**

```js
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const css = readFileSync(new URL("../src/styles.css", import.meta.url), "utf8").replace(/\r\n/g, "\n");

const readCssBlock = (selector, source = css) => {
  const start = source.indexOf(`${selector} {`);
  const end = source.indexOf("\n}", start);
  return source.slice(start, end + 2);
};

describe("product detail gallery layout", () => {
  it("caps the shared desktop gallery between 420px and 520px", () => {
    const gallery = readCssBlock(".product-gallery-main");
    expect(gallery).toContain("height: clamp(420px, 36vw, 520px);");
    expect(gallery).toContain("aspect-ratio: 4 / 3;");
  });

  it("keeps the existing mobile gallery height", () => {
    const mobile = css.slice(css.indexOf("@media (max-width: 900px)"));
    const gallery = readCssBlock(".product-gallery-main", mobile);
    expect(gallery).toContain("height: 360px;");
    expect(gallery).toContain("aspect-ratio: auto;");
  });
});
```

- [ ] **Step 2: 运行测试并确认先失败**

Run: `npm test -- tests/productDetailGalleryLayout.test.js`

Expected: FAIL，桌面端样式仍为 `height: clamp(520px, 48vw, 680px)`。

- [ ] **Step 3: 修改最小范围的共用样式**

将 `src/styles.css` 中桌面端规则改为：

```css
.product-gallery-main {
  height: clamp(420px, 36vw, 520px);
  min-height: 0;
  aspect-ratio: 4 / 3;
  margin: 0;
  display: grid;
  place-items: center;
  background: #f3f1ec;
  overflow: hidden;
  outline: none;
  position: relative;
}
```

不修改现有的 `@media (max-width: 900px)` 中以下规则：

```css
.product-gallery-main {
  height: 360px;
  aspect-ratio: auto;
}
```

- [ ] **Step 4: 运行定向测试并确认通过**

Run: `npm test -- tests/productDetailGalleryLayout.test.js`

Expected: 2 tests passed。

- [ ] **Step 5: 运行回归测试与生产构建**

Run: `npm test -- tests/productDetailModel.test.js tests/productDetailGalleryLayout.test.js`

Expected: 所有定向测试通过。

Run: `npm run build`

Expected: Vite 构建成功且无错误。

- [ ] **Step 6: 提交尺寸规则和测试**

```bash
git add tests/productDetailGalleryLayout.test.js src/styles.css
git commit -m "fix: resize product detail gallery"
```

### Task 2: 浏览器视觉验证

**Files:**
- Create: `design-qa.md`
- Inspect: `src/pages/SofaPdpPage.vue`
- Inspect: `src/styles.css`

**Interfaces:**
- Consumes: Task 1 产出的统一主图尺寸规则。
- Produces: 桌面端两个商品页和移动端一个商品页的验证记录，最终状态为 `final result: passed`。

- [ ] **Step 1: 启动本地站点**

Run: `npm run dev -- --host 127.0.0.1 --port 5173`

Expected: Vite 在 5173 端口启动。

- [ ] **Step 2: 验证两个桌面端商品详情页**

在 1920x950 视口依次打开：

```text
http://127.0.0.1:5173/product?id=1001
http://127.0.0.1:5173/product?id=1002
```

Expected: 两个页面主图高度均不超过 520px，主图顶部与右侧商品信息顶部对齐，缩略图和说明紧随主图下方。

- [ ] **Step 3: 验证移动端商品详情页**

在 390x844 视口打开：

```text
http://127.0.0.1:5173/product?id=1002
```

Expected: 页面为单栏，主图高度为 360px，无水平滚动，左右箭头和缩略图可用。

- [ ] **Step 4: 写入视觉 QA 结果**

创建 `design-qa.md`，包含参考截图、验证视口、商品 ID、主图实测尺寸、对齐情况和交互结果。所有 P0/P1/P2 问题解决后，以以下内容结束：

```text
final result: passed
```

- [ ] **Step 5: 提交视觉验证记录**

```bash
git add design-qa.md
git commit -m "test: verify product detail gallery layout"
```
