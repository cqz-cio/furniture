# RH Image Specs

> 本文档是图片区域规格的首版候选清单。当前基于用户提供的 Sale 页 `outerHTML` 生成，精确容器尺寸需要浏览器渲染测量后补齐。

## 1. General Rules

| 类型 | 推荐格式 | 推荐文件大小 | 备注 |
| --- | --- | --- | --- |
| Hero / full width banner | WebP + JPG fallback | 250KB - 600KB | 关键视觉可更高，但需要懒加载 |
| Category tile | WebP | 120KB - 260KB | 推荐 2x 输出 |
| Product card | WebP | 80KB - 180KB | 以商品图清晰度为优先 |
| Transparent asset | PNG / WebP alpha | 按实际透明区域控制 | RH 部分 Sale 图使用 `png-alpha` |
| Icon | SVG | 小于 20KB | 除非源站使用复杂位图 |

## 2. Sale Page Image Candidates

| ID | 来源 | 资源 | 当前判断 | 推荐 |
| --- | --- | --- | --- | --- |
| SALE-BG-001 | background | `11102025_RH_Sale_Membership_1600` | 会员横幅桌面背景 | 渲染后记录容器尺寸，推荐 2x WebP |
| SALE-BG-002 | background | `11102025_RH_Sale_Membership_375` | 会员横幅移动背景 | 390 宽移动端优先，推荐 780px 宽 WebP |
| SALE-IMG-001 | img | `05212026_RH_Sale_US CA Sale` | Sale 主视觉桌面图 | 先作为 Hero，占位图显示推荐尺寸 |
| SALE-IMG-002 | img | `05212026_RH_Sale_US CA Sale Mobile` | Sale 主视觉移动图 | 移动端 Hero，占位图显示推荐尺寸 |
| SALE-IMG-003 | img | `03062026_RH_Core_Sale1` | 分类图 | Category tile |
| SALE-IMG-004 | img | `03062026_RH_Core_Sale2` | 分类图 | Category tile |
| SALE-IMG-005 | img | `03062026_RH_Core_Sale3` | 分类图 | Category tile |
| SALE-IMG-006 | img | `03062026_RH_Core_Sale4` | 分类图 | Category tile |
| SALE-IMG-007 | img | `03062026_RH_Core_Sale5` | 分类图 | Category tile |
| SALE-IMG-008 | img | `03062026_RH_Core_Sale6` | 分类图 | Category tile |
| SALE-IMG-009 | img | `11252024_Rugs_Slide8` | Rugs 分类图 | Category tile |
| SALE-IMG-010 | img | `03062026_RH_Core_Sale7` | 分类图 | Category tile |
| SALE-IMG-011 | img | `03062026_RH_Core_Sale8` | 分类图 | Category tile |
| SALE-IMG-012 | img | `03062026_RH_Core_Sale9` | 分类图 | Category tile |
| SALE-IMG-013 | img | `03062026_RH_Core_Sale10` | 分类图 | Category tile |
| SALE-IMG-014 | img | `/content/dam/.../baby-and-child/...` | 外部品牌入口图 | 可先占位 |
| SALE-IMG-015 | img | `/content/dam/.../membership-banner...` | 会员模块图 | 可先占位 |

## 3. First Measured Sizes

基于 `fixtures/rh-sale-outerhtml.html` 在 `1440 x 900` 下的本地快照测量：

| ID | 渲染尺寸 | 原图尺寸 | 当前 object-fit | 推荐 2x |
| --- | --- | --- | --- | --- |
| SALE-IMG-001 | `1440 x 619.19` | `2000 x 860` | contain | `2880 x 1238` |
| SALE-IMG-002 | `1440 x 507.59` | `2000 x 705` | contain | `2880 x 1016` |
| SALE-IMG-003 至 SALE-IMG-010 | `1440 x 840.23` | `2000 x 1167` | contain | `2880 x 1680` |

注意：这是本地 `outerHTML` 快照的测量结果。由于 RH 线上页面触发自动浏览器校验，最终线上像素级验收需要等待可用浏览器会话或用户补充更多已渲染页面快照。

## 4. Placeholder Display Rules

Vue3 复刻时先使用 `ImageSpecPlaceholder` 组件：

```text
区域名称
推荐尺寸：宽 x 高
推荐比例：例如 16:9 / 4:5 / 3:2
推荐格式：WebP
推荐大小：例如 120KB - 260KB
object-fit：cover
```

真实浏览器测量完成后，再把 `推荐尺寸` 从估算改为实际容器尺寸。
