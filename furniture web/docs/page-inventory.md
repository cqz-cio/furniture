# RH 页面抽取清单

目标网站：`https://rh.com/us/en`

目标产物：Vue3 + Vite 前端规格页。页面不还原真实图片，只保留图片/视频投放容器，并在容器内标注实测显示尺寸、推荐 2x 素材尺寸、源素材尺寸、推荐文件体积和 `object-fit`。

## 已接入页面

| 页面 | 数据来源 | 桌面状态 | 移动状态 | 前端页面 |
| --- | --- | --- | --- | --- |
| Home | `data/manual/rh-home-1365x953.json`、`data/manual/rh-home-390x844.json` | 已接入 hero + 4 个后续图片槽 | 已接入对应移动槽位 | `src/pages/HomePage.vue` |
| Sale | `data/manual/rh-sale-1365x953.json`、`data/manual/rh-sale-390x844.json` | 已接入 hero、10 个分类图槽、membership banner | 已接入对应移动槽位 | `src/pages/SalePage.vue` |
| Teen | `data/manual/rh-teen-1365x953.json` | 已接入 hero 图槽 | 缺移动 JSON | `src/pages/TeenPage.vue` |

## 待抽取页面

这些页面目前没有足够源数据，不能凭视觉猜布局。每个页面至少需要一份桌面 JSON 和一份移动 JSON；如果有复杂 AEM 结构、背景图、视频、hover 菜单，再补 `outerHTML`。

| 页面 | 需要文件 |
| --- | --- |
| Living 一级页 | desktop JSON + mobile JSON + optional outerHTML |
| Dining 一级页 | desktop JSON + mobile JSON + optional outerHTML |
| Bed 一级页 | desktop JSON + mobile JSON + optional outerHTML |
| Bath 一级页 | desktop JSON + mobile JSON + optional outerHTML |
| Outdoor 一级页 | desktop JSON + mobile JSON + optional outerHTML |
| Lighting 一级页 | desktop JSON + mobile JSON + optional outerHTML |
| Textiles 一级页 | desktop JSON + mobile JSON + optional outerHTML |
| Rugs 一级页 | desktop JSON + mobile JSON + optional outerHTML |
| Decor 一级页 | desktop JSON + mobile JSON + optional outerHTML |
| Baby & Child 一级页 | desktop JSON + mobile JSON + optional outerHTML |
| 商品列表页 PLP | desktop JSON + mobile JSON + outerHTML |
| 商品详情页 PDP | desktop JSON + mobile JSON + outerHTML |

## 当前前端行为

- Header 中 `Sale` 打开已接入 Sale 规格页。
- Header 中 `Teen` 打开已接入 Teen 规格页。
- Header 中其他未接入页面打开“Extraction backlog”页面，明确提示缺少哪些源文件。
- Home 页面可通过点击中间 RH 标识打开。

## 数据命名建议

后续导出的文件建议统一放入 `data/manual/`：

```text
rh-<page>-1365x953.json
rh-<page>-390x844.json
rh-<page>-outerhtml.txt
```

示例：

```text
rh-living-1365x953.json
rh-living-390x844.json
rh-living-outerhtml.txt
```
