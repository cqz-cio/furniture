# 商品、ERP 与官网数据闭环修复 PRD

## 文档信息

| 项目 | 内容 |
|---|---|
| 文档状态 | 待实施 |
| 编写日期 | 2026-08-14 |
| 审计基线 | `bb62c00b feat(erp): initialize product mapping on web creation` |
| 适用范围 | 家具商城后台、商品服务、ERP 集成、家具官网、交易库存、SEO、家具搜索 |
| 关联设计 | `docs/superpowers/specs/2026-07-11-mall-erp-integration-design.md` |
| 数据库流程 | `docs/database-flyway-workflow.md` |

## 1. 文档用途

本文档用于指导后续开发会话把当前“数据库结构已经存在，但新增、修改、查询、删除、展示和失败恢复没有完全连接”的问题逐项修复。

执行者必须以本文档中的数据来源、字段契约、状态机和验收用例为准，不再根据现有页面里的演示模板、字段猜测或商品名称推断业务类型。

本文档不是一次性重写要求。实施应按照第 14 节的阶段顺序进行，每个阶段完成代码、迁移、自动化测试和验收后再进入下一阶段。

## 2. 背景与问题

当前项目已经采用商城商品表、ERP 商品表及映射表分离的设计。这个方向是正确的：

- ERP 管理商品编码、成本、仓库、真实库存及进销存业务。
- 商城管理营销标题、图片、文案、展示分类、上下架、购物车与在线交易。
- 官网只调用商城公开接口，不直接访问 ERP 管理接口。

目前的问题不在于“两套表”，而在于表之间缺少完整生命周期编排，主要表现为：

1. Product type 在后台、接口和官网分别维护，编码不一致，官网还会按商品名称猜类型。
2. `detail_config` 能保存 Packing、Material、Size 等字段，但公开字段策略和官网展示使用了不同字段名。
3. ERP 已清空的可选内容，官网仍可能从大理石演示模板回填。
4. 首次新增 SPU 会初始化 ERP，但编辑商品新增 SKU 不触发初始化；单个 SKU 未映射可能导致整个 SPU 从官网消失。
5. ERP 初始化失败只有日志，没有持久化任务、有限重试和人工补偿入口。
6. 官网库存与结算读取 ERP，订单创建和取消却修改商城本地 SKU 库存，形成两个库存事实来源。
7. 家具搜索投影表可查询、可删除，也有 `upsert` 方法，但商品新增和修改没有调用，投影可能为空或过期。
8. ERP 同步日志只有写入，没有查询、重试、死信处理和可观测管理。
9. SEO 元数据没有验证关联商品是否存在，商品删除后可能留下公开的孤立 SEO 记录。
10. 部分 ERP 表仍使用 `(业务键, deleted)` 唯一索引，重复创建、删除、重建、再次删除时可能冲突。

## 3. 产品目标

### 3.1 核心目标

1. 建立一套 Product type/商品细类唯一来源，后台与官网完全一一对应。
2. 建立稳定、可验证的商品详情字段契约，保存、公开策略和官网渲染使用同一字段名与空值语义。
3. 任何新 SKU 都能可靠进入 ERP 初始化流程；失败可自动重试、人工补偿且可追踪。
4. ERP 库存成为唯一实际库存来源，商城订单通过库存占用参与可售库存计算。
5. 商品 CRUD 同步维护家具搜索投影、SEO 生命周期及关联数据。
6. 全量同步只刷新 ERP 到 Web 的映射和可读状态，不反向修改 ERP 商品资料。

### 3.2 成功指标

- 三个 Room 下全部 13 个 P2 细类在数据库、后台下拉框、公开接口和官网标签中编码完全一致。
- 新建商品后，所有 SKU 最终进入“已映射”或具有明确可处理失败原因，不允许静默未映射。
- 编辑现有商品新增 SKU 时，原商品不从官网消失，新 SKU 能自动完成 ERP 初始化。
- ERP/API 商品的空白可选区块不会出现 Marble、White Carrara、220 cm、260 cm 等演示内容。
- Packing、Item No.、Material、Size、Color、Finish、Service、Sample 按公开策略和空值规则正确显示。
- 并发下单不会超过 `ERP 实际库存 - 有效商城占用量`。
- 新增或修改商品后，家具搜索投影无需手工 SQL 即可查询到最新数据。
- 所有同步失败可以在后台查看、重试并最终进入成功或死信状态。

## 4. 非目标

- 不合并商城商品表和 ERP 商品表。
- 不允许官网浏览器直接调用 `/admin-api/erp/*`。
- 不允许商城同步覆盖 ERP 成本、仓库和真实库存。
- 不允许 ERP 同步覆盖商城图片、营销文案、官网排序和 SEO 文案。
- 不重做商品详情页的视觉设计；本 PRD 只调整数据来源、显隐和内容正确性。
- 不删除订单、售后、统计及同步审计历史。
- 不把 Demo 数据作为生产商品缺省值。

## 5. 术语与唯一数据来源

| 数据 | 唯一来源 | 允许的派生/缓存 | 禁止行为 |
|---|---|---|---|
| P1 Room、P2 Product type | `product_category` 分类树 | 后台和官网读取同一公开分类 API | 前端各自硬编码第二套列表 |
| Product type 稳定编码 | P2 分类 `code` | API 返回 `categoryCode` | 按商品标题、关键词猜类型 |
| 官网营销标题、图片、详情文案 | 商城 `product_spu` | 官网展示模型 | ERP 全量同步覆盖 |
| ERP 商品编码、成本、启用状态 | ERP 商品表 | `mall_erp_product_mapping` 状态缓存 | 商城编辑后反向覆盖已有 ERP 商品 |
| 实际库存 | 启用仓库的 `erp_stock` | 可售库存聚合 | 使用 `product_sku.stock` 作为第二事实来源 |
| 商城库存占用 | 商城库存占用表 | 可售库存聚合 | 只扣本地 SKU 库存但 ERP 可售不感知 |
| 商品详情公开字段 | 租户 `websiteProductFields` 策略 | API `displayPolicy` | 官网忽略策略后自行回填模板 |
| 家具智能搜索字段 | `product_furniture_sku_search` 投影 | 商品 CRUD 自动重建 | 依赖人工 SQL 维护 |

## 6. 目标业务流程

### 6.1 商品新增

1. 运营选择 P1 Room。
2. 后台从该 P1 的启用 P2 子分类中加载 Product type。
3. 保存时 `product_spu.category_id` 指向 P2 分类；不再把 `detailConfig.productType` 作为主数据。
4. SPU、SKU 和 ERP 初始化任务在同一个商城事务中写入。
5. 事务提交后，Worker 为每个 SKU 创建或复用 ERP 商品，并建立一对一映射。
6. 映射成功后状态为 `SUCCESS`；失败进入有限重试，超过阈值进入 `DEAD`。
7. 官网只显示可公开字段和已映射 SKU；Demo 模板不得参与生产 API 商品。

### 6.2 商品修改和新增 SKU

1. 更新已有 SKU 时不新建 ERP 商品，不覆盖 ERP 主数据。
2. 插入新 SKU 时必须同时创建 ERP 初始化任务。
3. 删除 SKU 时继续清理 ERP 映射和家具搜索投影，但保留同步审计历史。
4. SPU 至少有一个已映射、启用 SKU 时仍可展示；未映射 SKU 不进入购买选项。
5. 后台显示 SPU 映射汇总：`未映射`、`处理中`、`部分映射`、`已映射`、`失败`、`已停用`。

### 6.3 ERP 全量同步

1. 扫描全部商城 SKU。
2. 已有映射：读取 ERP 编码、启用状态和库存，刷新映射状态与最后同步时间。
3. 无映射但能匹配现有 ERP 商品：新建映射。
4. 找不到 ERP 商品：返回 `UNMAPPED`，不得自动伪装成功。
5. 全量同步不得创建或修改 ERP 商品基础资料。
6. “创建缺失 ERP 商品”由独立的“补建/重试初始化”动作负责。

### 6.4 库存与订单

1. 可售库存统一计算为：`启用 ERP 仓库实际库存合计 - ACTIVE 商城占用量`。
2. 商品列表、详情、购物车、结算和下单使用同一个可售库存服务。
3. 创建订单时原子创建库存占用；并发请求必须通过数据库行锁或等价机制串行校验同一 SKU。
4. 订单取消、关闭、超时释放占用。
5. 支付后异步创建 ERP 销售订单，使用稳定幂等键。
6. ERP 出库扣减实际库存后，对应商城占用转为 `CONSUMED`，避免重复扣减。
7. 退货入库增加 ERP 实际库存；商城占用不得重复回补。

## 7. 功能需求

### EPIC A：统一 Room、Product type 与官网分类

#### A-01 分类模型增加稳定编码

在 `product_category` 增加稳定 `code` 字段。字段要求：

- 同一租户、同一父分类下，启用或未删除记录的 `code` 唯一。
- `code` 只允许小写字母、数字和连字符。
- 修改显示名称不改变 `code`。
- 公开分类接口返回 `id`、`parentId`、`code`、`name`、`sort` 和 `status`。
- 使用生成列 `active_record` 约束活动记录唯一，不使用 `(code, deleted)` 作为长期方案。

数据库修改只能新增下一个连续的 Flyway V 文件。当前审计基线最高为 V047，因此预计从 V048 开始；执行前必须再次确认仓库最新版本，禁止修改已提交的 V/B 文件。

#### A-02 规范 P1/P2 分类矩阵

以下 13 个 P2 编码是本次项目的规范值：

| P1 Room | P2 code | 后台/官网显示名称 |
|---|---|---|
| Dining Room Furniture | `dining-chair` | DINING CHAIRS |
| Dining Room Furniture | `bar-stool` | BAR STOOLS |
| Dining Room Furniture | `dining-table` | DINING TABLES |
| Living Room Furniture | `sofa` | SOFA & OCCASIONAL CHAIR |
| Living Room Furniture | `coffee-table` | SIDE TABLE & COFFEE TABLE |
| Living Room Furniture | `bookcase` | BOOKCASE & DISPLAY CABINET |
| Living Room Furniture | `media-console` | CONSOLE TABLE & BUFFET |
| Bedroom Furniture | `bed` | BED & HEADBOARD |
| Bedroom Furniture | `nightstand` | BEDSIDE TABLE |
| Bedroom Furniture | `dresser` | CHEST OF DRAWERS |
| Bedroom Furniture | `bench` | BENCH |
| Bedroom Furniture | `dressing-table` | DRESSING TABLE |
| Bedroom Furniture | `wardrobe` | WARDROBE |

同时修复当前显示拼写：`DING` 改为 `DINING`，`Wadrobe` 改为 `WARDROBE`。

#### A-03 旧 Product type 数据迁移

迁移程序必须先生成审计报告，再执行可确定映射：

| 旧值 | 新 P2 code |
|---|---|
| `bed-bench` | `bench` |
| `vanity` | `dressing-table` |
| `round-table` | `dining-table` |
| `single-sofa` | `sofa` |

`chair`、`sideboard` 等有歧义值必须结合当前 P1 Room 判断；仍无法确定的商品进入人工处理清单，禁止按商品名称自动迁移。

迁移完成后：

- `product_spu.category_id` 指向 P2 分类。
- `detailConfig.productType` 只允许在兼容过渡期读取，不再写入。
- 兼容期结束后由后续迁移删除旧 JSON 键。

#### A-04 后台与官网改造

- 后台 Product type 下拉框从分类 API 的当前 P1 子分类加载，删除 `ROOM_PRODUCT_TYPE_OPTIONS` 硬编码主数据。
- 后台保存时校验所选 P2 的 `parentId` 必须等于所选 P1。
- 官网 Room 页签从同一公开分类树生成，删除 `productListingFilters` 中与三个 Room 对应的硬编码主数据。
- `mapSpuToProduct` 必须读取 API 返回的 P2 `categoryCode`，不得读取不存在的顶层字段或按名称猜测。
- 生产数据无法识别类型时显示安全的 `uncategorized` 并记录诊断，不允许静默猜成其他细类。

#### A 验收标准

- 三个 Room 共 13 个 P2 在后台与官网逐项一致。
- 选择 BAR STOOLS 保存后，公开接口和官网筛选值均为 `bar-stool`。
- `bench` 不再在官网变成 `bed-bench`；`dressing-table` 不再变成 `vanity`。
- 修改分类显示名称不影响商品筛选和 URL。
- 直接调用保存接口传入不存在或不属于当前 Room 的 P2 时，后端拒绝保存。

### EPIC B：统一商品详情字段契约与官网显隐

#### B-01 规范 `detail_config` 字段

JSON 存储可以保留，但保存 DTO、后端校验、公开接口和官网模型必须使用同一契约：

| 字段 | 类型 | 空值语义 | 官网用途 |
|---|---|---|---|
| `itemNo` | string | 空字符串不显示 | Product information |
| `material` | string | 空字符串不显示 | Product information |
| `dimension` | object | 缺失不显示 | Product information |
| `color` | string | 空字符串不显示 | Product information |
| `finish` | string | 空字符串不显示 | Product information |
| `service` | string | 空字符串不显示 | Product information |
| `sample` | string | 空字符串不显示 | Product information |
| `packing` | string | 空字符串不显示 | Product information |
| `collection` | string | 空字符串不显示 | Hero 辅助信息 |
| `heroNote` | string | 空字符串不显示 | Hero note |
| `fabricSelector` | object/null | null 或无有效选项不显示 | Selector |
| `highlights` | array | 空数组不显示 | Highlights |
| `optionGroups` | array | 空数组不显示 | Options |
| `accordions` | array | 空数组不显示 | Accordions |
| `relatedLinks` | array | 空数组不显示 | Related links |

Packing 的规范持久化键确定为 `packing`，值为自由文本。必须迁移：

- `packingDisplay` 字符串 → `packing` 字符串。
- 旧 `packing` 对象 → 使用现有格式化规则转换为可读字符串。
- 同时存在时优先保留非空 `packingDisplay`，迁移后只保留 `packing`。

`dimension` 规范结构：

```json
{
  "shape": "round | rectangular",
  "diameter": 140,
  "width": null,
  "depth": null,
  "height": 78,
  "unit": "cm"
}
```

#### B-02 后端类型校验

- 新增明确的详情配置 DTO/校验器，避免后端只接收任意 `Map<String, Object>` 而不验证结构。
- 可继续以 JSON 写入 DO，但进入数据库前必须完成类型、长度、数值范围和数组元素校验。
- `productType` 不再属于详情 DTO 主契约。
- `finish` 允许为空；删除前端、后台服务和公开接口自动写入 `Natural Oak` 的逻辑。
- 已经人工保存的 `Natural Oak` 保留，不进行批量清空。

#### B-03 公开字段策略

- `TenantProductFieldEnum`、后端详情字段映射和官网字段名必须完全一致。
- API 继续返回顶层 `displayPolicy`。
- 官网映射层必须保留并执行 `displayPolicy`。
- 字段不允许公开时，后端清除值，官网同时移除对应标题、容器和空白占位。
- 商品级 JSON 不得覆盖租户级公开策略。

#### B-04 移除生产模板回填

- `productDetailModel.js` 中 Demo 模板只能用于明确 `source === "demo"` 的本地演示数据。
- `source === "yudao"` 或其他 API 商品：字段为空、缺失、null 或空数组时一律隐藏，不得使用模板回填。
- 禁止在生产 API 商品上通过 `value || template.value` 回填 Collection、Hero note、Selector、Highlights、Option groups、Accordions 或 Related links。
- Marble、White Carrara、220 cm、260 cm、Stone care 等示例可以留在 Demo fixture，但不得进入生产路径。

#### B-05 官网 Product information

官网详情页按以下固定顺序显示公开且非空字段：

1. Item No.
2. Material
3. Size
4. Color
5. Finish
6. Service
7. Sample
8. Packing

尺寸由 `dimension` 统一格式化：

- Round：`Dia 140 x H 78 cm`
- Rectangular：`W 55 x D 54 x H 95 cm`

不得让官网各页面再次维护不同的 Size/Packing 格式。

#### B 验收标准

- 新建 Rustic Dining Table，所有可选模块留空后，官网不显示任何大理石、石材或示例尺寸内容。
- Packing 填写 `Ships in two cartons`，保存、重新编辑和官网展示文本完全一致。
- Finish 留空后数据库、接口和官网均为空，不自动出现 Natural Oak。
- 租户关闭 Packing 后，接口不返回值，官网不显示 Packing 行。
- 空 Collection、空 Hero note、空 Highlights 和空 Accordion 不保留空容器。

### EPIC C：ERP 映射初始化、重试与状态闭环

#### C-01 增加持久化同步任务/Outbox

新增 `mall_erp_sync_task`，不得把应用内 AFTER_COMMIT 事件作为唯一可靠触发机制。建议字段：

- `id`
- `tenant_id`
- `entity_type`
- `entity_id`
- `event_type`
- `idempotency_key`
- `status`：`PENDING`、`PROCESSING`、`SUCCESS`、`FAILED`、`DEAD`
- `retry_count`
- `next_retry_at`
- `last_error`
- `locked_at`
- `locked_by`
- 标准创建、更新、软删除字段
- 活动记录稳定幂等唯一键

任务必须与 SPU/SKU 写入处于同一数据库事务。内存事件可以用来唤醒 Worker，但不能作为任务事实来源。

#### C-02 覆盖全部 SKU 生命周期

- 创建 SPU 的所有 SKU：各创建一条初始化任务。
- 编辑 SPU 插入的新 SKU：各创建一条初始化任务。
- 更新已有 SKU：不创建新 ERP 商品；仅在明确需要刷新映射时创建 `REFRESH` 任务。
- 删除 SKU：解除活动映射，清理搜索投影，保留历史日志与任务。
- 重复保存、重复投递和 Worker 重启不得创建重复 ERP 商品或重复映射。

#### C-03 重试策略

- 自动重试建议 5 次，采用退避时间，例如 1、5、15、30、60 分钟。
- 可恢复错误：ERP 暂时不可用、数据库锁冲突、超时。
- 不可恢复错误：缺少启用 ERP Furniture 分类、缺少计量单位、ERP 商品已被其他 SKU 占用。
- 不可恢复错误可以直接进入 `DEAD`，必须保留可理解的 `last_error`。
- 网络调用异常也必须落任务/日志，不能只写应用日志。

#### C-04 后台操作与状态

增加：

- 同步日志分页查询。
- 按商品/SKU、状态、时间筛选。
- 单条“重试初始化”。
- 批量“补建未映射 ERP 商品”。
- DEAD 任务人工重试。
- 查看 ERP 商品编码、映射状态、最后同步时间和失败原因。

状态汇总规则：

- 无 SKU：`NO_SKU`
- 全部无映射且无任务：`UNMAPPED`
- 存在 PENDING/PROCESSING：`PENDING`
- 部分成功、部分未完成：`PARTIAL`
- 全部成功：`SUCCESS`，前端显示“已映射”
- 任一 DEAD 且无处理中任务：`FAILED`
- ERP 商品停用：`DISABLED`

#### C-05 全量同步边界

- 保留现有“ERP 全量同步”只读 ERP 主数据的原则。
- 全量同步可以新增/刷新映射表记录，但不得创建、更新或删除 ERP 商品。
- 找不到 ERP 商品时统计为 `unmappedSkus`。
- 找到但已被占用时统计为 `failedSkus`。
- 操作完成后必须刷新列表，并依据真实映射状态显示“已映射”。

#### C-06 官网部分映射策略

- SPU 至少存在一个 SUCCESS 且 ERP 启用的 SKU 时，SPU 可以显示。
- 只向官网返回已映射、ERP 启用的 SKU。
- 所有 SKU 都不可用时隐藏 SPU 或返回明确不可购买状态。
- 新增一个未完成映射的 SKU 不得导致原来已正常展示的整个 SPU 消失。

#### C 验收标准

- 新建 1 SPU/2 SKU 后生成 2 个初始化任务和 2 个唯一映射。
- 编辑已映射商品新增第三个 SKU，第三个 SKU 自动完成初始化，原商品全程不消失。
- ERP 暂时不可用时任务进入重试；恢复后无需重新保存商品即可成功。
- 同一任务执行两次只产生一个 ERP 商品和一个活动映射。
- 全量同步前后 ERP 商品名称、成本、分类和单位不被修改。
- 后台的汇总数与每条 SKU 的真实状态一致。

### EPIC D：ERP 库存与商城订单占用闭环

#### D-01 新增商城库存占用表

新增 `mall_inventory_reservation`，建议字段：

- `tenant_id`
- `mall_order_id`
- `mall_order_item_id`
- `mall_sku_id`
- `erp_product_id`
- `quantity`
- `status`：`ACTIVE`、`RELEASED`、`CONSUMED`
- `expires_at`
- `idempotency_key`
- `release_reason`
- 标准时间、版本和软删除字段

同一订单项的活动占用必须幂等唯一。

#### D-02 统一可售库存服务

所有调用方统一使用：

```text
sellableStock = enabledErpWarehouseStock - activeMallReservations
```

调用方包括：

- 商品列表
- 商品详情
- 购物车新增/修改数量
- 结算试算
- 创建订单
- 家具 AI 搜索库存筛选

`product_sku.stock` 可以保留兼容镜像，但不得作为独立校验或独立扣减来源。

#### D-03 原子占用

- 创建订单时按稳定顺序锁定相关 SKU 的映射/库存守卫行。
- 在同一事务中重新计算可售库存并插入 ACTIVE 占用。
- 任一 SKU 不足则整个订单占用失败，不产生部分成功。
- 取消、关闭、支付超时和取消订单项必须幂等释放。
- 退款但未退货不自动增加 ERP 实际库存。

#### D-04 商城订单到 ERP

按照关联设计补齐 `mall_erp_order_mapping` 和 `MALL_ORDER_PAID` Outbox：

- 支付成功异步创建 ERP 销售订单。
- 使用 `tenantId + mallOrderId + MALL_ORDER_PAID` 幂等键。
- 创建失败不回滚支付事实，进入有限重试和人工补偿。
- ERP 出库后实际库存减少，对应 reservation 转为 CONSUMED。
- 退货入库后由 ERP 库存变化重新计算可售库存。

#### D 验收标准

- ERP 库存 1，两笔并发订单各购买 1，最多一笔占用成功。
- ERP 库存 10、ACTIVE 占用 3，列表、详情、购物车和结算均显示/校验 7。
- 取消订单后占用释放，可售库存恢复。
- 重复取消、重复支付回调和重复 ERP 出库事件不重复释放或扣减。
- 本地 `product_sku.stock` 与 ERP 不一致时，不影响最终可售库存结论。

### EPIC E：家具搜索投影闭环

#### E-01 商品 CRUD 自动维护投影

- 创建 SKU 后生成 `product_furniture_sku_search` 投影。
- 修改 Product type、Material、Color、Dimension、Room、Feature 或 SKU 后更新投影。
- 删除 SKU 时继续删除活动投影。
- 投影生成失败应使同事务回滚，或进入明确的本地重建任务；不得静默成功。

#### E-02 统一投影映射规则

- `category_code` 来自 P2 分类 `code`。
- `room_type_codes` 来自 P2 的父分类。
- Material、Color、Dimension 来自规范详情字段，不解析营销描述。
- 无法标准化的值保留原始展示字段并记录待治理项，不凭模糊关键词错误分类。

#### E-03 全量重建

提供租户范围内的投影重建服务/管理动作：

1. 扫描活动 SPU/SKU。
2. 校验 P2 分类与详情字段。
3. 幂等 upsert 投影。
4. 删除已经没有活动 SKU 的孤立投影。
5. 输出成功、跳过、失败数量及错误清单。

#### E 验收标准

- 新建商品无需手工 SQL 即可被 FurnitureProductSearchTool 查询。
- 修改尺寸和材质后搜索结果使用新值。
- 删除 SKU 后投影消失。
- 全量重建执行两次结果一致，不产生重复记录。

### EPIC F：SEO 与商品生命周期

#### F-01 SEO 关联校验

- 创建、更新和发布 PRODUCT 类型 SEO 时校验对应 SPU 存在且属于当前租户。
- CATEGORY 类型同样校验分类存在。
- 不存在的实体不得发布。
- 公共 SEO 查询在返回前再次校验源实体仍可公开。

#### F-02 商品删除联动

- 商品删除事务写入持久化 `PRODUCT_DELETED` 事件或本地可靠任务。
- SEO 消费方将对应公开记录下架；可以保留历史草稿，但不得继续公开。
- 删除失败可重试并可审计。

#### F-03 商品状态变更校验

- 商品下架或进入回收站前校验有效促销引用。
- 明确业务规则：阻止操作，或先结束促销后再下架；不得保留 TODO 后直接改状态。

#### F 验收标准

- 不存在的商品 ID 无法创建或发布 PRODUCT SEO。
- 已发布商品删除后，公开 SEO 接口不再返回该记录。
- 有效促销中的商品不能直接进入不允许的状态。

### EPIC G：同步日志、版本与数据库生命周期治理

#### G-01 同步日志定义

- `mall_erp_sync_log` 是追加式审计日志，不直接修改历史结果。
- 每次尝试写一条结果，关联 taskId、attempt、entity、eventType 和稳定幂等键。
- 日志不保存令牌、密码、完整会员地址或支付敏感信息。
- 人工重试创建新任务/新尝试，不覆盖旧失败日志。

#### G-02 映射版本

二选一，但必须明确实施：

1. 使用 `mall_erp_product_mapping.version` 做乐观锁，每次映射状态更新递增；推荐。
2. 如果确认没有并发更新需求，通过新迁移删除无效 version 字段。

禁止继续永久写 0 而不读取。

#### G-03 修复软删除唯一索引

审计并修复仍使用 `(业务键, deleted)` 唯一索引的 ERP 活动主数据，至少包括：

- ERP product unit
- ERP product category
- ERP product bar code
- ERP warehouse
- ERP stock 的 product + warehouse

统一采用：

```sql
active_record = CASE WHEN deleted = 0 THEN 1 ELSE NULL END
```

唯一索引包含 `active_record`，并增加“创建 → 删除 → 重建 → 再次删除 → 再重建”回归测试。

#### G 验收标准

- 后台可按状态查询所有同步尝试和失败原因。
- FAILED/DEAD 任务可人工重试，历史日志不被覆盖。
- 映射并发更新不会丢失状态。
- ERP 同名单位、同编码商品或同名仓库完成两轮删除重建后仍可继续使用。

## 8. API 需求

具体路径可按项目现有风格调整，但语义不得改变。

| API | 用途 |
|---|---|
| `GET /app-api/product/category/tree` | 返回带稳定 code 的公开 P1/P2 分类树 |
| `GET /admin-api/product/category/list` | 后台加载 Room 与 Product type |
| `POST /admin-api/product/spu/erp-integration/sync-all` | 只读 ERP 主数据的全量映射刷新 |
| `POST /admin-api/product/spu/erp-integration/retry-initialize` | 对指定 SKU 补建/重试 ERP 初始化 |
| `POST /admin-api/product/spu/erp-integration/retry-unmapped` | 批量处理未映射 SKU |
| `GET /admin-api/erp/integration/task/page` | 查询同步任务 |
| `GET /admin-api/erp/integration/log/page` | 查询追加式同步日志 |
| `POST /admin-api/erp/integration/task/{id}/retry` | 重试 FAILED/DEAD 任务 |
| `POST /admin-api/product/furniture-projection/rebuild` | 重建当前租户家具搜索投影 |

公开商品 API 必须返回：

- P2 `categoryId`、`categoryCode`、`categoryName`
- P1 Room 摘要或可通过分类树解析的 `parentId`
- 规范 `detailConfig`
- 顶层 `displayPolicy`
- 仅允许公开且可用的 SKU
- 统一计算后的 sellable stock

## 9. 数据迁移与上线前审计

### 9.1 迁移前只读报告

迁移程序必须先输出以下数量和记录清单：

- 缺少 P2 Product type 的 SPU。
- `detailConfig.productType` 为未知值的 SPU。
- `packingDisplay`、旧 packing 对象和同时存在两者的 SPU。
- Finish 为系统自动默认值但无法确认真实性的 SPU。
- 无 ERP 映射、重复映射、孤立映射和指向已删除 ERP 商品的 SKU。
- 缺失家具搜索投影和孤立投影。
- 指向不存在商品/分类的 SEO 记录。
- ERP 活动业务键重复风险。

### 9.2 数据修复顺序

1. 新增分类 code 和必要表/索引。
2. 初始化规范 P1/P2 分类。
3. 迁移 SPU 到 P2 categoryId。
4. 迁移 detail_config 字段，尤其是 packing。
5. 重建家具搜索投影。
6. 为未映射 SKU 创建初始化任务。
7. 修复或下架孤立 SEO。
8. 启用新官网读取逻辑。

### 9.3 Flyway 要求

- 只新增连续 V 迁移，不修改或删除历史 V/B。
- 执行 `npm run build:db-baseline`。
- 执行 `npm run verify:db-migrations`。
- 验证空库安装、旧数据库升级和重复启动幂等。
- 生产部署前备份数据库。

## 10. 兼容与回滚

- Product type 迁移至少保留一个发布周期的旧 JSON 读取兼容，但新写入只能使用 P2 categoryId/code。
- Packing 迁移期间读取顺序：规范 `packing` 字符串 → `packingDisplay` → 旧 packing 对象格式化；新保存只写规范 `packing`。
- 新旧库存逻辑切换前必须完成占用数据初始化；不得在存在活动订单时直接关闭旧扣减而不迁移。
- Outbox Worker 可以通过配置暂停，但任务记录不能丢失。
- 官网模板回填关闭后无需数据回滚；Demo 页面通过明确 Demo source 保留演示。
- 若阶段上线失败，只回退该阶段应用代码；已执行 Flyway 迁移不得手工删除，应通过后续修复迁移前向恢复。

## 11. 可观测性

至少提供以下指标或后台统计：

- 活动 SKU 总数、已映射数、未映射数、部分映射 SPU 数。
- PENDING、FAILED、DEAD ERP 任务数与最老等待时间。
- 最近一次全量同步的 total/mapped/refreshed/unmapped/failed/noSku。
- ERP 实际库存、商城 ACTIVE 占用和可售库存差异。
- 缺失/失败家具搜索投影数。
- 孤立公开 SEO 数。
- 每个同步任务的 trace/task ID、重试次数和最后错误。

失败告警建议：

- DEAD 任务大于 0。
- PENDING 超过 30 分钟。
- 映射率低于 100%，但不包含明确停用 SKU。
- 可售库存出现负数。
- 公开商品引用不存在分类或 SEO 实体。

## 12. 权限与安全

- 全量同步、补建 ERP、重试任务、投影重建必须使用独立后台权限点。
- 所有操作限定当前 tenant，禁止跨租户匹配。
- 批量补建与投影重建需要二次确认和操作审计。
- 官网不返回 ERP 成本、内部错误堆栈、后台用户或同步请求敏感摘要。
- 错误信息对运营可理解，对官网用户只返回安全业务提示。

## 13. 自动化测试与验收清单

### 13.1 后端测试

- Product category code 唯一、父子关系和跨租户隔离。
- SPU 保存必须指向合法 P2。
- 详情 DTO 对字段类型、单位、数值和长度的校验。
- SPU 创建和新增 SKU 均写 Outbox。
- Worker 幂等、重试、DEAD、人工重试。
- 全量同步不调用 ERP 商品 update/insert。
- 部分映射 SPU 的公开行为。
- 原子库存占用并发测试。
- 取消、超时、支付、出库和退货的幂等状态转换。
- 搜索投影 create/update/delete/rebuild。
- SEO 实体存在性与删除联动。
- ERP 活动唯一键重复生命周期测试。

### 13.2 后台前端测试

- 更新 `check:product-type-options`：改为校验分类 API 契约，而非硬编码两套列表。
- 更新 `check:product-type-no-auto-template`：确保选择类型只改变分类，不填充示例详情。
- 更新 `check:packing-text`：规范字段改为 `packing` 字符串并覆盖旧数据兼容。
- 替换 `check:finish-default`：改为校验 Finish 可空且不自动填充。
- 增加映射汇总状态、失败原因和重试按钮测试。
- 执行 `pnpm ts:check` 及相关 contract checks。

### 13.3 官网测试

- `npm test` 覆盖分类、详情模型、字段策略和空值显隐。
- `npm run build` 必须通过。
- 增加 13 个 P2 分类矩阵测试。
- 增加真实 API 商品不使用 Demo 模板测试。
- 增加 Packing/Dimension 格式化测试。
- 增加部分 SKU 未映射时 SPU 仍展示测试。
- 增加列表、详情、购物车和结算库存一致性测试。

### 13.4 数据库测试

- `npm run build:db-baseline`
- `npm run verify:db-migrations`
- 空库安装。
- 从当前生产相同版本升级。
- 同一数据库重复启动不重复插入分类、任务或映射。
- 迁移脚本执行前后输出审计数量，未知数据不得静默丢弃。

### 13.5 端到端核心场景

1. 新建 Rustic Dining Table，选择 Dining Room → Dining Tables，Packing 为 `Ships in two cartons`，其他可选区块为空。
2. 保存后自动创建 ERP 商品和映射。
3. 官网正确出现在 Dining Tables，显示规范 Product information，不显示 Marble 示例。
4. 编辑商品增加一个 SKU，原商品不消失，新 SKU 最终映射成功。
5. 模拟 ERP 暂时不可用，验证任务重试和后台错误可见。
6. ERP 库存设置为 1，模拟两笔并发订单，验证最多一笔成功占用。
7. 取消成功订单，验证可售库存恢复。
8. 修改 Material/Size，验证官网和家具 AI 搜索均更新。
9. 将商品下架并删除，验证映射、投影和公开 SEO 不再活动，历史订单与同步日志保留。

## 14. 实施阶段与依赖

### Phase 0：特征测试与数据审计

- 为当前关键行为补 characterization tests。
- 生成第 9.1 节只读报告。
- 不做业务数据修改。

完成标准：风险数据数量可见，测试能稳定复现现有断点。

### Phase 1：分类与详情契约

- 完成 EPIC A、EPIC B 的数据库与后端契约。
- 完成后台表单迁移和官网 Demo/生产数据分流。

依赖：Phase 0。

完成标准：13 个 Product type 一致，详情字段保存到展示闭环，大理石模板不进入 API 商品。

### Phase 2：ERP 初始化与补偿

- 完成 EPIC C。
- 迁移未映射 SKU 为持久化任务。

依赖：Phase 1 的规范 SKU/分类数据。

完成标准：首次新增和新增 SKU 都可靠映射，失败可重试，部分映射不隐藏整个商品。

### Phase 3：库存与订单

- 完成 EPIC D。
- 按关联设计补齐商城订单到 ERP 单据映射。

依赖：Phase 2 的稳定 SKU ↔ ERP 映射。

完成标准：所有渠道使用同一个可售库存结论，并发不超卖。

### Phase 4：搜索、SEO 与数据库治理

- 完成 EPIC E、F、G。
- 重建投影并清理公开孤立数据。

依赖：Phase 1 的分类和详情契约。

完成标准：搜索、SEO、日志和软删除生命周期均可重复验证。

### Phase 5：全链路回归与发布

- 执行第 13 节全部测试。
- 完成故障演练：ERP 不可用、Worker 重启、重复事件、数据库锁冲突。
- 验证迁移、指标、权限、回滚和运营操作说明。

完成标准：第 3.2 节成功指标全部满足，无 P0/P1 未决问题。

## 15. 开发执行规则

后续执行会话必须遵守：

1. 开始前读取本文档、关联 ERP 设计和数据库 Flyway 流程。
2. 先确认当前分支及最新迁移版本，不能假设仍是 `bb62c00b`/V047。
3. 每个 Phase 先写或更新失败测试，再实施代码。
4. 不在一个提交中混入无关 UI 重构、文案改版或依赖升级。
5. 所有跨模块调用通过 API/服务边界，不直接跨模块修改对方数据表。
6. 所有任务、映射和订单事件必须 tenant-aware、幂等、可重试。
7. 每个 Phase 交付时列出：修改文件、迁移版本、测试结果、已知限制、数据修复数量。
8. 任何与本文档目标架构冲突的现有测试，应更新为新契约，而不是为了通过测试保留错误行为。

## 16. Definition of Done

只有满足以下全部条件，本 PRD 才算完成：

- 13 个 P2 类型由数据库分类树统一驱动，后台与官网无第二套主数据。
- 规范详情字段从保存、策略过滤到官网展示全部一致。
- 生产 API 商品不再使用 Demo 模板回填。
- 首次新增和新增 SKU 都有可靠 ERP 初始化、重试和人工补偿。
- 全量同步不会修改 ERP 商品基础资料，映射结果准确可见。
- ERP 实际库存减商城有效占用是唯一可售库存算法。
- 家具搜索投影随商品 CRUD 自动维护并可全量重建。
- SEO 不再公开引用已删除或不存在实体的记录。
- ERP 同步任务、日志、版本和软删除唯一索引均具有完整生命周期测试。
- 数据库迁移、后端测试、后台检查、官网测试和端到端核心场景全部通过。

## 17. 已排除的误报

审计复核发现，“警戒库存数量直接统计全部商品”不是实际缺陷。`ProductSpuServiceImpl.getTabsCount()` 调用的零参数 `ProductSpuMapper.selectCount()` 已被项目自定义为：库存小于等于 `ProductConstants.ALERT_STOCK` 且状态不是回收站。因此本 PRD 不要求修改警戒库存统计，除非后续产品另行要求把固定阈值 10 改为租户配置。
