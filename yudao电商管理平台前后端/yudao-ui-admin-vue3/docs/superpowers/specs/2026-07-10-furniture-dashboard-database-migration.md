# 家具电商数据看板数据库与数据迁移设计

> 日期：2026-07-10
> 数据库：MySQL 8.x
> 时区：Asia/Shanghai
> 关联设计：[家具电商数据看板开发设计](./2026-07-10-furniture-commerce-data-dashboard-design.md)

## 1. 目标

本设计为数据看板提供原始行为事件、站点日聚合、商品日聚合扩展和订单成本快照。所有金额以“分”为单位，所有业务表保留芋道的租户、审计和逻辑删除字段。

数据库迁移文件固定为：

`yudao-cloud/sql/mysql/statistics-commerce-dashboard.sql`

## 2. 事件枚举

| event_type | 常量 | 说明 |
|---:|---|---|
| 1 | HOME_VIEW | 首页成功进入、返回或刷新 |
| 2 | PRODUCT_DETAIL_VIEW | 商品详情数据加载成功 |
| 3 | ADD_TO_CART | 真实购物车接口添加成功 |
| 4 | CHECKOUT_START | 用户进入结算流程 |

| device_type | 常量 |
|---:|---|
| 1 | DESKTOP |
| 2 | MOBILE |
| 3 | TABLET |
| 9 | OTHER |

## 3. 新表

### 3.1 原始行为事件表

```sql
CREATE TABLE IF NOT EXISTS `statistics_behavior_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '事件主键',
  `event_id` varchar(64) NOT NULL COMMENT '客户端事件唯一标识',
  `event_type` tinyint NOT NULL COMMENT '1首页 2商品详情 3加购 4开始结算',
  `visitor_hash` char(64) NOT NULL COMMENT '服务端SHA-256匿名访客哈希',
  `session_hash` char(64) NOT NULL COMMENT '服务端SHA-256会话哈希',
  `user_id` bigint DEFAULT NULL COMMENT '已登录会员编号',
  `spu_id` bigint DEFAULT NULL COMMENT '商品SPU编号',
  `sku_id` bigint DEFAULT NULL COMMENT '商品SKU编号',
  `quantity` int DEFAULT NULL COMMENT '加购数量',
  `page_path` varchar(255) NOT NULL COMMENT '标准化路径，不含敏感查询参数',
  `referrer_host` varchar(255) DEFAULT NULL COMMENT '来源域名',
  `device_type` tinyint NOT NULL DEFAULT 9 COMMENT '1桌面 2手机 3平板 9其他',
  `occurred_at` datetime(3) NOT NULL COMMENT '事件发生时间',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '服务器入库时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_event` (`tenant_id`, `event_id`),
  KEY `idx_tenant_type_time` (`tenant_id`, `event_type`, `occurred_at`),
  KEY `idx_tenant_spu_type_time` (`tenant_id`, `spu_id`, `event_type`, `occurred_at`),
  KEY `idx_tenant_time_visitor` (`tenant_id`, `occurred_at`, `visitor_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统计-原始行为事件';
```

数据约束由服务层和单元测试保证：

- `HOME_VIEW`：`page_path='/'`，`spu_id`、`sku_id`、`quantity` 为空。
- `PRODUCT_DETAIL_VIEW`：`spu_id` 必填，`page_path='/product'`。
- `ADD_TO_CART`：`spu_id`、`sku_id`、`quantity` 必填，`quantity > 0`。
- `CHECKOUT_START`：商品字段为空。
- `occurred_at` 超出服务器时间前后 24 小时，入库时替换为服务器时间。
- 原始 `visitorId`、`sessionId` 和 IP 不落库；服务端使用租户级盐值计算 SHA-256。

### 3.2 站点日聚合表

```sql
CREATE TABLE IF NOT EXISTS `statistics_traffic_daily` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `day` date NOT NULL COMMENT '统计日期',
  `home_pv` int NOT NULL DEFAULT 0,
  `home_uv` int NOT NULL DEFAULT 0,
  `product_detail_pv` int NOT NULL DEFAULT 0,
  `product_detail_uv` int NOT NULL DEFAULT 0,
  `add_cart_count` int NOT NULL DEFAULT 0,
  `add_cart_user_count` int NOT NULL DEFAULT 0,
  `checkout_start_count` int NOT NULL DEFAULT 0,
  `paid_order_count` int NOT NULL DEFAULT 0,
  `paid_buyer_count` int NOT NULL DEFAULT 0,
  `paid_item_count` int NOT NULL DEFAULT 0,
  `paid_revenue` bigint NOT NULL DEFAULT 0 COMMENT '支付销售额，分',
  `refund_amount` bigint NOT NULL DEFAULT 0 COMMENT '成功退款额，分',
  `net_revenue` bigint NOT NULL DEFAULT 0 COMMENT '净销售额，分',
  `cost_amount` bigint NOT NULL DEFAULT 0 COMMENT '净商品成本，分',
  `gross_profit` bigint NOT NULL DEFAULT 0 COMMENT '商品毛利，分',
  `estimated_cost_item_count` int NOT NULL DEFAULT 0,
  `missing_cost_item_count` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_day` (`tenant_id`, `day`),
  KEY `idx_tenant_update_time` (`tenant_id`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统计-站点日聚合';
```

`missing_cost_item_count` 是总体设计的工程化补充，用于稳定区分 `INCOMPLETE`，不能用“成本为空等同于0”的方式替代。

## 4. 现有表扩展

### 4.1 product_statistics

迁移脚本先用下列定义补齐可能缺失的基础表，再执行扩展：

```sql
CREATE TABLE IF NOT EXISTS `product_statistics` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `time` date NOT NULL COMMENT '统计日期',
  `spu_id` bigint NOT NULL COMMENT '商品SPU编号',
  `browse_count` int NOT NULL DEFAULT 0,
  `browse_user_count` int NOT NULL DEFAULT 0,
  `favorite_count` int NOT NULL DEFAULT 0,
  `cart_count` int NOT NULL DEFAULT 0,
  `order_count` int NOT NULL DEFAULT 0,
  `order_pay_count` int NOT NULL DEFAULT 0,
  `order_pay_price` int NOT NULL DEFAULT 0,
  `after_sale_count` int NOT NULL DEFAULT 0,
  `after_sale_refund_price` int NOT NULL DEFAULT 0,
  `browse_convert_percent` int NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品日统计';
```

然后执行目标字段扩展：

```sql
ALTER TABLE `product_statistics`
  ADD COLUMN `cart_user_count` int NOT NULL DEFAULT 0 COMMENT '加购访客数' AFTER `cart_count`,
  ADD COLUMN `paid_order_count` int NOT NULL DEFAULT 0 COMMENT '包含该SPU的去重支付订单数' AFTER `order_pay_count`,
  ADD COLUMN `paid_buyer_count` int NOT NULL DEFAULT 0 COMMENT '购买该SPU的去重买家数' AFTER `paid_order_count`,
  ADD COLUMN `cost_amount` bigint NOT NULL DEFAULT 0 COMMENT '商品净成本，分' AFTER `after_sale_refund_price`,
  ADD COLUMN `gross_profit` bigint NOT NULL DEFAULT 0 COMMENT '商品毛利，分' AFTER `cost_amount`,
  ADD COLUMN `gross_margin_percent` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '毛利率百分比' AFTER `gross_profit`,
  ADD COLUMN `estimated_cost_item_count` int NOT NULL DEFAULT 0 AFTER `gross_margin_percent`,
  ADD COLUMN `missing_cost_item_count` int NOT NULL DEFAULT 0 AFTER `estimated_cost_item_count`,
  ADD UNIQUE KEY `uk_tenant_time_spu` (`tenant_id`, `time`, `spu_id`);
```

实际迁移文件必须通过 `information_schema.COLUMNS` 和 `information_schema.STATISTICS` 判断字段与索引是否存在，再动态执行每一条 `ALTER`，保证重复执行不会失败。不得用捕获错误后继续的方式伪装幂等。

每个字段使用同一守卫形式，以下以 `cart_user_count` 为完整示例，其余字段只替换查询字段名和 `ALTER` 内容：

```sql
SET @column_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'product_statistics'
    AND COLUMN_NAME = 'cart_user_count'
);
SET @ddl = IF(
  @column_exists = 0,
  'ALTER TABLE `product_statistics` ADD COLUMN `cart_user_count` int NOT NULL DEFAULT 0 COMMENT ''加购访客数'' AFTER `cart_count`',
  'SELECT ''product_statistics.cart_user_count already exists'' AS message'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
```

字段兼容规则：

- `browse_count` 改为商品详情 PV，数据源仅为 `PRODUCT_DETAIL_VIEW`。
- `browse_user_count` 改为商品详情 UV。
- `cart_count` 改为成功加购次数；新增 `cart_user_count` 表示加购访客数。
- `order_pay_count` 继续表示支付件数。
- `browse_convert_percent` 保留给旧页面兼容；新看板转化率在查询层按 `paid_order_count / browse_count` 精确计算。

### 4.2 trade_order_item

```sql
ALTER TABLE `trade_order_item`
  ADD COLUMN `cost_price` int DEFAULT NULL COMMENT '下单时SKU单件成本快照，分' AFTER `price`,
  ADD COLUMN `cost_estimated` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否使用历史估算成本' AFTER `cost_price`;
```

新订单必须在 `TradePriceCalculatorHelper.buildCalculateResp` 中把 `ProductSkuRespDTO.costPrice` 写入价格计算项，再由 `TradeOrderConvert` 映射到订单明细。看板查询不得读取商品当前成本替代新订单快照。

## 5. 历史成本回填

迁移时只回填 `cost_price IS NULL` 的历史订单明细：

```sql
UPDATE `trade_order_item` item
JOIN `product_sku` sku
  ON sku.id = item.sku_id
 AND sku.tenant_id = item.tenant_id
SET item.cost_price = sku.cost_price,
    item.cost_estimated = b'1',
    item.update_time = CURRENT_TIMESTAMP
WHERE item.cost_price IS NULL;
```

回填后执行：

```sql
SELECT tenant_id,
       COUNT(*) AS paid_item_rows,
       SUM(cost_price IS NULL) AS missing_cost_rows,
       SUM(cost_estimated = b'1') AS estimated_cost_rows
FROM trade_order_item item
JOIN trade_order orders ON orders.id = item.order_id
WHERE orders.pay_status = b'1'
GROUP BY tenant_id;
```

解释：

- 新订单：`cost_estimated=0` 且 `cost_price` 非空，质量为精确。
- 历史可回填：`cost_estimated=1`，质量为估算。
- SKU 已删除或成本为空：`cost_price` 保持空，质量为不完整。

## 6. 聚合重算规则

每个租户、每个自然日使用一个事务执行：

1. 删除 `statistics_traffic_daily` 的 `(tenant_id, day)` 记录。
2. 删除 `product_statistics` 的 `(tenant_id, time)` 记录。
3. 从原始事件聚合站点和商品 PV、UV、加购、结算数据。
4. 从支付订单、订单明细和售后表聚合订单、收入、退款与成本。
5. 插入站点日聚合。
6. 批量插入商品日聚合。

重算使用半开区间 `[day 00:00:00, day+1 00:00:00)`，不得使用 `23:59:59`。

并发控制使用 Redis 租户日锁：

`statistics:dashboard:aggregate:{tenantId}:{yyyy-MM-dd}`

锁等待0秒、租约4分钟；未获得锁时跳过本次执行并记录 INFO 日志，不并发删除同一天数据。

## 7. 定时任务数据

迁移脚本幂等插入三条任务：

| name | handler_name | handler_param | cron_expression | 作用 |
|---|---|---|---|---|
| 数据看板-当日聚合 | dashboardStatisticsJob | 0 | `0 */5 * * * ?` | 每5分钟重算当天 |
| 数据看板-昨日定稿 | dashboardStatisticsJob | 1 | `0 10 0 * * ?` | 00:10重算昨日 |
| 数据看板-事件清理 | dashboardBehaviorCleanupJob | 180 | `0 30 2 * * ?` | 删除180天前事件 |

`handler_param=N` 表示从今天起向前重算 N 天；`0` 只重算今天，`1` 只重算昨天，手工回填 `30` 时重算最近30个完整自然日。

原 `productStatisticsJob` 在数据看板启用后必须停止调度，避免两个任务同时写 `product_statistics`。代码保留用于兼容，但发布核对必须确认调度状态。

三条任务以停止状态插入，发布人员完成服务和页面验证后再在任务管理中启用：

```sql
INSERT INTO infra_job
  (name, status, handler_name, handler_param, cron_expression, retry_count,
   retry_interval, monitor_timeout, creator, create_time, updater, update_time, deleted)
SELECT '数据看板-当日聚合', 2, 'dashboardStatisticsJob', '0', '0 */5 * * * ?',
       0, 0, 240000, 'migration', NOW(), 'migration', NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM infra_job
  WHERE handler_name='dashboardStatisticsJob' AND handler_param='0' AND deleted=b'0'
);

INSERT INTO infra_job
  (name, status, handler_name, handler_param, cron_expression, retry_count,
   retry_interval, monitor_timeout, creator, create_time, updater, update_time, deleted)
SELECT '数据看板-昨日定稿', 2, 'dashboardStatisticsJob', '1', '0 10 0 * * ?',
       0, 0, 240000, 'migration', NOW(), 'migration', NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM infra_job
  WHERE handler_name='dashboardStatisticsJob' AND handler_param='1' AND deleted=b'0'
);

INSERT INTO infra_job
  (name, status, handler_name, handler_param, cron_expression, retry_count,
   retry_interval, monitor_timeout, creator, create_time, updater, update_time, deleted)
SELECT '数据看板-事件清理', 2, 'dashboardBehaviorCleanupJob', '180', '0 30 2 * * ?',
       0, 0, 240000, 'migration', NOW(), 'migration', NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM infra_job
  WHERE handler_name='dashboardBehaviorCleanupJob' AND handler_param='180' AND deleted=b'0'
);
```

## 8. 菜单与权限SQL

菜单通过 `path='/dashboard' AND deleted=b'0'` 判断唯一性：

```sql
INSERT INTO system_menu
  (name, permission, type, sort, parent_id, path, icon, component, component_name,
   status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '数据看板', '', 2, 5, 0, '/dashboard', 'ep:data-analysis',
       'dashboard/index', 'FurnitureDashboard', 0, b'1', b'1', b'1',
       'migration', NOW(), 'migration', NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM system_menu WHERE path='/dashboard' AND deleted=b'0'
);

SET @dashboard_menu_id = (
  SELECT id FROM system_menu WHERE path='/dashboard' AND deleted=b'0' ORDER BY id LIMIT 1
);

INSERT INTO system_menu
  (name, permission, type, sort, parent_id, path, icon, component, component_name,
   status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '数据看板查询', 'statistics:dashboard:query', 3, 1, @dashboard_menu_id,
       '', '', '', NULL, 0, b'1', b'1', b'1', 'migration', NOW(), 'migration', NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM system_menu WHERE permission='statistics:dashboard:query' AND deleted=b'0'
);

INSERT INTO system_menu
  (name, permission, type, sort, parent_id, path, icon, component, component_name,
   status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '数据看板导出', 'statistics:dashboard:export', 3, 2, @dashboard_menu_id,
       '', '', '', NULL, 0, b'1', b'1', b'1', 'migration', NOW(), 'migration', NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM system_menu WHERE permission='statistics:dashboard:export' AND deleted=b'0'
);
```

迁移不自动猜测业务角色。发布人员在角色管理中把“数据看板”“数据看板查询”“数据看板导出”分配给指定角色；超级管理员按现有系统规则自动拥有权限。

## 9. 迁移验证

```sql
SHOW CREATE TABLE statistics_behavior_event;
SHOW CREATE TABLE statistics_traffic_daily;
SHOW COLUMNS FROM product_statistics;
SHOW COLUMNS FROM trade_order_item LIKE 'cost_%';
SELECT id, name, path, permission, parent_id
FROM system_menu
WHERE path='/dashboard'
   OR permission IN ('statistics:dashboard:query', 'statistics:dashboard:export');
```

必须满足：

- 两张新表存在且唯一索引正确。
- `product_statistics` 每租户、日期、SPU只有一行。
- 新订单明细成本非空且 `cost_estimated=0`。
- 历史缺失成本可被查询并显示为 `INCOMPLETE`。
- 同一迁移脚本连续执行两次不报错、不重复菜单和任务。

## 10. 回滚原则

回滚应用版本时：

- 先停止三条数据看板任务。
- 保留新表和新增列，旧版本不会读取它们，避免丢失事件和成本快照。
- 删除数据看板菜单或设置为不可见。
- 只有在确认不再需要数据且完成备份后，才另行执行物理删表、删列脚本。

生产回滚默认采用“停用功能但保留数据”，禁止把 `DROP TABLE` 放入自动发布回滚脚本。
