# RH Layout Extraction Report

> 抽取批次：Batch 001
> 目标网站：`https://rh.com/us/en`
> 技术栈目标：Vue3
> Viewport 目标：`1440 x 900`、`390 x 844`
> 图片策略：先使用占位图，并标注推荐尺寸

## 1. 当前输入

用户提供了一个 `outerHTML` 文件：

```text
C:\Users\admin\Desktop\新建文本文档.txt
```

解析结果显示它是 RH 的 Sale 页面快照：

```text
data-brand: RH
data-page-path: /us/en/sale
data-user-type: ANONYMOUS
HTML size: 171,572 bytes
```

已生成结构化数据：

```text
data/rh-sale/outerhtml-extraction.json
data/rh-sale/navigation-candidates.json
data/rh-sale/image-spec-candidates.json
data/pages/rh-sale-fixture-1440.json
```

## 2. 抽取摘要

从 `outerHTML` 中提取到：

```text
total tags: 1059
unique tags: 36
anchors: 55
img tags: 15
background images: 4
AEM authoring modules: 91
style blocks: 3
landmarks: 3
```

这说明该页面不是普通静态 HTML，而是 RH 的 AEM 内容模块 + React/MUI 应用混合结构。

## 3. Browser Measurement Status

已安装并验证 Playwright：

```text
package: playwright ^1.60.0
browser: chromium installed
```

线上 RH 页面在自动浏览器里触发了校验页，测量结果只返回 1 个 `body` 元素，正文里出现 `var dd=...`。这表示 RH 当前对自动浏览器启用了机器人校验，不能把线上页面作为完整 DOM 坐标来源。

因此 Batch 001 的可靠测量来源切换为用户提供的 `outerHTML` 快照。本地快照包装为：

```text
fixtures/rh-sale-outerhtml.html
```

并完成 `1440 x 900` 测量：

```text
data/pages/rh-sale-fixture-1440.json
captures/source/rh-sale-fixture-1440.png
measured elements: 383
measured images: 14
```

## 4. 已确认页面结构

### Global Shell

- `body#body-root`
- `#promo-banner`
- `#rh-alert-banner`
- `#spa-root`
- `main#main`
- `footer`

### Above Navigation Banner

页面顶部有促销横幅：

```text
THE EARLY SUMMER SALE. RH MEMBERS SAVE UP TO 70%.
HUNDREDS OF NEW ITEMS ADDED. SHOP
```

样式特征：

```text
background: #F1F0ED
font: RHSans-ExtraLight
font-size: 10.75px mobile, 12px desktop
line-height: 15px
text-transform: uppercase
text-align: center
padding: 16px
```

### Sale Page Content

AEM 模块命名显示页面主结构为：

```text
Box 2.0 - Sale Content
Box 2.0 - Sale Hero
Box 2.0 - Spring Clearance
Box 2.0 - Spring
Box 2.0 - On Hundreds
Box 2.0 - Links
Box 2.0 - Living
Box 2.0 - Sofas
Box 2.0 - Dining
Box 2.0 - M - Bed
Box 2.0 - Bathroom Collections
Box 2.0 - M - Outdoor Collection
Box 2.0 - M - Rug Collections
```

这类命名可以直接映射成 Vue3 组件：

```text
SalePage.vue
SaleHero.vue
SaleQuickLinks.vue
SaleCategoryTile.vue
MembershipBanner.vue
GlobalFooter.vue
```

## 5. 公开源站补充信号

通过公开可访问页面确认：

首页主导航包含：

```text
Living
Dining
Bed
Bath
Outdoor
Lighting
Textiles
Rugs
Décor
Baby & Child
Teen
Sale
Interior Design
```

商品列表页结构包含：

```text
page title: All Living Sale
membership promo: RH MEMBERS PROGRAM SAVE 30% ON EVERYTHING*
filters: sale, in-stock, Product Type, Material, Sectional Configuration
sort
results count
product groups
product cards
product image
product name
variant description
member sale / sale / regular price
discount badge
```

商品详情页结构包含：

```text
image/gallery area
caption
product title
starting price
member sale / regular price
product description
related links
newsletter
footer
```

## 6. 当前限制

这次用户提供的是 Sale 页 `outerHTML`，不是首页完整 `outerHTML`。因此：

- Sale 页可以进入较完整的结构抽取。
- 首页、一级导航页、商品列表页、商品详情页目前以公开源站文本结构为主。
- 线上 RH 页面当前触发机器人校验，不能直接拿到完整 React DOM 坐标。
- Sale 页已可通过本地 `outerHTML` fixture 测量部分 `x / y / width / height`。
- 本地 fixture 缺少源站完整运行态上下文，因此它是“快照测量”，不是最终线上像素验收。

## 7. 下一步抽取顺序

建议按这个顺序继续：

1. Sale 页：用当前 `outerHTML` 完成结构和图片区域规格。
2. 首页：自动渲染源站，抓首屏、导航和 World of RH 模块。
3. 一级导航页：Living、Dining、Bed、Bath、Outdoor、Lighting、Textiles、Rugs、Décor。
4. 商品列表页：以 `All Living Sale` 为第一模板。
5. 商品详情页：以 `Cloud Bench-Cushion Sofa` 为第一模板。
6. 移动端：重复 1 到 5，viewport 使用 `390 x 844`。

## 8. Vue3 组件拆分建议

```text
src/
  app/
    App.vue
  layouts/
    RhLayout.vue
  components/
    RhHeader.vue
    RhPromoBanner.vue
    RhFooter.vue
    ImageSpecPlaceholder.vue
    ProductCard.vue
    ProductGrid.vue
    FilterRail.vue
    SaleHero.vue
    SaleCategoryTile.vue
  pages/
    HomePage.vue
    SalePage.vue
    CategoryLandingPage.vue
    ProductListingPage.vue
    ProductDetailPage.vue
```

## 9. 验收重点

- 桌面端以 `1440 x 900` 为主要基准。
- 移动端以 `390 x 844` 为主要基准。
- 图片区域先用占位图，但必须显示推荐比例和推荐分辨率。
- Header、促销条、页面主内容和 Footer 必须先稳定。
- 商品卡片必须保留商品名、描述、会员价、原价、折扣标签结构。
