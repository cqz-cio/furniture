# ERP 与家具商城商品分类审计（2026-07-21）

## 结论

- 当前运行数据库 `oakved_main_0d6e4079` 中有 41 个启用商城商品、41 个 ERP 商品映射，未映射的启用 SKU 为 0。
- 商城前端原来只加载第一页 24 条，并且商品接口只返回 `categoryId`，没有返回可用于导航匹配的分类名称。
- 本次对接后，前端会加载全部分页；后端返回 `categoryName`；ERP 同步会在 `Furniture` 下创建对应子分类并更新商品分类。
- 15 个现有商城分类均能自动映射，无待人工确认的“商品实体”。

## 当前商品分类

| 商城 / ERP 分类 | 商品数 | 家具 Web 分类 | 主要导航入口 |
| --- | ---: | --- | --- |
| Sofas | 12 | Sofas | Living > Sofas |
| Lounge Chairs | 5 | Lounge Chairs | Living / Bedroom > Chairs |
| Dining Tables | 3 | Dining Tables | Dining > Tables |
| Dining Chairs | 3 | Dining Chairs | Dining > Chairs |
| Lighting | 3 | Lighting | Decor、Living、Dining、Bedroom |
| Coffee Tables | 2 | Coffee Tables | Living > Tables |
| Rugs | 2 | Rugs | Decor、Living、Dining、Bedroom |
| Bedroom Storage | 2 | Nightstands / Dressers（按商品名细分） | Bedroom、Storage |
| Media Storage | 2 | Media Consoles / Sideboards（按商品名细分） | Living、Dining、Storage |
| Ottomans | 2 | Ottomans | Living > Seating |
| Beds | 1 | Beds | Bedroom > Beds |
| Desks | 1 | Desks | Bedroom / Study |
| Wardrobes | 1 | Wardrobes | Bedroom / Storage |
| Side Tables | 1 | Side Tables | Living / Bedroom > Tables |
| Bar Stools | 1 | Bar Stools | Dining > Bar & Counter Stools |

合计：41 个商品。

## 需要人工确认的导航内容

以下项目不是商品无法匹配，而是 ERP / 商城数据中没有足够的业务字段，不能可靠自动判断：

1. `Collections > Solstice / Halcyon / Kindred`：商品没有系列或 collection 字段。
2. `Bespoke`：商品没有定制 / 非定制标记。
3. `Sale`：现有商品没有可用的促销活动归属；不能仅凭市场价和销售价自动认定参与 Sale。
4. `New` 与 `Best Sellers`：当前 41 个商品的 `recommend_new`、`recommend_best` 都是 true，因此两个入口都会显示全部 41 个商品，缺少区分度。
5. `Bedroom > Headboards / Benches`、`Dining > Bistro Tables / Upholstery Swatches`：当前没有对应商品分类或明确商品。
6. `Baby & Child`、`Outdoor`：当前 41 个 ERP 商品没有年龄段或室外适用标记，暂不做推断。

## 自动匹配原则

- 优先使用商城分类名称，不依赖数据库分类 ID。
- `Bedroom Storage` 依据商品名拆分为 Nightstand 和 Dresser。
- `Media Storage` 依据商品名拆分为 Media Console 和 Sideboard。
- 房间导航允许同一商品出现在多个合理入口，例如灯具和地毯可同时出现在 Bedroom、Living、Dining 与 Decor。
- 无法可靠判断的商品保留在 All Furniture，不隐藏、不删除。
