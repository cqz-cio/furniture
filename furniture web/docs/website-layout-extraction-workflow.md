# Website Layout Extraction Workflow

> 适用项目：家具/家居类网站前端布局抽取、规格化、复刻与视觉验收。
> 当前目标站示例：`https://rh.com/us/en`。
> 工作原则：以浏览器真实渲染为准，以自动化抽取为主，以人工材料兜底。

## 1. 目标

本项目要建立一套可重复执行的前端布局抽取流程，用来从目标网站提取页面结构、元素尺寸、样式规则、图片区域规格和响应式布局，并最终支持生成可运行的前端页面。

核心要求：

- 样式抽取要完整，不能只依赖单个元素的 `Copy styles`。
- 布局位置要精确到可复刻的 `x / y / width / height`。
- 图片区域要单独标注容器尺寸、推荐比例、推荐分辨率、推荐格式和文件大小。
- 所有结论要能通过源站截图、本地截图和规格数据互相验证。

## 2. 最终推荐方案

最高效方案是：用户提供目标 URL、页面范围、目标 viewport 和技术栈；Codex 使用浏览器自动化真实渲染源站，自动截图、抽取 DOM、computed styles、元素坐标、图片信息，再生成规格文档和前端实现，并通过源站截图与本地页面截图做视觉对比。

用户不需要手动截很长的网页截图。截图由自动化浏览器完成。

用户也不需要逐个复制 `Copy styles`。`Copy styles` 只适合单元素排查，不能作为整站抽取主流程。

## 3. 用户输入规范

每次开始抽取任务前，用户应提供：

```text
目标网站 URL：
需要抽取的页面范围：
目标技术栈：
桌面端 viewport：
移动端 viewport：
是否需要登录：
是否需要复刻交互状态：
是否允许使用占位图：
```

推荐示例：

```text
目标网站 URL：https://rh.com/us/en
需要抽取的页面范围：首页、导航点击后的一级分类页、商品列表页、商品详情页、Sale 页
目标技术栈：Vue3 / React / HTML CSS / UniApp
桌面端 viewport：1440 x 900
移动端 viewport：390 x 844
是否需要登录：否
是否需要复刻交互状态：导航展开、筛选抽屉、移动端菜单
是否允许使用占位图：允许，但要标注推荐图片规格
```

如果网站存在登录、地区限制、验证码、反爬或本地状态差异，用户应补充：

- 当前页面主容器的 `Copy outerHTML`，优先选择 `body`、`#app`、`#root`、`#__next`、`main`。
- 特殊状态截图，例如菜单展开、弹窗打开、筛选后的结果页。
- 测试账号或可公开访问的替代页面。

## 4. 页面范围抽取规则

页面范围分为四级。

Level 1：全局页面骨架

- Header
- Desktop navigation
- Mobile navigation
- Footer
- Newsletter
- Region selector
- Account / Cart / Search entry

Level 2：主页面类型

- Home page
- Category landing page
- Product listing page
- Product detail page
- Sale page
- Search result page
- Interior design / service page
- Gallery / store location page

Level 3：交互状态

- 导航 hover / click 展开
- 移动端 hamburger menu
- 筛选抽屉
- 排序菜单
- 商品配置选项
- 图片轮播 / gallery
- 加购状态
- 登录或订阅弹窗

Level 4：异常与边界状态

- 空搜索结果
- 图片加载失败
- 商品无库存
- 折扣价 / 会员价 / 原价同时出现
- 长标题换行
- 移动端文本溢出

首次实施时优先完成 Level 1 和 Level 2；后续再补 Level 3 和 Level 4。

## 5. 自动化抽取流程

1. 打开源站
   - 使用指定 viewport 真实渲染页面。
   - 等待首屏内容、关键图片和字体加载。
   - 记录 URL、页面标题、viewport、时间戳。

2. 采集截图
   - 首屏截图。
   - 整页长截图。
   - 关键模块截图。
   - 移动端截图。

3. 抽取 DOM 与布局
   - 获取 DOM 层级。
   - 为可见元素生成稳定 selector。
   - 获取 `getBoundingClientRect()`。
   - 记录 `x / y / width / height`。
   - 记录层级关系和父子容器关系。

4. 抽取 computed styles
   - display / position / box-sizing
   - margin / padding / gap
   - font-family / font-size / line-height / font-weight
   - color / background
   - border / radius / shadow
   - grid / flex
   - object-fit / object-position
   - z-index / overflow

5. 抽取图片规格
   - 图片 URL。
   - 渲染尺寸。
   - 原始尺寸。
   - 容器尺寸。
   - 显示比例。
   - 裁切方式。
   - 推荐 1x / 2x / 3x 分辨率。
   - 推荐格式和文件大小。

6. 生成规格文档
   - 页面级结构。
   - 模块级布局。
   - 组件级样式。
   - 图片区域规格表。
   - 响应式差异表。

7. 生成前端实现
   - 按目标技术栈创建页面。
   - 抽象全局布局、导航、卡片、图片容器和页脚。
   - 图片未知时使用规格占位图。
   - 占位图必须显示推荐尺寸、比例和格式。

8. 视觉验收
   - 启动本地页面。
   - 截取本地页面同 viewport 图片。
   - 与源站截图对比。
   - 修正间距、字体、尺寸、裁切和响应式问题。

## 5.1 Console 抽取脚本

如果 RH 线上页面对自动浏览器触发校验，则使用用户本机浏览器 Console 抽取。

脚本位置：

```text
tools/rh-console-layout-export.js
```

操作说明：

```text
docs/console-extraction-guide.md
```

原则：

```text
一个页面 + 一个状态 + 一个 viewport = 一份 JSON
```

例如 Sale 桌面、Sale 移动端、首页桌面、商品列表页桌面、商品详情页桌面都需要分别导出。

## 6. 图片区域规格标准

每个图片区域必须输出以下字段：

```text
页面：
模块：
区域名称：
selector：
容器位置：x, y
容器尺寸：width x height
显示比例：
原图尺寸：
推荐 1x 分辨率：
推荐 2x 分辨率：
推荐 3x 分辨率：
推荐格式：
推荐文件大小：
object-fit：
object-position：
是否允许裁切：
备注：
```

推荐规则：

- Hero / full-width banner：优先 WebP，2x 宽度，控制在 250KB 到 600KB。
- 商品卡片图：优先 WebP，2x 宽度，控制在 80KB 到 180KB。
- 小图标：优先 SVG；复杂图标可使用 WebP / PNG。
- 透明图：使用 PNG 或 WebP alpha。
- 摄影图：优先 WebP；兼容要求高时补 JPG。

分辨率建议：

```text
推荐 1x = 容器 CSS 像素尺寸
推荐 2x = 容器 CSS 像素尺寸 * 2
推荐 3x = 仅移动端高精图或关键视觉使用
```

## 7. DevTools 手动材料规范

当自动化访问不完整时，用户提供 DevTools 材料。

优先级：

1. `Copy outerHTML`
   - 用于获取结构。
   - 优先复制 `body`、`#app`、`#root`、`#__next`、`main` 或关键模块。

2. `Copy styles`
   - 用于补充单个关键元素样式。
   - 适合 header、hero、商品卡片、按钮、图片容器。
   - 不适合整页样式导出。

3. 截图
   - 只需要特殊状态或自动化无法访问的页面。
   - 长页面不要求用户手动截整页。

4. 图片地址
   - 可选。
   - 如果涉及版权或防盗链，只记录规格，不复用原图。

不推荐单独提供：

- `Copy selector`
- `Copy JS path`
- `Copy XPath`
- `Copy full XPath`

这些只能辅助定位，不能替代结构、样式和截图。

## 8. 交付物结构

建议项目目录：

```text
docs/
  website-layout-extraction-workflow.md
  extraction-report.md
  image-specs.md
  responsive-specs.md
captures/
  source/
  local/
  diff/
data/
  extracted-layout.json
  extracted-images.json
src/
  components/
  pages/
```

每次页面抽取至少交付：

- `docs/extraction-report.md`
- `docs/image-specs.md`
- `data/extracted-layout.json`
- 源站截图
- 本地复刻截图
- 可运行前端代码

## 9. 验收标准

页面验收以指定 viewport 为准。

桌面端建议：

- `1440 x 900`
- `1920 x 1080`

移动端建议：

- `390 x 844`
- `430 x 932`

验收检查：

- Header 高度和导航间距一致。
- 首屏视觉比例一致。
- 主要模块顺序一致。
- 图片容器比例一致。
- 商品卡片宽度、间距、文字层级一致。
- 页脚信息层级一致。
- 移动端无横向滚动。
- 文本没有溢出或重叠。
- 图片占位信息清晰可替换。

允许误差建议：

- 关键容器尺寸：不超过 2 到 4 px。
- 普通文本间距：不超过 4 到 8 px。
- 长页面累计高度：按模块独立对比，不只看整页总高度。

## 10. RH 类家居网站布局要点

RH 这类高端家居站不是普通货架式电商，而是品牌画廊式电商。

复刻时应重点保留：

- 极简顶部导航。
- 大图主导的首屏。
- 高留白。
- 细字体和克制文字密度。
- 品类入口清晰。
- 商品卡片信息完整，但视觉上不拥挤。
- 图片比例、裁切和构图优先于装饰效果。
- 页面色彩低饱和，避免过强渐变和装饰背景。

不应为了“好看”额外添加：

- 大面积渐变背景。
- 装饰性光斑。
- 过圆的卡片。
- 营销感过强的彩色按钮。
- 与源站不一致的复杂动画。

## 11. 最终工作流程

1. 用户确认页面范围和技术栈。
2. 建立抽取清单。
3. 按 viewport 自动打开源站。
4. 自动截图并抽取布局 JSON。
5. 生成页面规格和图片规格。
6. 生成本地前端页面。
7. 启动本地服务。
8. 截取本地页面。
9. 与源站截图对照修正。
10. 输出最终代码、规格文档和截图证据。

## 12. Skills 准备情况

当前环境没有独立的 `web-layout-extractor` 或 `网站布局抽取` 专用 skill，但完成本项目所需的核心能力已经齐全。

必需 skills：

- `browser`：打开源站、本地页面、查看渲染结果、截图。
- `playwright`：自动化抽取 DOM、computed styles、元素坐标、图片区域、响应式尺寸。
- `playwright-interactive`：本地复刻后的反复对比、调试和视觉修正。

辅助 skills：

- `screenshot`：当浏览器截图能力不足时，用系统截图兜底。
- `imagegen`：需要生成占位图、示意图或替代视觉素材时使用。
- `figma-generate-design` + `figma-use`：后续需要把网页布局生成到 Figma 时使用。
- `figma-implement-design`：后续从 Figma 反向实现前端代码时使用。
- `figma-create-design-system-rules`：需要沉淀设计系统规则时使用。

工程流程 skills：

- `writing-plans`：把抽取和复刻拆成可执行计划。
- `verification-before-completion`：交付前验证截图、构建、页面效果。
- `systematic-debugging`：处理加载失败、样式偏差、截图不一致等问题。

结论：当前环境可以直接开始网站布局抽取工作，不需要额外安装 skill。除非后续出现专门的第三方 `web-layout-extractor` skill，否则现有组合已经覆盖核心流程。

## 13. 当前项目下一步

当前 `D:\furniture web` 目录为空。建议下一步先创建项目骨架：

- 如果目标是快速静态复刻：创建 `HTML + CSS + JS` 项目。
- 如果目标是组件化复用：创建 `Vue3 + Vite` 或 `React + Vite` 项目。
- 如果目标是小程序或跨端：创建 `UniApp` 项目。

对 RH 这类家具网站，推荐先用 `Vue3 + Vite` 或 `React + Vite` 做 Web 版复刻，再按需要迁移到其他端。
