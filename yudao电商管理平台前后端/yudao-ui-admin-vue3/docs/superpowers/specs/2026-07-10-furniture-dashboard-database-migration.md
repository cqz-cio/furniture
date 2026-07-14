# 家具电商数据看板数据库与数据迁移设计

> 日期：2026-07-10
> 数据库：MySQL 8.x
> 业务时区：Asia/Shanghai
> 关联设计：[家具电商数据看板开发设计](./2026-07-10-furniture-commerce-data-dashboard-design.md)

## 1. 目标与执行边界

本设计定义数据看板的可执行数据库迁移、历史成本分批回填、日聚合重算、数据清理和重复执行验证。所有金额使用租户结算币种的最小货币单位；一期家具租户默认币种为 USD。

迁移文件固定为：

    yudao-cloud/sql/mysql/statistics-commerce-dashboard.sql

必须遵守以下边界：

- 结构迁移、历史成本回填、历史日聚合是三个独立阶段。结构脚本不得包含无边界的全表 UPDATE。
- 所有阶段必须显式指定并校验 tenant_id；默认只允许配置启用的家具租户 121，禁止自动遍历全部租户。
- 所有业务表关联同时约束 tenant_id 和 deleted = 0；看板代码和脚本不得使用 TenantIgnore 绕过租户插件。
- MySQL DDL 会隐式提交，不能声称整份迁移可由单一事务回滚。幂等依赖前置检查、information_schema 守卫、迁移锁和执行后断言。
- 新表可以使用 CREATE TABLE IF NOT EXISTS，但脚本随后必须验证完整字段、类型、可空性和索引；已有错误结构不得被“表已存在”掩盖。
- product_statistics、trade_order_item 等基础表不存在时立即停止，不得由本迁移创建一个不完整的替代表。
- 同一结构脚本在生产结构副本连续执行两次必须均退出 0；第二次不得新增字段、索引、菜单或任务，也不得改写业务数据。

## 2. 执行阶段

| 阶段 | 操作 | 是否写业务数据 | 可否重跑 |
|---|---|---:|---:|
| A | 只读预检、重复数据与类型检查 | 否 | 是 |
| B | 获取迁移锁并执行结构、菜单、停止态任务迁移 | 仅元数据 | 是 |
| C | 执行结构后断言并再次运行结构脚本 | 否 | 是 |
| D | 按租户、主键游标分批回填历史成本 | 是 | 是 |
| E | 按租户、业务日期重算历史财务聚合 | 是 | 是 |
| F | 数据对账通过后由发布流程逐项启用任务和入口 | 否 | 是 |

结构脚本开头和结尾必须包含迁移锁：

~~~sql
SELECT GET_LOCK(CONCAT(DATABASE(), ':statistics-commerce-dashboard:v2'), 60) AS migration_lock;
-- migration_lock 必须为 1，否则立即停止。

SET time_zone = '+08:00';

-- 结构、菜单、停止态任务迁移

SELECT RELEASE_LOCK(CONCAT(DATABASE(), ':statistics-commerce-dashboard:v2')) AS released;
~~~

发布工具必须在异常路径也释放连接；连接断开时 MySQL 会释放该连接持有的锁。

## 3. 迁移前只读预检

### 3.1 基础对象与版本

~~~sql
SELECT VERSION() AS mysql_version, @@time_zone AS session_time_zone;

SELECT table_name
FROM information_schema.TABLES
WHERE table_schema = DATABASE()
  AND table_name IN (
    'product_statistics', 'trade_order', 'trade_order_item',
    'trade_after_sale', 'product_spu', 'product_sku',
    'system_menu', 'infra_job'
  )
ORDER BY table_name;
~~~

结果必须包含当前部署实际使用的全部基础表。缺少 product_statistics、trade_order、trade_order_item、trade_after_sale、product_spu 或 product_sku 时停止发布。若当前调度平台不使用 infra_job，任务创建改由受控调度 API 完成，但不得静默跳过。

### 3.2 唯一键冲突预检

在添加 product_statistics 唯一键前执行：

~~~sql
SELECT tenant_id, time, spu_id, COUNT(*) AS duplicate_count
FROM product_statistics
WHERE deleted = b'0'
GROUP BY tenant_id, time, spu_id
HAVING COUNT(*) > 1;
~~~

结果必须为 0 行。发现重复时先导出冲突行，由数据所有者确认保留规则；迁移脚本不得自行删除或合并。

菜单和权限冲突预检：

~~~sql
SELECT path, COUNT(*) AS duplicate_count
FROM system_menu
WHERE path = '/dashboard' AND deleted = b'0'
GROUP BY path
HAVING COUNT(*) > 1;

SELECT permission, COUNT(*) AS duplicate_count
FROM system_menu
WHERE permission IN (
  'statistics:dashboard:query',
  'statistics:dashboard:profit-query',
  'statistics:dashboard:export',
  'statistics:dashboard:profit-export'
) AND deleted = b'0'
GROUP BY permission
HAVING COUNT(*) > 1;
~~~

任一查询返回数据即停止，不以 ORDER BY id LIMIT 1 掩盖重复配置。

### 3.3 租户边界与待回填规模

~~~sql
SELECT tenant_id, COUNT(*) AS item_rows, MIN(id) AS min_id, MAX(id) AS max_id
FROM trade_order_item
WHERE deleted = b'0'
GROUP BY tenant_id
ORDER BY tenant_id;

SELECT i.tenant_id, COUNT(*) AS cross_tenant_or_orphan_rows
FROM trade_order_item i
LEFT JOIN trade_order o
  ON o.id = i.order_id
 AND o.tenant_id = i.tenant_id
 AND o.deleted = b'0'
WHERE i.deleted = b'0'
  AND o.id IS NULL
GROUP BY i.tenant_id;
~~~

第二个查询必须为 0 行。发布单记录启用租户、每租户待扫描行数、主键范围、预估批次数和可接受执行窗口。

### 3.4 字段兼容预检

若目标字段已经存在，必须检查类型和可空性而不是直接跳过：

~~~sql
SELECT table_name, column_name, column_type, is_nullable, column_default
FROM information_schema.COLUMNS
WHERE table_schema = DATABASE()
  AND (
    (table_name = 'trade_order_item' AND column_name IN ('cost_price', 'cost_estimated'))
    OR
    (table_name = 'product_statistics' AND column_name IN (
      'order_pay_price', 'after_sale_refund_price', 'cost_amount',
      'gross_profit', 'gross_margin_percent', 'exact_cost_item_count',
      'estimated_cost_item_count', 'missing_cost_item_count',
      'known_cost_amount', 'traffic_data_status', 'traffic_watermark'
    ))
  )
ORDER BY table_name, ordinal_position;
~~~

已有列与目标定义不一致时必须执行受守卫的 MODIFY COLUMN，或停止并提交单独兼容迁移；不得继续使用旧的 int 金额或非空默认 0 利润列。

## 4. 枚举与质量状态

### 4.1 行为事件

| event_type | 常量 |
|---:|---|
| 1 | HOME_VIEW |
| 2 | PRODUCT_DETAIL_VIEW |
| 3 | ADD_TO_CART |
| 4 | CHECKOUT_START |

| event_source | 常量 |
|---:|---|
| 1 | PUBLIC_WEB |
| 2 | SERVER_CART |

公共追踪只允许事件类型 1、2、4 且来源固定为 PUBLIC_WEB。ADD_TO_CART 必须由 Trade 购物车事务提交后的内网调用写入，来源固定为 SERVER_CART；客户端不能选择来源。

| device_type | 常量 |
|---:|---|
| 1 | DESKTOP |
| 2 | MOBILE |
| 3 | TABLET |
| 9 | OTHER |

| traffic_quality | 常量 | 是否进入业务指标 |
|---:|---|---:|
| 1 | ACCEPTED | 是 |
| 2 | BOT | 否 |
| 3 | INTERNAL | 否 |
| 4 | TEST | 否 |
| 5 | ABNORMAL | 否 |

硬限流拒绝的请求不写原始事件表，只增加安全指标。exclusion_reason 只保存受控原因代码，不保存 IP、User-Agent 或请求体。

### 4.2 聚合质量

| profit_data_quality | 常量 |
|---:|---|
| 1 | EXACT |
| 2 | MIXED |
| 3 | ESTIMATED |
| 4 | INCOMPLETE |
| 5 | NOT_APPLICABLE |

| traffic_data_status | 常量 |
|---:|---|
| 1 | COMPLETE |
| 2 | PARTIAL |
| 3 | UNAVAILABLE |

`traffic_data_status` 只描述覆盖范围。`FRESH`、`DELAYED`、`STALE` 由接口根据相关水位和 `last_successful_run_at` 动态计算，不写入覆盖状态列。

## 5. 新表

### 5.1 HMAC 日版本登记表

HMAC 新版本只能在 Asia/Shanghai 自然日边界生效。数据库用每租户每日唯一登记约束写入版本；表内不保存密钥或可恢复密钥的材料。

~~~sql
CREATE TABLE IF NOT EXISTS statistics_dashboard_hmac_day (
  id bigint NOT NULL AUTO_INCREMENT,
  day date NOT NULL,
  hash_key_version smallint unsigned NOT NULL,
  activated_at datetime(3) NOT NULL COMMENT '必须为Asia/Shanghai日界',
  destroy_after datetime(3) DEFAULT NULL COMMENT '最早可销毁时间，实际销毁还需确认删除请求完成',
  creator varchar(64) DEFAULT '',
  create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updater varchar(64) DEFAULT '',
  update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_hmac_tenant_day (tenant_id, day),
  UNIQUE KEY uk_hmac_tenant_day_version (tenant_id, day, hash_key_version),
  CONSTRAINT chk_hmac_day_boundary CHECK (
    DATE(activated_at) = day AND TIME(activated_at) = '00:00:00'
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统计-HMAC每日写入版本';
~~~

待启用版本可在前一日完成 KMS 预加载，但对应日登记只能写一个版本。常规回滚不得修改当天登记；旧钥至少保留到该版本原始事件全部物理清理且相关删除请求处理完成。

### 5.2 原始行为事件表

~~~sql
CREATE TABLE IF NOT EXISTS statistics_behavior_event (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '事件主键',
  event_id varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事件生产方幂等编号',
  event_type tinyint NOT NULL COMMENT '1首页 2商品详情 3加购 4开始结算',
  event_source tinyint NOT NULL COMMENT '1公共网页 2服务端购物车',
  visitor_hash char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'HMAC-SHA256访客摘要',
  session_hash char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT 'HMAC-SHA256会话摘要；服务端加购可空',
  hash_key_version smallint unsigned NOT NULL COMMENT 'HMAC密钥版本',
  user_id bigint DEFAULT NULL COMMENT '已登录会员编号',
  spu_id bigint DEFAULT NULL COMMENT '商品SPU编号',
  sku_id bigint DEFAULT NULL COMMENT '商品SKU编号',
  quantity int DEFAULT NULL COMMENT '加购数量',
  page_path varchar(255) NOT NULL COMMENT '标准化路径',
  referrer_host varchar(255) DEFAULT NULL COMMENT '来源域名',
  device_type tinyint NOT NULL DEFAULT 9 COMMENT '服务端分类设备',
  traffic_quality tinyint NOT NULL DEFAULT 1 COMMENT '1接纳 2机器人 3内部 4测试 5异常',
  exclusion_reason varchar(64) DEFAULT NULL COMMENT '受控排除原因代码',
  occurred_at datetime(3) NOT NULL COMMENT '服务端接收时间',
  event_day date NOT NULL COMMENT 'occurred_at在Asia/Shanghai对应自然日',
  creator varchar(64) DEFAULT '',
  create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updater varchar(64) DEFAULT '',
  update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_event (tenant_id, event_id),
  KEY idx_tenant_quality_type_time (tenant_id, traffic_quality, event_type, occurred_at),
  KEY idx_tenant_spu_type_time (tenant_id, spu_id, event_type, occurred_at),
  KEY idx_tenant_time_visitor (tenant_id, occurred_at, hash_key_version, visitor_hash),
  CONSTRAINT chk_statistics_behavior_event_type CHECK (event_type IN (1, 2, 3, 4)),
  CONSTRAINT chk_statistics_behavior_event_source CHECK (event_source IN (1, 2)),
  CONSTRAINT chk_statistics_behavior_event_source_type CHECK (
    (event_source = 1 AND event_type IN (1, 2, 4))
    OR (event_source = 2 AND event_type = 3)
  ),
  CONSTRAINT chk_statistics_behavior_event_quantity CHECK (quantity IS NULL OR quantity BETWEEN 1 AND 999),
  CONSTRAINT chk_statistics_behavior_event_quality CHECK (traffic_quality BETWEEN 1 AND 5),
  CONSTRAINT chk_statistics_behavior_event_day CHECK (event_day = DATE(occurred_at)),
  CONSTRAINT fk_behavior_event_hmac_day
    FOREIGN KEY (tenant_id, event_day, hash_key_version)
    REFERENCES statistics_dashboard_hmac_day (tenant_id, day, hash_key_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统计-原始行为事件';
~~~

库中不保存 HMAC 密钥、原始 visitorId、sessionId、IP、完整 Referrer 或完整 User-Agent。hash_key_version 只用于选择密钥版本和删除检索。

### 5.3 公共埋点接收缺口表

公共事件的 5 秒服务端去重依赖 Redis。Redis 不可用时为避免绕过去重造成灌水或重复，接口必须拒绝本次公共事件并写入不含个人标识的缺口账本；聚合发现对应日期存在缺口后，将流量覆盖标为 `PARTIAL`。这不是行为事件表的替代来源，不能补写 PV/UV。

~~~sql
CREATE TABLE IF NOT EXISTS statistics_behavior_ingestion_gap (
  id bigint NOT NULL AUTO_INCREMENT,
  day date NOT NULL COMMENT 'Asia/Shanghai自然日',
  reason_code varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '例如DEDUP_REDIS_UNAVAILABLE',
  bucket_start datetime(3) NOT NULL COMMENT '受控五分钟聚合桶开始时间',
  first_seen_at datetime(3) NOT NULL,
  last_seen_at datetime(3) NOT NULL,
  rejected_count bigint NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '',
  create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updater varchar(64) DEFAULT '',
  update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_gap_tenant_day_reason_bucket (tenant_id, day, reason_code, bucket_start),
  KEY idx_gap_tenant_day (tenant_id, day),
  CONSTRAINT chk_behavior_gap_day CHECK (day = DATE(bucket_start)),
  CONSTRAINT chk_behavior_gap_rejected CHECK (rejected_count > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统计-公共埋点接收缺口';
~~~

写入使用受控 `INSERT ... ON DUPLICATE KEY UPDATE`，只递增 `rejected_count` 和推进 `last_seen_at`；该语句不是行为事件幂等写入，不能用于吞掉其他数据库错误。缺口表不含原始 UUID、IP、请求体或用户字段，日聚合和历史状态长期保留，以便解释为什么当天不能显示为完整流量。

### 5.4 站点日聚合表

流量列允许 NULL，用于表达未采集或覆盖不完整；业务上的真实 0 只能与 COMPLETE 状态一起写入。财务金额使用 bigint。存在缺失成本时，cost_amount、gross_profit 和 gross_margin_percent 必须为 NULL。

~~~sql
CREATE TABLE IF NOT EXISTS statistics_traffic_daily (
  id bigint NOT NULL AUTO_INCREMENT,
  day date NOT NULL COMMENT 'Asia/Shanghai自然日',
  currency_code char(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  home_pv bigint DEFAULT NULL,
  home_uv bigint DEFAULT NULL,
  product_detail_pv bigint DEFAULT NULL,
  product_detail_uv bigint DEFAULT NULL,
  add_cart_count bigint DEFAULT NULL,
  add_cart_user_count bigint DEFAULT NULL,
  checkout_start_count bigint DEFAULT NULL,
  paid_order_count bigint NOT NULL DEFAULT 0,
  paid_buyer_count bigint NOT NULL DEFAULT 0,
  paid_item_count bigint NOT NULL DEFAULT 0,
  paid_revenue bigint NOT NULL DEFAULT 0 COMMENT '支付日销售额',
  refund_amount bigint NOT NULL DEFAULT 0 COMMENT '退款成功日退款额',
  net_revenue bigint NOT NULL DEFAULT 0,
  known_cost_amount bigint NOT NULL DEFAULT 0 COMMENT '已知净成本，仅用于覆盖解释',
  cost_amount bigint DEFAULT NULL COMMENT '成本完整时的净成本',
  gross_profit bigint DEFAULT NULL,
  gross_margin_percent decimal(12,4) DEFAULT NULL,
  exact_cost_item_count bigint NOT NULL DEFAULT 0,
  estimated_cost_item_count bigint NOT NULL DEFAULT 0,
  missing_cost_item_count bigint NOT NULL DEFAULT 0,
  profit_data_quality tinyint NOT NULL DEFAULT 5,
  accepted_event_count bigint NOT NULL DEFAULT 0,
  excluded_event_count bigint NOT NULL DEFAULT 0,
  traffic_data_status tinyint NOT NULL DEFAULT 3,
  traffic_watermark datetime(3) DEFAULT NULL,
  trade_watermark datetime(3) DEFAULT NULL,
  refund_watermark datetime(3) DEFAULT NULL,
  last_successful_run_at datetime(3) DEFAULT NULL,
  creator varchar(64) DEFAULT '',
  create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updater varchar(64) DEFAULT '',
  update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_day (tenant_id, day),
  KEY idx_tenant_update_time (tenant_id, update_time),
  CONSTRAINT chk_statistics_traffic_status CHECK (traffic_data_status BETWEEN 1 AND 3),
  CONSTRAINT chk_statistics_profit_quality CHECK (profit_data_quality BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统计-站点日聚合';
~~~

### 5.5 回填检查点表

~~~sql
CREATE TABLE IF NOT EXISTS statistics_dashboard_migration_checkpoint (
  id bigint NOT NULL AUTO_INCREMENT,
  phase varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  last_id bigint NOT NULL DEFAULT 0,
  upper_bound_id bigint NOT NULL DEFAULT 0,
  processed_rows bigint NOT NULL DEFAULT 0,
  updated_rows bigint NOT NULL DEFAULT 0,
  status tinyint NOT NULL DEFAULT 1 COMMENT '1待执行 2执行中 3完成 4失败 5暂停',
  error_code varchar(64) DEFAULT NULL,
  started_at datetime(3) DEFAULT NULL,
  completed_at datetime(3) DEFAULT NULL,
  creator varchar(64) DEFAULT '',
  create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updater varchar(64) DEFAULT '',
  update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_phase (tenant_id, phase)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统计-迁移与回填检查点';
~~~

检查点只记录主键水位、计数和错误代码，不记录订单、会员或访客明细。

## 6. 现有表扩展

### 6.1 product_statistics

基础表必须已经存在。结构迁移逐列使用 information_schema 守卫完成以下目标：

| 字段 | 目标定义 |
|---|---|
| order_pay_price | bigint NOT NULL DEFAULT 0 |
| after_sale_refund_price | bigint NOT NULL DEFAULT 0 |
| cart_user_count | bigint NOT NULL DEFAULT 0 |
| paid_order_count | bigint NOT NULL DEFAULT 0 |
| paid_buyer_count | bigint NOT NULL DEFAULT 0 |
| known_cost_amount | bigint NOT NULL DEFAULT 0 |
| cost_amount | bigint NULL |
| gross_profit | bigint NULL |
| gross_margin_percent | decimal(12,4) NULL |
| exact_cost_item_count | bigint NOT NULL DEFAULT 0 |
| estimated_cost_item_count | bigint NOT NULL DEFAULT 0 |
| missing_cost_item_count | bigint NOT NULL DEFAULT 0 |
| profit_data_quality | tinyint NOT NULL DEFAULT 5 |
| traffic_data_status | tinyint NOT NULL DEFAULT 3 |
| traffic_watermark | datetime(3) NULL |
| trade_watermark | datetime(3) NULL |
| refund_watermark | datetime(3) NULL |
| last_successful_run_at | datetime(3) NULL |

添加唯一键 uk_tenant_time_spu (tenant_id, time, spu_id) 前，3.2 节重复预检必须为 0 行。历史 browse_count、browse_user_count 在埋点启用日前保持原值但 traffic_data_status 为 UNAVAILABLE；新看板不得把这些旧值视为新埋点流量。

存在 missing_cost_item_count > 0 时，cost_amount、gross_profit、gross_margin_percent 全部写 NULL。known_cost_amount 可展示成本覆盖，但不得用于返回完整利润。

### 6.2 trade_order_item

目标字段：

~~~sql
ALTER TABLE trade_order_item
  ADD COLUMN cost_price bigint DEFAULT NULL COMMENT '下单时SKU单件成本快照，最小货币单位' AFTER price,
  ADD COLUMN cost_estimated bit(1) NOT NULL DEFAULT b'0' COMMENT '是否历史估算' AFTER cost_price;
~~~

实际脚本必须逐列守卫，不能直接执行上面的组合 ALTER。若 cost_price 已存在但仍为 int，在确认无越界和复制延迟窗口后执行受守卫的 MODIFY COLUMN cost_price bigint NULL。

新订单由交易服务在创建订单明细时写入成本快照，cost_estimated = 0。看板查询不得用商品当前成本替代新订单快照。

### 6.3 可执行的字段守卫模式

每个新增列都采用以下模式；下面以 hash_key_version 为例：

~~~sql
SET @column_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE table_schema = DATABASE()
    AND table_name = 'statistics_behavior_event'
    AND column_name = 'hash_key_version'
);

SET @ddl = IF(
  @column_exists = 0,
  'ALTER TABLE statistics_behavior_event ADD COLUMN hash_key_version smallint unsigned NOT NULL AFTER session_hash',
  'DO 0'
);
PREPARE dashboard_stmt FROM @ddl;
EXECUTE dashboard_stmt;
DEALLOCATE PREPARE dashboard_stmt;

SELECT COUNT(*) INTO @column_matches
FROM information_schema.COLUMNS
WHERE table_schema = DATABASE()
  AND table_name = 'statistics_behavior_event'
  AND column_name = 'hash_key_version'
  AND column_type = 'smallint unsigned'
  AND is_nullable = 'NO';
~~~

迁移执行器必须断言 @column_matches = 1；否则退出非 0。索引守卫不仅按索引名判断，还必须核对 NON_UNIQUE 和按 SEQ_IN_INDEX 排序后的列清单。所有目标字段和索引都必须在实际 SQL 文件中展开，禁止以“其余字段同理”代替可执行语句。

## 7. 历史成本分批回填

### 7.1 原则

- 回填是独立、可暂停、可续跑的运维动作，不放在结构脚本事务中。
- 每次只处理一个显式 tenant_id；启用租户之外的请求直接拒绝。
- 默认 batch_size = 10000，允许范围 1000 至 20000。
- 检查点的 upper_bound_id 在本轮开始时冻结，避免持续新增订单导致永不完成。
- 只更新 cost_price IS NULL 且能找到同租户、未删除 SKU 成本的行；已有快照绝不覆盖。
- 回填成功行设置 cost_estimated = 1；SKU 不存在、已删除或成本为空的行继续保持 NULL。
- 每批单独提交；锁等待、复制延迟或数据库负载越过发布阈值时将状态写为 PAUSED 并停止。

### 7.2 初始化检查点

~~~sql
SET @tenant_id = 121;

INSERT INTO statistics_dashboard_migration_checkpoint
  (tenant_id, phase, last_id, upper_bound_id, status, creator, create_time, updater, update_time, deleted)
SELECT @tenant_id, 'COST_BACKFILL_V1', 0, COALESCE(MAX(id), 0), 1,
       'migration', NOW(3), 'migration', NOW(3), b'0'
FROM trade_order_item
WHERE tenant_id = @tenant_id AND deleted = b'0'
ON DUPLICATE KEY UPDATE
  upper_bound_id = upper_bound_id,
  update_time = NOW(3);
~~~

已完成检查点不得自动扩大 upper_bound_id；扩大范围需要新的 phase 版本或审批后的显式续跑。

### 7.3 单批执行模板

~~~sql
SET @tenant_id = 121;
SET @batch_size = 10000;

START TRANSACTION;

SELECT last_id, upper_bound_id
INTO @last_id, @upper_bound_id
FROM statistics_dashboard_migration_checkpoint
WHERE tenant_id = @tenant_id
  AND phase = 'COST_BACKFILL_V1'
  AND deleted = b'0'
FOR UPDATE;

SELECT COALESCE(MAX(id), @last_id)
INTO @batch_end
FROM (
  SELECT id
  FROM trade_order_item
  WHERE tenant_id = @tenant_id
    AND deleted = b'0'
    AND id > @last_id
    AND id <= @upper_bound_id
  ORDER BY id
  LIMIT 10000
) AS batch_ids;

SELECT COUNT(*)
INTO @scanned_rows
FROM trade_order_item
WHERE tenant_id = @tenant_id
  AND deleted = b'0'
  AND id > @last_id
  AND id <= @batch_end;

UPDATE trade_order_item item
JOIN product_sku sku
  ON sku.id = item.sku_id
 AND sku.tenant_id = item.tenant_id
 AND sku.deleted = b'0'
SET item.cost_price = sku.cost_price,
    item.cost_estimated = b'1',
    item.update_time = NOW(3)
WHERE item.tenant_id = @tenant_id
  AND item.deleted = b'0'
  AND item.id > @last_id
  AND item.id <= @batch_end
  AND item.cost_price IS NULL
  AND sku.cost_price IS NOT NULL;

SET @updated_rows = ROW_COUNT();

UPDATE statistics_dashboard_migration_checkpoint
SET last_id = @batch_end,
    processed_rows = processed_rows + @scanned_rows,
    updated_rows = updated_rows + @updated_rows,
    status = IF(@batch_end = @last_id OR @batch_end >= upper_bound_id, 3, 2),
    started_at = COALESCE(started_at, NOW(3)),
    completed_at = IF(@batch_end = @last_id OR @batch_end >= upper_bound_id, NOW(3), NULL),
    update_time = NOW(3)
WHERE tenant_id = @tenant_id
  AND phase = 'COST_BACKFILL_V1'
  AND deleted = b'0';

COMMIT;
~~~

生产执行器以实际 @batch_size 生成 LIMIT；不得接受未经校验的任意 SQL 片段。若 @batch_end = @last_id，标记完成并停止。

### 7.4 回填质量查询

只统计已支付订单，且所有连接必须同租户：

~~~sql
SELECT i.tenant_id,
       SUM(i.cost_price IS NOT NULL AND i.cost_estimated = b'0') AS exact_cost_item_count,
       SUM(i.cost_price IS NOT NULL AND i.cost_estimated = b'1') AS estimated_cost_item_count,
       SUM(i.cost_price IS NULL) AS missing_cost_item_count
FROM trade_order_item i
JOIN trade_order o
  ON o.id = i.order_id
 AND o.tenant_id = i.tenant_id
 AND o.deleted = b'0'
WHERE i.deleted = b'0'
  AND o.pay_status = b'1'
  AND o.pay_time IS NOT NULL
  AND i.tenant_id = 121
GROUP BY i.tenant_id;
~~~

三类数量之和必须等于参与统计的支付订单明细数。缺失成本不阻断看板上线，但必须导致对应日期利润为 NULL 和质量 INCOMPLETE。

## 8. 日聚合与重算

### 8.1 业务日期归属

- 支付订单数、支付买家数、支付件数、支付销售额和支付成本按 trade_order.pay_time 所在自然日归属。
- 退款额按成功售后 trade_after_sale.refund_time 所在自然日归属，不回写支付日。
- 退货并退款的成本冲回按 refund_time 所在自然日归属；仅退款不得冲回成本。
- 退款日可以出现负净销售额或负已知成本，这是业务结果，不得强制归零。
- 支付或退款状态被更正时，事件处理器必须将旧业务日和新业务日都加入重算队列。

### 8.2 重算范围

- 每 5 分钟重算今天和昨天，吸收跨午夜迟到数据。
- 每日 00:10 定稿昨天。
- 每日低峰期滚动重算最近 7 个完整自然日。
- 7 天以外只允许受审计的显式租户、起止日期重算，最大 366 天。
- 所有任务只遍历 DASHBOARD_ENABLED_TENANTS 中的租户；默认仅 121。

### 8.3 单租户单日事务

每个租户、每个 day 使用锁：

    statistics:dashboard:aggregate:{tenantId}:{yyyy-MM-dd}

在事务开始时固定 run_cutoff = NOW(3)。读取各来源时排除 update_time >= run_cutoff 的变更，并把成功扫描截止时间分别写入 traffic_watermark、trade_watermark、refund_watermark。无数据也要写水位，水位表示已成功扫描至何时，不是 MAX(业务时间)。

事务步骤：

1. 校验租户已启用、时区和 currencyCode 配置存在；公共来源不得出现 ADD_TO_CART，服务端购物车来源只能是 ADD_TO_CART。
2. 将 ACCEPTED 且 deleted = 0 的事件写入流量暂存结果；其他质量只累计 excluded_event_count。查询 `statistics_behavior_ingestion_gap`：当日存在任何未删除缺口时，不补 PV/UV，流量覆盖状态强制为 PARTIAL，并记录缺口原因/次数到运行监控。
3. 按 pay_time 聚合支付订单和明细。
4. 按 refund_time 聚合成功退款；只有 RETURN_AND_REFUND、售后完成且有明确退货数量时冲回成本。
5. 将当日支付或退款涉及的 order_item_id 去重后统计 exact、estimated、missing 三类成本行数。
6. known_cost_amount = 已知支付成本 - 已知退货成本。
7. missing > 0 时 cost_amount、gross_profit、gross_margin_percent 均为 NULL，质量为 INCOMPLETE。
8. missing = 0 且无成本贡献行时质量为 NOT_APPLICABLE；否则根据精确和估算行数组合得到 EXACT、MIXED 或 ESTIMATED。
9. 在同一事务内按 tenant_id + day 替换站点聚合，并按 tenant_id + time 替换商品聚合。若采用删除后重建，必须调用显式带租户条件的物理 `DELETE` SQL；MyBatis 逻辑删除会因唯一键不包含 `deleted` 而使随后插入冲突，禁止用于日重算。也可采用完整 upsert 后物理清除当日已不再存在的商品行。
10. 成功后写 last_successful_run_at；失败整体回滚，保留上次成功数据和水位。

任何订单、明细、售后、SPU、SKU、分类连接都必须显式包含：

~~~sql
child.tenant_id = parent.tenant_id
AND child.tenant_id = :tenantId
AND child.deleted = b'0'
AND parent.deleted = b'0'
~~~

实际 Mapper 使用绑定参数，不允许使用字符串替换。商品分页排序仅使用服务端枚举到固定列名的映射。

### 8.4 流量覆盖

- 埋点启用日前的日行 traffic_data_status = UNAVAILABLE，PV/UV/加购/结算列为 NULL。
- consentRequired = true 的站点在用户同意前不创建任何分析标识、不发送行为事件，也不写 cookieless 替代事件；数据库中不存在代表未同意用户的占位记录。
- 同意人群产生的流量只能解释为已同意人群覆盖。策略启用或变更日按实际覆盖标记 PARTIAL，并由接口披露选择偏差。
- 当前自然日为 PARTIAL。
- 完整日已成功扫描且无已知接收中断时为 COMPLETE；真实无访问可写 0。
- 接收中断、`statistics_behavior_ingestion_gap` 记录或质量监控缺口使覆盖状态为 PARTIAL，不得补业务 0；水位超过 10/20 分钟分别由接口派生 DELAYED/STALE 新鲜度，不改写覆盖状态。
- 流量历史不可回填；历史财务回填不得生成虚假 PV/UV。

## 9. 原始事件清理和隐私删除

### 9.1 180 天物理清理

清理任务固定从受控配置读取保留期，不接受任意任务参数覆盖。按单租户、主键批次物理 DELETE，每批最多 10000 行，且同时满足：

- occurred_at 早于保留截止日；
- 对应自然日已成功聚合；
- traffic_watermark 已越过该批最大 create_time；
- 对应日覆盖不为 UNAVAILABLE，且相关水位/最后成功时间未处于 STALE；
- 不存在 legal hold。

禁止使用 MyBatis 逻辑删除代替物理删除。每批记录 tenant、日期、主键范围和删除数量，不记录事件标识。

### 9.2 用户、访客和租户删除

- 会员删除按 tenant_id + user_id 物理删除保留期内原始事件。
- 访客删除由隐私服务使用仍有效的 HMAC 各密钥版本计算摘要，再按 tenant_id + hash_key_version + visitor_hash 分批物理删除。
- 撤回同意后立即停止新写入并清除浏览器标识；服务端删除按审批策略执行。
- 租户退场先关闭网关映射、采集和任务，再物理删除原始事件、检查点和租户专属临时文件；日聚合按业务留存审批处理，最后销毁租户派生 HMAC 密钥。
- 删除任务完成后重算受影响日期，并写只含请求编号、租户、数量和结果的审计记录。

## 10. 任务与权限迁移

### 10.1 任务

任务记录以停止状态幂等创建：

| 名称 | handler | 参数 | Cron |
|---|---|---|---|
| 数据看板-今日昨日滚动 | dashboardStatisticsJob | TODAY_AND_YESTERDAY | 0 */5 * * * ? |
| 数据看板-昨日定稿 | dashboardStatisticsJob | FINALIZE_YESTERDAY | 0 10 0 * * ? |
| 数据看板-近7日修复 | dashboardStatisticsJob | ROLLING_7_COMPLETE_DAYS | 0 40 2 * * ? |
| 数据看板-事件物理清理 | dashboardBehaviorCleanupJob | 空；保留期来自受控配置 | 0 30 3 * * ? |

handler 内再次校验启用租户、参数枚举和日期上限。原 productStatisticsJob 在新任务启用前停止，避免并发写 product_statistics。历史重算和隐私清理不创建可被普通后台用户任意修改参数的常驻任务。

### 10.2 菜单与四类权限

迁移幂等创建：

- 页面 /dashboard，组件 dashboard/index，组件名 FurnitureDashboard。
- statistics:dashboard:query。
- statistics:dashboard:profit-query。
- statistics:dashboard:export。
- statistics:dashboard:profit-export。

按 path 和 permission 预检唯一性后再插入；迁移不自动给任何业务角色授权。超级管理员外的角色授权由发布流程审批并记录审计。

## 11. 迁移后验证与重复执行

### 11.1 结构断言

~~~sql
SHOW CREATE TABLE statistics_behavior_event;
SHOW CREATE TABLE statistics_behavior_ingestion_gap;
SHOW CREATE TABLE statistics_traffic_daily;
SHOW CREATE TABLE statistics_dashboard_hmac_day;
SHOW CREATE TABLE statistics_dashboard_migration_checkpoint;

SELECT table_name, column_name, column_type, is_nullable, column_default
FROM information_schema.COLUMNS
WHERE table_schema = DATABASE()
  AND table_name IN (
    'statistics_behavior_event',
    'statistics_behavior_ingestion_gap',
    'statistics_traffic_daily',
    'statistics_dashboard_hmac_day',
    'statistics_dashboard_migration_checkpoint',
    'product_statistics',
    'trade_order_item'
  )
ORDER BY table_name, ordinal_position;
~~~

必须自动断言：

- 事件来源、HMAC 版本、流量质量、接收缺口、三类成本行数、分源水位字段存在且类型正确；公共来源不能保存加购，服务端购物车来源只能保存加购。
- 所有聚合金额为 bigint；cost_amount、gross_profit、gross_margin_percent 可空。
- trade_order_item.cost_price 为 bigint nullable。
- 四张新表唯一键和 HMAC 外键正确；product_statistics 唯一键正确且无重复。
- 四类权限各一条；四个任务各一条且停止。

### 11.2 数据不变量

~~~sql
SELECT tenant_id, day
FROM statistics_traffic_daily
WHERE missing_cost_item_count > 0
  AND (cost_amount IS NOT NULL
       OR gross_profit IS NOT NULL
       OR gross_margin_percent IS NOT NULL);

SELECT tenant_id, day
FROM statistics_traffic_daily
WHERE exact_cost_item_count < 0
   OR estimated_cost_item_count < 0
   OR missing_cost_item_count < 0
   OR (missing_cost_item_count > 0 AND profit_data_quality <> 4)
   OR (missing_cost_item_count = 0 AND exact_cost_item_count = 0
       AND estimated_cost_item_count = 0 AND profit_data_quality <> 5)
   OR (missing_cost_item_count = 0 AND exact_cost_item_count > 0
       AND estimated_cost_item_count = 0 AND profit_data_quality <> 1)
   OR (missing_cost_item_count = 0 AND exact_cost_item_count > 0
       AND estimated_cost_item_count > 0 AND profit_data_quality <> 2)
   OR (missing_cost_item_count = 0 AND exact_cost_item_count = 0
       AND estimated_cost_item_count > 0 AND profit_data_quality <> 3);

SELECT tenant_id, day
FROM statistics_traffic_daily
WHERE traffic_data_status = 1
  AND traffic_watermark IS NULL;

SELECT g.tenant_id, g.day
FROM statistics_behavior_ingestion_gap g
JOIN statistics_traffic_daily d
  ON d.tenant_id = g.tenant_id
 AND d.day = g.day
 AND d.deleted = b'0'
WHERE g.deleted = b'0'
  AND d.traffic_data_status = 1;

SELECT tenant_id, id, event_type, event_source
FROM statistics_behavior_event
WHERE deleted = b'0'
  AND NOT (
    (event_source = 1 AND event_type IN (1, 2, 4))
    OR (event_source = 2 AND event_type = 3)
  );

SELECT tenant_id, event_day, COUNT(DISTINCT hash_key_version) AS version_count
FROM statistics_behavior_event
WHERE deleted = b'0'
GROUP BY tenant_id, event_day
HAVING COUNT(DISTINCT hash_key_version) > 1;
~~~

六个查询都必须为 0 行。另需按租户抽样对账：

- pay_time 当日支付订单、件数和金额；
- refund_time 当日退款额及退货成本冲回；
- 精确、估算、缺失三类成本行数；
- INCOMPLETE 日利润为空；
- 流量不可用日为 NULL 而不是 0；
- 同一租户同一自然日只有一个 HMAC 写入版本；
- 所有 JOIN 无跨租户或已删除数据。

### 11.3 重复执行门禁

1. 在生产结构副本执行结构脚本一次，退出 0。
2. 保存 information_schema 字段和索引清单、菜单数、任务数。
3. 再执行同一结构脚本，退出 0。
4. 两次清单完全相同，业务表行数和成本字段值不变化。
5. 分批回填从检查点续跑；已填成本不覆盖，总更新行数不增加。
6. 同一租户同一天重算两次，聚合结果和水位规则一致且各只有一行；验证未因逻辑删除与唯一键冲突留下 `deleted=1` 的旧聚合行。

任一条件失败均不得启用任务、埋点或看板。

## 12. 回滚原则

- 回滚应用前先关闭前台埋点、网关站点路由、聚合任务、导出和看板菜单。
- 默认保留新表、新列、成本快照和检查点；旧应用必须忽略这些向前兼容字段。
- 回滚期间不得销毁仍用于读取或隐私删除的 HMAC 旧版本密钥。
- 指标错误优先停止任务、修复后按 tenant + day 重算，不直接恢复整库。
- 只有结构或数据被错误破坏且已完成影响分析时才恢复加密备份。
- 自动回滚脚本禁止 DROP TABLE、DROP COLUMN 或无租户条件的 DELETE。
- 确认不再需要数据、完成审批和可验证备份后，物理删表/删列使用独立销毁脚本，不属于常规发布回滚。
