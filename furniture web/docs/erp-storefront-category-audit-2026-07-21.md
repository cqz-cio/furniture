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

## 商品图片核对与 Web 展示

- ERP 核心表 `erp_product` 没有独立商品图片字段；Web 使用同步后的商城 `product_spu.pic_url`（封面）、`product_spu.slider_pic_urls`（详情轮播）和 SKU 图片。
- 当前 41 个启用商品全部有封面图，轮播字段合计 254 张图片；不存在缺少封面的启用商品。
- `jabo-product-import` 的 15 个商品全部有多图，共 15 张封面和 137 张轮播图。
- ERP 文件库中有 241 个“嘉博白底”单品图片文件并已关联到商品；另有 1 张“场景”氛围图，不属于单品详情图。
- Web 商品详情页原先最多只显示前 5 张，并用占位内容补足不足 5 张的商品；现已改为去重后展示 ERP 提供的全部真实图片。没有真实图片时才显示占位内容。
- 商品列表继续使用封面图，悬停时使用第一张与封面不同的轮播图；移动端详情缩略图改为横向滚动，避免多图被挤成多行。

### 图片待人工确认

只有 `嘉博白底-9` 需要确认：

1. 该目录本地有 36 个文件，去除 9 个字节完全相同的副本后有 27 张不同图片；ERP 当前仅关联 18 个文件，并且其中 `j/09/10.jpg` 与 `j/09/11.jpg` 内容完全相同，实际为 17 张不同图片。
2. 未进入 ERP 的内容包括一组 9 张同风格单椅角度图。请确认它们应追加到 `Ivory Oak Three-Piece Sofa Set`（SPU 45）的详情轮播，还是应该新建一个独立单椅商品。
3. ERP 已关联的沙发角度图中有一张重复图；确认上述归属后，应以遗漏的有效角度图替换重复项。

除 SPU 45 外，其他商品图片无需人工确认。
