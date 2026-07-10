# 商品详情页展示图设计 QA

## 对照目标

- 原始参考图：`D:\Documents\xwechat_files\wxid_lq64xxzmtnwo32_b334\temp\RWTemp\2026-07\a7cf2a7d3f5327bc0aa9468f619a4493\ae70ab27283b18efde22ac0f0794bca9.png`
- 桌面端实现截图：`captures/product-detail-gallery/oak-nightstand-desktop-after.png`
- 第二个商品桌面截图：`captures/product-detail-gallery/walnut-single-sofa-desktop-after.png`
- 移动端实现截图：`captures/product-detail-gallery/oak-nightstand-mobile-after.png`
- 前后对照图：`captures/product-detail-gallery/oak-nightstand-before-after.png`

## 验证环境

- 桌面端视口：1920 × 950
- 移动端视口：390 × 844
- 桌面端商品：Oak Nightstand（ID 1002）、Walnut Single Sofa（ID 1001）
- 移动端商品：Oak Nightstand（ID 1002）
- 页面状态：详情页首屏、画廊首张图；另外验证了下一张图片切换

## 实测结果

- 两个桌面商品页的主图区域均为 520px 高。
- 两个桌面商品页的主图顶部和右侧商品信息顶部均为 y=208，完全对齐。
- 桌面端页面没有横向溢出。
- 移动端主图区域为 360px 高，宽 339px，符合现有单栏规则。
- 移动端页面没有横向溢出，右侧信息区改为正常文档流，缩略图保持横向滚动。
- 点击“下一张”后，画廊状态从 `Hero view 1 / 5` 变为 `Room view 2 / 5`。
- 浏览器控制台没有错误。

## 完整画面对照

`oak-nightstand-before-after.png` 将原始截图和修改后的 1920px 桌面实现放在同一画面中。修改前主图区域高达 680px，并明显压过右侧首屏信息；修改后主图为 520px，顶部对齐不变，缩略图和说明回到首屏内，左右两栏的视觉比重更协调。

不需要额外的局部放大对照：本次改动只涉及主图区域的整体尺寸和位置，完整 1920px 对照中边界、顶部对齐和与右侧信息的比例均清晰可见。

## 必查视觉项目

- 字体与排版：未修改字体、字号、字重、行高或换行规则；与原页面保持一致。
- 间距与布局节奏：主图高度由最高 680px 降至最高 520px；顶部与右栏对齐；缩略图紧随主图显示。
- 颜色与视觉变量：未修改背景色、边框、透明度或现有设计变量。
- 图片质量与素材一致性：继续使用原商品图片和 `object-fit: contain`，没有裁切、替换或降低图片质量。
- 文案与内容：未修改任何商品名称、价格、说明、按钮或辅助文案。

## Findings

没有发现需要处理的 P0、P1 或 P2 问题。

## Comparison History

- 初始问题：桌面端 `.product-gallery-main` 使用 `clamp(520px, 48vw, 680px)`，在 1920px 视口达到 680px，区域过大。
- 修复：改为 `clamp(420px, 36vw, 520px)`，不改变移动端 360px 规则。
- 修复后证据：两个桌面商品页均实测 520px 且顶部完全对齐；移动端实测 360px，无横向溢出。

## Implementation Checklist

- [x] 所有详情页使用同一套共用样式
- [x] 桌面端主图高度在 420-520px 范围内
- [x] 移动端保持 360px
- [x] 主图与右侧信息顶部对齐
- [x] 图片切换和缩略图可用
- [x] 无控制台错误

## Follow-up Polish

无阻塞性交付项。

final result: passed
