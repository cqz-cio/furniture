# 商品详情页主图适配与对齐修复实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让所有商品详情页主图与缩略图栏对齐，并完整显示家具主体。

**Architecture:** 只修改共享的商品画廊 CSS。主图容器保持统一高度但占满左栏；图片改为绝对定位并以 `cover` 填充，从素材上下空白中裁切，避免网格百分比高度导致的溢出裁切。

**Tech Stack:** Vue 3、CSS、Vitest、Vite

## Global Constraints

- 桌面端主图保持 `clamp(420px, 36vw, 520px)` 高度。
- 移动端主图保持 360px 高度。
- 主图和缩略图栏必须使用同一左栏宽度。
- 所有 `SofaPdpPage.vue` 商品详情页使用同一规则。

---

### Task 1: 修复共享主图布局与图片适配

**Files:**
- Modify: `src/styles.css:4076-4087,4322-4328`
- Modify: `tests/productDetailGalleryLayout.test.js`

**Interfaces:**
- Consumes: `.product-gallery-main`、`.product-gallery-main img`、`.product-gallery-thumbs` 共享选择器。
- Produces: 左栏全宽、主图内容不溢出、裁掉素材留白但完整保留家具主体的统一画廊。

- [ ] **Step 1: 写入失败测试**

在 `tests/productDetailGalleryLayout.test.js` 中断言：

```js
expect(gallery).toContain("width: 100%;");
expect(gallery).toContain("aspect-ratio: auto;");
expect(galleryImage).toContain("position: absolute;");
expect(galleryImage).toContain("inset: 0;");
expect(galleryImage).toContain("object-fit: cover;");
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `npm.cmd test -- tests/productDetailGalleryLayout.test.js`

Expected: FAIL，因为现有主图仍有 `aspect-ratio: 4 / 3`，图片仍使用 `object-fit: contain`。

- [ ] **Step 3: 最小化修改共享样式**

```css
.product-gallery-main {
  width: 100%;
  aspect-ratio: auto;
}

.product-gallery-main img {
  position: absolute;
  inset: 0;
  object-fit: cover;
}
```

保留 `height: clamp(420px, 36vw, 520px)`、移动端 360px 高度、左右箭头、缩略图和图片切换逻辑。

- [ ] **Step 4: 运行定向测试和构建**

Run: `npm.cmd test -- tests/productDetailModel.test.js tests/productDetailGalleryLayout.test.js`

Expected: 所有测试通过。

Run: `npm.cmd run build`

Expected: Vite 构建成功。

- [ ] **Step 5: 浏览器验证**

在 `http://127.0.0.1:5173/product?id=1002` 和 `?id=1001` 的 1920px 视口确认：主图和缩略图同宽、图片渲染高度等于容器高度、家具主体完整显示。再在 390px 视口确认移动端 360px 规则和无横向滚动。
