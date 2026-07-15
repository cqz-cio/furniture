# North America Fulfillment Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 `yudao-module-trade-server` 内交付一个默认关闭、可灰度启用的北美本地履约后端基础，支持美国境内和加拿大境内的普通包裹、LTL、白手套发货单建模、拆单、交运、模拟轨迹、幂等、审计、旧物流字段兼容和订单履约摘要。

**Architecture:** 采用已确认的渐进式履约域方案：`TradeOrder` 继续拥有交易事实，新的 `fulfillment` 包拥有 Shipment、Package、Leg、TrackingEvent 和订单履约摘要；领域状态与 outbox 在同一事务提交；第三方能力通过端口隔离，本计划只实现无网络的 Mock 适配器。旧 `logistics_id/logistics_no` 和旧轨迹接口在迁移期作为首个活动包裹的兼容投影保留。

**Tech Stack:** Java 17, Spring Boot 3.5, MyBatis-Plus 3.5, MySQL 8, H2 test fixture, JUnit 5, Mockito, Maven, Node.js ESM, Vitest 4

## Global Constraints

- 只在 `codex/agent-rag` 分支工作；保留所有无关修改和未跟踪文件。
- 完成且验证的任务按任务提交到 `codex/agent-rag`；用户未要求时不推送，不创建 PR，不修改 `main`。
- 本计划只交付 Phase 1 后端基础；不接入真实 Shippo、EasyPost、UPS、FedEx、USPS、Canada Post、Purolator、LTL 或白手套 API。
- 只允许 `US -> US` 与 `CA -> CA`；拒绝 `US -> CA`、`CA -> US` 和中国到北美的国际干线。
- Shipment 状态不能修改支付状态；履约异常不能回滚 ERP/WMS 已完成事实。
- 所有业务时间以 UTC `LocalDateTime` 持久化，时区单独保存 IANA 标识。
- Redis key、日志、异常和审计摘要不得包含明文姓名、电话、完整地址、签名或第三方密钥。
- 迁移必须新增为 `V015__trade_fulfillment_core.sql`；不得改写 `V001` 到 `V014`。
- 新能力默认关闭；关闭时旧订单、旧发货和旧轨迹接口行为保持不变。
- 单元和集成测试禁止访问外部网络。
- Phase 0 的真实账号/运单 PoC 可以与本计划并行，但任何真实 provider adapter 计划必须以已签字的 PoC 评分表为输入。

## Plan Decomposition

本文件产生可独立测试和灰度的后端基础。后续分别编写并执行以下计划，避免把互不依赖的子系统塞进一次变更：

1. `provider-poc-and-selection`：真实账号、真实运单、合同、隐私、成本和承运商覆盖验证。
2. `parcel-provider-integration`：PoC 胜出的美国/加拿大包裹 provider、webhook 和轮询补偿。
3. `fulfillment-admin-console`：运营端拆单、时间线、异常和人工修正页面。
4. `fulfillment-customer-experience`：商城端多包裹、英/法文时间线和通知。
5. `ltl-white-glove`：PRO/BOL、多运输段、预约、改约和 POD。

---

### Task 1: Add the additive fulfillment schema migration

**Files:**
- Create: `yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V015__trade_fulfillment_core.sql`
- Create: `furniture web/tests/databaseFulfillmentMigration.test.js`
- Modify: `furniture web/tests/dbMigrations.test.js`
- Modify: `furniture web/tests/databaseSafetyWorkflow.test.js`
- Modify: `yudao电商管理平台前后端/yudao-cloud/sql/mysql/oakved-baseline.sql` (generated)

**Interfaces:**
- Consumes: immutable migration catalog `V001` through `V014`.
- Produces: ten tenant-aware fulfillment tables and a regenerated deterministic baseline.
- Does not create foreign keys; referential checks stay in services, matching the current schema style.

- [ ] **Step 1: Write the failing migration contract test**

Create `databaseFulfillmentMigration.test.js` with exact table, column, unique-key, country check, and immutability assertions:

```js
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

const root = join(import.meta.dirname, "../../yudao电商管理平台前后端/yudao-cloud/sql/mysql");
const migrationPath = join(root, "migrations/V015__trade_fulfillment_core.sql");

describe("V015 trade fulfillment core migration", () => {
  it("creates the complete Phase 1 persistence contract", () => {
    const sql = readFileSync(migrationPath, "utf8");
    for (const table of [
      "trade_carrier",
      "trade_logistics_provider",
      "trade_shipment",
      "trade_shipment_item",
      "trade_shipment_package",
      "trade_shipment_leg",
      "trade_tracking_event",
      "trade_order_fulfillment_summary",
      "trade_fulfillment_idempotency",
      "trade_fulfillment_outbox_event",
    ]) {
      expect(sql).toContain(`CREATE TABLE IF NOT EXISTS \`${table}\``);
    }
    expect(sql).toContain("CHECK (`origin_country` IN ('US','CA'))");
    expect(sql).toContain("CHECK (`destination_country` = `origin_country`)");
    expect(sql).toContain("UNIQUE KEY `uk_tracking_event_external`");
    expect(sql).toContain("UNIQUE KEY `uk_tracking_event_hash`");
    expect(sql).toContain("UNIQUE KEY `uk_fulfillment_idempotency`");
    expect(sql).not.toMatch(/api[_-]?key|secret\s+varchar|receiver_mobile/i);
  });

  it("keeps published migrations immutable and appends version 015", () => {
    const build = readFileSync(join(root, "build-oakved-baseline.mjs"), "utf8");
    expect(build).toContain("discoverMigrations");
    expect(migrationPath).toMatch(/V015__trade_fulfillment_core\.sql$/);
  });
});
```

- [ ] **Step 2: Run the test and verify RED**

Run from `D:\code\furniture web`:

```powershell
npm.cmd test -- databaseFulfillmentMigration.test.js
```

Expected: FAIL because `V015__trade_fulfillment_core.sql` does not exist.

- [ ] **Step 3: Implement the migration**

The migration must use `BIGINT AUTO_INCREMENT`, `utf8mb4`, standard audit columns, logical deletion, tenant-leading indexes, and these exact business columns:

```sql
CREATE TABLE IF NOT EXISTS `trade_carrier` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `code` varchar(32) NOT NULL,
  `name` varchar(128) NOT NULL,
  `country_codes` varchar(32) NOT NULL,
  `legacy_express_id` bigint DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 0,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_carrier_code` (`tenant_id`,`code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `trade_logistics_provider` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `code` varchar(32) NOT NULL,
  `name` varchar(128) NOT NULL,
  `capabilities` varchar(512) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider_code` (`tenant_id`,`code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `trade_shipment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  `shipment_no` varchar(32) NOT NULL,
  `shipment_type` varchar(20) NOT NULL,
  `status` varchar(32) NOT NULL,
  `origin_country` char(2) NOT NULL,
  `destination_country` char(2) NOT NULL,
  `origin_timezone` varchar(64) NOT NULL,
  `destination_timezone` varchar(64) NOT NULL,
  `warehouse_id` bigint NOT NULL,
  `provider_id` bigint DEFAULT NULL,
  `estimated_delivery_at` datetime DEFAULT NULL,
  `delivered_at` datetime DEFAULT NULL,
  `last_event_occurred_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shipment_no` (`tenant_id`,`shipment_no`,`deleted`),
  KEY `idx_shipment_order` (`tenant_id`,`order_id`,`deleted`),
  KEY `idx_shipment_status` (`tenant_id`,`status`,`update_time`),
  CONSTRAINT `chk_shipment_origin_country` CHECK (`origin_country` IN ('US','CA')),
  CONSTRAINT `chk_shipment_destination_country` CHECK (`destination_country` IN ('US','CA')),
  CONSTRAINT `chk_shipment_domestic` CHECK (`destination_country` = `origin_country`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `trade_shipment_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `shipment_id` bigint NOT NULL,
  `order_item_id` bigint NOT NULL,
  `sku_id` bigint NOT NULL,
  `quantity` decimal(24,6) NOT NULL,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shipment_item` (`tenant_id`,`shipment_id`,`order_item_id`,`deleted`),
  KEY `idx_shipment_item_order_item` (`tenant_id`,`order_item_id`,`deleted`),
  CONSTRAINT `chk_shipment_item_quantity` CHECK (`quantity` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `trade_shipment_package` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `shipment_id` bigint NOT NULL,
  `package_no` varchar(32) NOT NULL,
  `package_type` varchar(20) NOT NULL,
  `carrier_id` bigint DEFAULT NULL,
  `tracking_number` varchar(64) DEFAULT NULL,
  `weight` decimal(18,6) DEFAULT NULL,
  `weight_unit` varchar(4) DEFAULT NULL,
  `length` decimal(18,6) DEFAULT NULL,
  `width` decimal(18,6) DEFAULT NULL,
  `height` decimal(18,6) DEFAULT NULL,
  `dimension_unit` varchar(4) DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shipment_package_no` (`tenant_id`,`shipment_id`,`package_no`,`deleted`),
  UNIQUE KEY `uk_package_tracking` (`tenant_id`,`carrier_id`,`tracking_number`,`deleted`),
  CONSTRAINT `chk_package_weight` CHECK (`weight` IS NULL OR `weight` >= 0),
  CONSTRAINT `chk_package_dimensions` CHECK ((`length` IS NULL OR `length` >= 0) AND (`width` IS NULL OR `width` >= 0) AND (`height` IS NULL OR `height` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `trade_shipment_leg` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `shipment_id` bigint NOT NULL,
  `package_id` bigint DEFAULT NULL,
  `sequence_no` int NOT NULL,
  `leg_type` varchar(20) NOT NULL,
  `carrier_id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  `service_level` varchar(64) DEFAULT NULL,
  `tracking_number` varchar(64) DEFAULT NULL,
  `pro_number` varchar(64) DEFAULT NULL,
  `bol_number` varchar(64) DEFAULT NULL,
  `origin_location` varchar(256) DEFAULT NULL,
  `destination_location` varchar(256) DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `started_at` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shipment_leg_sequence` (`tenant_id`,`shipment_id`,`sequence_no`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `trade_tracking_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `shipment_id` bigint NOT NULL,
  `package_id` bigint DEFAULT NULL,
  `shipment_leg_id` bigint DEFAULT NULL,
  `provider_id` bigint NOT NULL,
  `external_event_id` varchar(128) DEFAULT NULL,
  `event_hash` char(64) DEFAULT NULL,
  `standard_status` varchar(32) NOT NULL,
  `provider_status` varchar(128) NOT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `location` varchar(256) DEFAULT NULL,
  `occurred_at` datetime NOT NULL,
  `occurred_timezone` varchar(64) DEFAULT NULL,
  `received_at` datetime NOT NULL,
  `raw_payload_ref` varchar(256) DEFAULT NULL,
  `source` varchar(20) NOT NULL,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tracking_event_external` (`tenant_id`,`provider_id`,`external_event_id`,`deleted`),
  UNIQUE KEY `uk_tracking_event_hash` (`tenant_id`,`provider_id`,`event_hash`,`deleted`),
  KEY `idx_tracking_event_timeline` (`tenant_id`,`shipment_id`,`occurred_at`,`id`),
  CONSTRAINT `chk_tracking_event_identity` CHECK (`external_event_id` IS NOT NULL OR `event_hash` IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `trade_order_fulfillment_summary` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  `status` varchar(32) NOT NULL,
  `shipment_count` int NOT NULL DEFAULT 0,
  `delivered_shipment_count` int NOT NULL DEFAULT 0,
  `version` int NOT NULL DEFAULT 0,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_fulfillment_summary` (`tenant_id`,`order_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `trade_fulfillment_idempotency` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `operation` varchar(64) NOT NULL,
  `idempotency_key_hash` char(64) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `resource_type` varchar(32) NOT NULL,
  `resource_id` bigint DEFAULT NULL,
  `status` varchar(16) NOT NULL,
  `expires_at` datetime NOT NULL,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fulfillment_idempotency` (`tenant_id`,`operation`,`idempotency_key_hash`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `trade_fulfillment_outbox_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `event_id` char(36) NOT NULL,
  `aggregate_type` varchar(32) NOT NULL,
  `aggregate_id` bigint NOT NULL,
  `event_type` varchar(64) NOT NULL,
  `payload` json NOT NULL,
  `status` varchar(16) NOT NULL,
  `attempt_count` int NOT NULL DEFAULT 0,
  `next_attempt_at` datetime NOT NULL,
  `published_at` datetime DEFAULT NULL,
  `last_error_code` varchar(64) DEFAULT NULL,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fulfillment_outbox_event_id` (`tenant_id`,`event_id`,`deleted`),
  KEY `idx_fulfillment_outbox_due` (`tenant_id`,`status`,`next_attempt_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

- [ ] **Step 4: Regenerate the baseline and run migration tests**

Run from `D:\code\furniture web`:

```powershell
npm.cmd run build:db-baseline
npm.cmd test -- databaseFulfillmentMigration.test.js databaseSafetyWorkflow.test.js dbMigrations.test.js
```

Expected: generator reports versions `001` through `015`; all focused tests PASS. In an isolated worktree, `ruoyi-vue-pro.sql` and `quartz.sql` are ignored local generator inputs and must be copied temporarily from the primary workspace before generation; never stage or commit those temporary copies. Update the two existing migration tests from the V014/14 catalog to the V015/15 catalog, preferring migration-directory discovery over duplicated hard-coded file lists.

- [ ] **Step 5: Commit**

```powershell
git add -- 'furniture web/tests/databaseFulfillmentMigration.test.js' 'furniture web/tests/dbMigrations.test.js' 'furniture web/tests/databaseSafetyWorkflow.test.js' 'yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V015__trade_fulfillment_core.sql' 'yudao电商管理平台前后端/yudao-cloud/sql/mysql/oakved-baseline.sql'
git commit -m "feat: add north america fulfillment schema"
```

### Task 2: Implement standard statuses and the non-regressing state machine

**Files:**
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-api/src/main/java/cn/iocoder/yudao/module/trade/enums/fulfillment/ShipmentStatusEnum.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-api/src/main/java/cn/iocoder/yudao/module/trade/enums/fulfillment/ShipmentTypeEnum.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-api/src/main/java/cn/iocoder/yudao/module/trade/enums/fulfillment/OrderFulfillmentStatusEnum.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-api/src/main/java/cn/iocoder/yudao/module/trade/enums/fulfillment/TrackingEventSourceEnum.java`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-api/src/main/java/cn/iocoder/yudao/module/trade/enums/ErrorCodeConstants.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment/domain/ShipmentStateMachine.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/fulfillment/domain/ShipmentStateMachineTest.java`

**Interfaces:**
- `TransitionDecision decide(current, incoming, currentOccurredAt, incomingOccurredAt)` returns `APPLY`, `TIMELINE_ONLY`, or `REJECT`.
- Terminal states are `DELIVERED`, `RETURNED`, and `CANCELED`.

- [ ] **Step 1: Write failing state-machine tests**

Cover every allowed edge from the approved design, plus these invariants:

```java
@Test
void deliveredNeverRegressesToInTransit() {
    assertEquals(TIMELINE_ONLY, stateMachine.decide(
            DELIVERED, IN_TRANSIT, time("2026-07-15T10:00:00"), time("2026-07-15T11:00:00")));
}

@Test
void lateEventIsStoredWithoutChangingCurrentStatus() {
    assertEquals(TIMELINE_ONLY, stateMachine.decide(
            OUT_FOR_DELIVERY, AT_LOCAL_TERMINAL,
            time("2026-07-15T10:00:00"), time("2026-07-15T09:00:00")));
}

@Test
void exceptionCanRecoverToOutForDelivery() {
    assertEquals(APPLY, stateMachine.decide(
            DELIVERY_EXCEPTION, OUT_FOR_DELIVERY,
            time("2026-07-15T10:00:00"), time("2026-07-15T11:00:00")));
}
```

- [ ] **Step 2: Run and verify RED**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=ShipmentStateMachineTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: compilation FAIL because the fulfillment enums and state machine do not exist.

- [ ] **Step 3: Implement enums and the complete transition map**

`ShipmentStatusEnum` must contain exactly:

```java
public enum ShipmentStatusEnum {
    DRAFT, READY_TO_SHIP, HANDED_TO_CARRIER, IN_TRANSIT,
    AT_LOCAL_TERMINAL, APPOINTMENT_REQUIRED, APPOINTMENT_CONFIRMED,
    OUT_FOR_DELIVERY, DELIVERED, DELIVERY_EXCEPTION,
    RETURNING, RETURNED, CANCELED
}
```

The other Phase 1 enums are simple enums with no fields or custom constructors and must contain exactly:

```text
ShipmentTypeEnum: PARCEL, LTL, WHITE_GLOVE
OrderFulfillmentStatusEnum: NOT_SHIPPED, PARTIALLY_SHIPPED, SHIPPED, PARTIALLY_DELIVERED, DELIVERED, DELIVERY_EXCEPTION, RETURNING, RETURNED
TrackingEventSourceEnum: WEBHOOK, POLLING, MANUAL, MIGRATION
```

Do not add `UNKNOWN`, numeric codes, labels, or provider-specific values.

Implement `ShipmentStateMachine` as an immutable `EnumMap<ShipmentStatusEnum, Set<ShipmentStatusEnum>>`. The allowed transitions are:

```java
DRAFT -> READY_TO_SHIP, CANCELED
READY_TO_SHIP -> HANDED_TO_CARRIER, CANCELED
HANDED_TO_CARRIER -> IN_TRANSIT, DELIVERY_EXCEPTION, CANCELED
IN_TRANSIT -> AT_LOCAL_TERMINAL, APPOINTMENT_REQUIRED, OUT_FOR_DELIVERY, DELIVERY_EXCEPTION, RETURNING
AT_LOCAL_TERMINAL -> APPOINTMENT_REQUIRED, APPOINTMENT_CONFIRMED, OUT_FOR_DELIVERY, DELIVERY_EXCEPTION, RETURNING
APPOINTMENT_REQUIRED -> APPOINTMENT_CONFIRMED, DELIVERY_EXCEPTION, RETURNING
APPOINTMENT_CONFIRMED -> OUT_FOR_DELIVERY, APPOINTMENT_REQUIRED, DELIVERY_EXCEPTION, RETURNING
OUT_FOR_DELIVERY -> DELIVERED, APPOINTMENT_REQUIRED, DELIVERY_EXCEPTION, RETURNING
DELIVERY_EXCEPTION -> IN_TRANSIT, AT_LOCAL_TERMINAL, APPOINTMENT_REQUIRED, APPOINTMENT_CONFIRMED, OUT_FOR_DELIVERY, DELIVERED, RETURNING
RETURNING -> RETURNED, DELIVERY_EXCEPTION
```

`currentOccurredAt` and `incomingOccurredAt` use `java.time.LocalDateTime` with UTC value semantics. `incomingOccurredAt` is required; `currentOccurredAt` may be null when no event has been applied, in which case the late-event comparison is skipped. Decision order must be: same status = `TIMELINE_ONLY`; terminal current state = `TIMELINE_ONLY`; older `occurredAt` = `TIMELINE_ONLY`; allowed edge = `APPLY`; otherwise = `REJECT`.

Reserve `1_011_009_000` through `1_011_009_011` in `ErrorCodeConstants` for the complete Phase 1 error set so later parallel tasks compile against one stable contract:

```text
FULFILLMENT_SHIPMENT_NOT_FOUND
FULFILLMENT_ORDER_NOT_FOUND
FULFILLMENT_ORDER_ITEM_QUANTITY_EXCEEDED
FULFILLMENT_COUNTRY_NOT_SUPPORTED
FULFILLMENT_CROSS_BORDER_NOT_SUPPORTED
FULFILLMENT_INVALID_STATUS_TRANSITION
FULFILLMENT_DUPLICATE_TRACKING_NUMBER
FULFILLMENT_PROVIDER_NOT_AVAILABLE
FULFILLMENT_PROVIDER_CAPABILITY_UNSUPPORTED
FULFILLMENT_VERSION_CONFLICT
FULFILLMENT_IDEMPOTENCY_CONFLICT
FULFILLMENT_DISPATCH_INCOMPLETE
```

Messages must be customer-safe and must not echo request bodies, tracking numbers, addresses, provider payloads, or secrets.

- [ ] **Step 4: Run tests and verify GREEN**

Run the command from Step 2. Expected: all `ShipmentStateMachineTest` cases PASS.

- [ ] **Step 5: Commit**

```powershell
git add -- 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-api/src/main/java/cn/iocoder/yudao/module/trade/enums/fulfillment' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-api/src/main/java/cn/iocoder/yudao/module/trade/enums/ErrorCodeConstants.java' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment/domain' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/fulfillment/domain'
git commit -m "feat: add fulfillment state machine"
```

### Task 3: Add persistence objects, tenant queries, and optimistic updates

**Files:**
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/dal/dataobject/fulfillment/CarrierDO.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/dal/dataobject/fulfillment/LogisticsProviderDO.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/dal/dataobject/fulfillment/ShipmentDO.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/dal/dataobject/fulfillment/ShipmentItemDO.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/dal/dataobject/fulfillment/ShipmentPackageDO.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/dal/dataobject/fulfillment/ShipmentLegDO.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/dal/dataobject/fulfillment/TrackingEventDO.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/dal/dataobject/fulfillment/OrderFulfillmentSummaryDO.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/dal/dataobject/fulfillment/FulfillmentIdempotencyDO.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/dal/dataobject/fulfillment/FulfillmentOutboxEventDO.java`
- Create: mapper interfaces matching all ten DOs under `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/dal/mysql/fulfillment/`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/resources/sql/create_tables.sql`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/resources/sql/clean.sql`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/dal/mysql/fulfillment/FulfillmentPersistenceTest.java`

**Interfaces:**
- Every DO extends `BaseDO`; table names and Java property types mirror V015.
- Quantity/dimensions use `BigDecimal`; timestamps use `LocalDateTime`; JSON outbox payload uses `JacksonTypeHandler`.
- Query methods always include tenant and logical-deletion predicates even though the framework tenant interceptor is active.

- [ ] **Step 1: Add H2 fixtures and failing mapper tests**

Add H2-compatible forms of all V015 tables to `create_tables.sql`, and delete child-to-parent in this order in `clean.sql`: outbox, idempotency, event, leg, package, item, summary, shipment, provider, carrier.

The test must prove:

```java
assertEquals(1, shipmentMapper.selectListByOrderId(121L, 100L).size());
assertTrue(shipmentMapper.selectListByOrderId(122L, 100L).isEmpty());
assertEquals(1, shipmentMapper.updateStatusByIdAndVersion(
        121L, shipmentId, 0, IN_TRANSIT.name(), eventTime));
assertEquals(0, shipmentMapper.updateStatusByIdAndVersion(
        121L, shipmentId, 0, DELIVERED.name(), eventTime.plusHours(1)));
```

Also prove duplicate tracking numbers, duplicate external event IDs, and duplicate idempotency hashes are rejected by database constraints.

- [ ] **Step 2: Run and verify RED**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=FulfillmentPersistenceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: compilation FAIL because DOs and mappers do not exist.

- [ ] **Step 3: Implement DOs and mapper contracts**

Use the existing `TradeOrderDO` Lombok and `@TableName` style. `ShipmentMapper` must implement the following exact methods:

```java
default List<ShipmentDO> selectListByOrderId(Long tenantId, Long orderId) {
    return selectList(new LambdaQueryWrapperX<ShipmentDO>()
            .eq(ShipmentDO::getTenantId, tenantId)
            .eq(ShipmentDO::getOrderId, orderId)
            .orderByAsc(ShipmentDO::getId));
}

default int updateStatusByIdAndVersion(Long tenantId, Long id, Integer version,
                                       String status, LocalDateTime occurredAt) {
    return update(null, new LambdaUpdateWrapper<ShipmentDO>()
            .eq(ShipmentDO::getTenantId, tenantId)
            .eq(ShipmentDO::getId, id)
            .eq(ShipmentDO::getVersion, version)
            .set(ShipmentDO::getStatus, status)
            .set(ShipmentDO::getLastEventOccurredAt, occurredAt)
            .set(ShipmentDO::getVersion, version + 1));
}
```

`TrackingEventMapper` must expose tenant-scoped timeline reads ordered by `occurred_at ASC, id ASC`. `ShipmentItemMapper` must expose `sumQuantityByOrderItemId(tenantId, orderItemId)` so the service can prevent over-shipment. `OrderFulfillmentSummaryMapper` must expose a version-checked update.

- [ ] **Step 4: Run persistence tests and the existing trade transaction test**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=FulfillmentPersistenceTest,TradeOrderUpdateServiceImplTransactionTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS and no H2 cleanup-order violation.

- [ ] **Step 5: Commit**

```powershell
git add -- 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/dal' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/resources/sql' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/dal/mysql/fulfillment'
git commit -m "feat: add fulfillment persistence layer"
```

### Task 4: Define the provider port and an offline Mock adapter

**Files:**
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/framework/fulfillment/core/LogisticsProviderClient.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/framework/fulfillment/core/LogisticsProviderRegistry.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/framework/fulfillment/core/ProviderCapability.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/framework/fulfillment/core/dto/ProviderTrackingEvent.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/framework/fulfillment/core/dto/TrackingRegistrationCommand.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/framework/fulfillment/core/dto/TrackingRegistrationResult.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/framework/fulfillment/core/dto/TrackingQuery.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/framework/fulfillment/core/dto/TrackingSnapshot.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/framework/fulfillment/core/impl/MockLogisticsProviderClient.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/framework/fulfillment/core/LogisticsProviderRegistryTest.java`

**Interfaces:**

```java
public interface LogisticsProviderClient {
    String getProviderCode();
    Set<ProviderCapability> getCapabilities();
    TrackingRegistrationResult registerTracking(TrackingRegistrationCommand command);
    TrackingSnapshot queryTracking(TrackingQuery query);
}
```

Appointment, POD, webhook verification and parsing stay out of this Phase 1 interface; they are added by the provider/LTL plans only after real contracts are known.

- [ ] **Step 1: Write failing registry and Mock tests**

Assert that codes are normalized with `Locale.ROOT`, duplicate provider codes fail startup, unknown codes return controlled `FULFILLMENT_PROVIDER_NOT_AVAILABLE`, Mock declares `TRACKING_QUERY` only, and its query returns deterministic events without HTTP clients.

- [ ] **Step 2: Run and verify RED**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=LogisticsProviderRegistryTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

- [ ] **Step 3: Implement the registry and Mock**

`MockLogisticsProviderClient` must use provider code `mock`, accept carrier/tracking identifiers, and return the events supplied to its in-memory fixture API in tests. Production code must not expose that fixture API as a controller. Registry construction must be:

```java
public LogisticsProviderRegistry(List<LogisticsProviderClient> clients) {
    this.clients = clients.stream().collect(Collectors.toUnmodifiableMap(
            client -> client.getProviderCode().toLowerCase(Locale.ROOT),
            Function.identity(),
            (left, right) -> { throw new IllegalStateException("Duplicate logistics provider: " + left.getProviderCode()); }
    ));
}
```

- [ ] **Step 4: Run tests and verify GREEN**

Run Step 2 again. Expected: PASS and no network activity.

- [ ] **Step 5: Commit**

```powershell
git add -- 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/framework/fulfillment' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/framework/fulfillment'
git commit -m "feat: add fulfillment provider port"
```

### Task 5: Create draft shipments with country, quantity, and idempotency guards

**Files:**
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment/FulfillmentCommandService.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment/FulfillmentCommandServiceImpl.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment/command/CreateShipmentCommand.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment/command/CreateShipmentItemCommand.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment/support/FulfillmentHashing.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment/support/FulfillmentNoGenerator.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/framework/fulfillment/config/FulfillmentProperties.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/fulfillment/FulfillmentCommandServiceImplTest.java`

**Interfaces:**

```java
Long createShipment(String idempotencyKey, CreateShipmentCommand command);
```

The command contains tenantId, orderId, shipmentType, originCountry, destinationCountry, originTimezone, destinationTimezone, warehouseId, providerId, and a non-empty item list of orderItemId, skuId, and quantity.

- [ ] **Step 1: Write failing command-service tests**

Cover successful US and CA drafts, cross-border rejection, unsupported country rejection, order not found, SKU mismatch, zero quantity, aggregate quantity exceeding the order line, repeated identical idempotency request returning the same shipment ID, and repeated key with a different request hash returning a conflict.

- [ ] **Step 2: Run and verify RED**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=FulfillmentCommandServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

- [ ] **Step 3: Implement transactional draft creation**

The transaction order must be:

1. HMAC-SHA-256 the idempotency key using `yudao.trade.fulfillment.idempotency-hmac-key`; never persist the raw key.
2. Canonically serialize the command and SHA-256 it as `request_hash`.
3. Insert `PROCESSING` idempotency row; on duplicate, return completed resource only if request hashes match.
4. Load order and order items under tenant scope.
5. Validate `US/CA`, domestic equality, order-line ownership, SKU equality and total shipped quantity.
6. Insert `DRAFT` shipment and shipment items.
7. Upsert order summary as `NOT_SHIPPED`.
8. Insert `SHIPMENT_CREATED` outbox event.
9. Mark idempotency row `COMPLETED` with shipment ID and 24-hour expiry.

`FulfillmentNoGenerator` returns `SHP-` plus UTC `yyyyMMdd` plus 16 uppercase hex characters; database uniqueness remains the final collision guard.

Create the `@ConfigurationProperties(prefix = "yudao.trade.fulfillment")` property skeleton in this task with boolean fields `enabled`, `writeNewModel`, `readFromNewModel`, `customerUiEnabled`, and `legacyMigrationWriteEnabled` all defaulting to false, plus blank `providerCode` and `idempotencyHmacKey`. Task 10 adds cross-field startup validation and environment YAML; Task 5 must inject this typed property rather than introducing a separate `@Value` string.

- [ ] **Step 4: Run service and transaction tests**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=FulfillmentCommandServiceImplTest,FulfillmentPersistenceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS; verify outbox and shipment rows roll back together when any insert fails.

- [ ] **Step 5: Commit**

```powershell
git add -- 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/framework/fulfillment/config/FulfillmentProperties.java' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/fulfillment'
git commit -m "feat: create idempotent fulfillment shipments"
```

### Task 6: Add packages, legs, readiness, and dispatch compatibility projection

**Files:**
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment/command/UpsertPackageCommand.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment/command/AddShipmentLegCommand.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment/command/DispatchShipmentCommand.java`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment/FulfillmentCommandService.java`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment/FulfillmentCommandServiceImpl.java`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/dal/mysql/order/TradeOrderMapper.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/fulfillment/FulfillmentDispatchServiceTest.java`

**Interfaces:**

```java
Long addPackage(String idempotencyKey, UpsertPackageCommand command);
Long addLeg(String idempotencyKey, AddShipmentLegCommand command);
void markReady(String idempotencyKey, Long tenantId, Long shipmentId, Integer expectedVersion);
void dispatch(String idempotencyKey, DispatchShipmentCommand command);
```

- [ ] **Step 1: Write failing dispatch tests**

Cover: draft package creation; parcel requiring carrier and tracking number; LTL accepting PRO/BOL on its leg; duplicate active tracking rejection; missing leg/provider/carrier rejection; stale version rejection; successful `DRAFT -> READY_TO_SHIP -> HANDED_TO_CARRIER`; and first-package compatibility projection.

The projection assertion must verify `trade_order.logistics_id` receives `trade_carrier.legacy_express_id`, never the new carrier ID, and `logistics_no` receives the first active package tracking number.

- [ ] **Step 2: Run and verify RED**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=FulfillmentDispatchServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

- [ ] **Step 3: Implement package/leg validation and dispatch**

Dispatch must execute in one transaction:

1. Lock or version-check the shipment.
2. Verify current state `READY_TO_SHIP`.
3. Verify at least one item, one package and one leg.
4. Verify every package has a carrier; PARCEL also requires tracking number.
5. Verify every leg has a provider and carrier; LTL requires `proNumber` or `bolNumber`.
6. Transition with `ShipmentStateMachine`.
7. Set package and active leg to `HANDED_TO_CARRIER`.
8. Update summary to `PARTIALLY_SHIPPED` or `SHIPPED` from all valid shipments.
9. If this is the first active package and the carrier has `legacyExpressId`, update legacy fields with a version/status guard; do not mark partially shipped orders as fully delivered.
10. Insert `PACKAGE_DISPATCHED` outbox event.
11. After commit, call `registerTracking` only when the selected provider explicitly declares the registration capability; otherwise skip registration and rely on polling/query. Registration failure creates a retryable outbox/sync event and does not roll back dispatch. Phase 1 Mock declares `TRACKING_QUERY` only, so dispatch must not call Mock `registerTracking`.

- [ ] **Step 4: Run focused tests**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=FulfillmentDispatchServiceTest,FulfillmentCommandServiceImplTest,TradeOrderUpdateServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS; existing order delivery tests stay green.

- [ ] **Step 5: Commit**

```powershell
git add -- 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/dal/mysql/order/TradeOrderMapper.java' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/fulfillment/FulfillmentDispatchServiceTest.java'
git commit -m "feat: dispatch fulfillment shipments"
```

### Task 7A: Add versioned provider-status mapping and replay audit foundation

Implement and verify `docs/superpowers/plans/2026-07-15-north-america-tracking-mapping-foundation.md` before Task 7. This adds V016 rather than rewriting the already committed V015 migration. It must not seed real provider mappings.

### Task 7: Ingest deterministic tracking events and update summaries with outbox atomicity

**Files:**
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment/FulfillmentTrackingService.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment/FulfillmentTrackingServiceImpl.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment/command/ApplyTrackingEventCommand.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment/domain/OrderFulfillmentSummaryCalculator.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/fulfillment/FulfillmentTrackingServiceImplTest.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/fulfillment/FulfillmentTrackingTransactionTest.java`

**Interfaces:**

```java
TrackingApplyResult applyEvent(ApplyTrackingEventCommand command);
```

`TrackingApplyResult` reports `inserted`, `stateChanged`, previous status, and current status; duplicate events are successful no-ops.

- [ ] **Step 1: Write failing event tests**

Test external-ID dedupe, hash dedupe, canonical whitespace/case normalization, late-event timeline insertion without regression, terminal-state protection, exception recovery, stale optimistic lock retry, all-packages-delivered aggregation, partial delivery aggregation, and outbox rollback.

The stable hash input is exactly:

```text
carrierCode\ntrackingNumber\nproviderStatus\noccurredAtUtc\nnormalizedLocation\nnormalizedDescription
```

Normalize with Unicode NFKC, trim, collapse internal whitespace, and uppercase carrier/provider status using `Locale.ROOT`; do not uppercase customer descriptions.

- [ ] **Step 2: Run and verify RED**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=FulfillmentTrackingServiceImplTest,FulfillmentTrackingTransactionTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

- [ ] **Step 3: Implement event application**

Within one transaction:

1. Resolve tenant-scoped shipment/package/leg/provider.
2. Derive external ID or stable event hash.
3. Insert timeline row; translate duplicate-key violations into `inserted=false`.
4. Ask the state machine whether to apply, store-only, or reject.
5. For `APPLY`, update package/leg and shipment with version guards; retry the aggregate calculation at most three times.
6. Recalculate order summary from all non-canceled shipments.
7. Insert `TRACKING_UPDATED` and, when applicable, `DELIVERY_EXCEPTION`, `OUT_FOR_DELIVERY`, `DELIVERED`, `RETURN_STARTED`, or `RETURNED` outbox events.
8. Never update payment fields or call ERP/WMS synchronously.

Summary rules are exact: zero dispatched = `NOT_SHIPPED`; some but not all dispatched = `PARTIALLY_SHIPPED`; all dispatched and none delivered = `SHIPPED`; some delivered = `PARTIALLY_DELIVERED`; all delivered = `DELIVERED`; any active exception = `DELIVERY_EXCEPTION`; any returning = `RETURNING`; all returned = `RETURNED`.

- [ ] **Step 4: Run tests and verify GREEN**

Run Step 2 again. Expected: PASS, including transaction rollback assertions.

- [ ] **Step 5: Commit**

```powershell
git add -- 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/fulfillment'
git commit -m "feat: process fulfillment tracking events"
```

### Task 8: Expose guarded admin APIs and read models

**Files:**
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/admin/fulfillment/TradeFulfillmentController.java`
- Create: request/response VOs under `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/admin/fulfillment/vo/`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment/FulfillmentQueryService.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment/FulfillmentQueryServiceImpl.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/controller/admin/fulfillment/TradeFulfillmentControllerTest.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/fulfillment/FulfillmentQueryServiceImplTest.java`

**Interfaces:**

```text
POST /admin-api/trade/fulfillment/shipments
PUT  /admin-api/trade/fulfillment/shipments/{id}/ready
POST /admin-api/trade/fulfillment/shipments/{id}/packages
POST /admin-api/trade/fulfillment/shipments/{id}/legs
POST /admin-api/trade/fulfillment/shipments/{id}/dispatch
POST /admin-api/trade/fulfillment/shipments/{id}/manual-event
GET  /admin-api/trade/fulfillment/shipments/{id}
GET  /admin-api/trade/fulfillment/shipments/{id}/timeline
GET  /admin-api/trade/fulfillment/shipments/page
```

- [ ] **Step 1: Write failing controller and query tests**

Assert `Idempotency-Key` is mandatory for every POST and PUT, expected version is mandatory for state-changing requests, tenant context is passed into services rather than accepted from the body, page reads are tenant-scoped, manual events require a reason of 5–500 characters, and responses mask tracking numbers to the last four characters.

- [ ] **Step 2: Run and verify RED**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=TradeFulfillmentControllerTest,FulfillmentQueryServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

- [ ] **Step 3: Implement APIs, permissions, and read models**

Use existing `CommonResult`, `PageResult`, validation annotations and permission style. Apply:

```text
trade:fulfillment:shipment:create
trade:fulfillment:shipment:update
trade:fulfillment:shipment:dispatch
trade:fulfillment:shipment:query
trade:fulfillment:tracking:manual
```

Controllers obtain tenant/user context from framework helpers. Never accept `tenantId`, creator, updater, raw payload reference, outbox status, or provider credentials from HTTP request bodies. Manual-event audit content stores operator ID, reason, previous status, requested status and request trace ID, but not address or phone.

- [ ] **Step 4: Run API tests**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=TradeFulfillmentControllerTest,FulfillmentQueryServiceImplTest,FulfillmentCommandServiceImplTest,FulfillmentTrackingServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS and no authorization-free write endpoint.

- [ ] **Step 5: Commit**

```powershell
git add -- 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/admin/fulfillment' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment/FulfillmentQueryService.java' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment/FulfillmentQueryServiceImpl.java' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/controller/admin/fulfillment' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/fulfillment/FulfillmentQueryServiceImplTest.java'
git commit -m "feat: expose fulfillment admin APIs"
```

### Task 9: Preserve the legacy tracking API and add idempotent backfill

**Files:**
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/order/TradeOrderQueryServiceImpl.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment/FulfillmentLegacyProjectionService.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment/FulfillmentLegacyMigrationService.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/framework/fulfillment/cache/ExpressTrackCacheKeyGenerator.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/job/fulfillment/FulfillmentLegacyMigrationJob.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/fulfillment/FulfillmentLegacyProjectionServiceTest.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/fulfillment/FulfillmentLegacyMigrationServiceTest.java`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/controller/app/order/AppTradeOrderControllerTest.java`

**Interfaces:**
- Existing `GET /admin-api/trade/order/get-express-track-list?id=...` and `GET /app-api/trade/order/get-express-track-list?id=...` remain unchanged.
- Backfill method: `MigrationBatchResult migrateActiveOrders(Long tenantId, Long afterOrderId, int limit, boolean dryRun)`.

- [ ] **Step 1: Write failing compatibility tests**

Assert: new-model events are returned as `ExpressTrackRespDTO(time, content)` when `read-from-new-model=true`; absence of new data falls back to the current provider query; app reads still require `orderId + loginUserId`; disabled flag preserves current behavior; the legacy provider cache key is an HMAC digest and contains neither tracking number nor phone; repeated backfill creates no duplicates; completed/canceled orders are skipped; invalid carrier or blank tracking number is reported but not fabricated.

- [ ] **Step 2: Run and verify RED**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=FulfillmentLegacyProjectionServiceTest,FulfillmentLegacyMigrationServiceTest,AppTradeOrderControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

- [ ] **Step 3: Implement safe projection and backfill**

`TradeOrderQueryServiceImpl` must call the projection only when the read flag is enabled. The new projection returns an empty optional when no new shipment exists, enabling the exact old code path as fallback. Replace the existing `#code + '-' + #logisticsNo + '-' + #receiverMobile` expression with `keyGenerator = "expressTrackCacheKeyGenerator"`. The generator calculates HMAC-SHA-256 over `tenantId|code|trackingNumber|phone` with the configured fulfillment HMAC key and returns only `express-track:<64 lowercase hex>`; it never logs its arguments. New-model reads use database events and internal IDs only.

Backfill uses idempotency operation `LEGACY_ORDER_MIGRATION` and hash of `tenantId:orderId`. For each active order with nonblank legacy logistics fields it creates one PARCEL Shipment, one item allocation set, one Package, one LAST_MILE Leg and one `source=MIGRATION` event. It never contacts a provider and never changes order payment or trade status. The job defaults to `dryRun=true`, batch size 100, and requires an explicit property to write.

- [ ] **Step 4: Run compatibility tests**

Run Step 2 again. Expected: PASS; old app ownership checks remain intact.

- [ ] **Step 5: Commit**

```powershell
git add -- 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/order/TradeOrderQueryServiceImpl.java' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/framework/fulfillment/cache' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/job/fulfillment' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/fulfillment' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/controller/app/order/AppTradeOrderControllerTest.java'
git commit -m "feat: preserve legacy logistics compatibility"
```

### Task 10: Add feature flags, security defaults, and full verification

**Files:**
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/framework/fulfillment/config/FulfillmentProperties.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/framework/fulfillment/config/FulfillmentConfiguration.java`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-server/src/main/resources/application.yaml`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-server/src/main/resources/application-local.yaml`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/resources/application-unit-test.yaml`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/framework/fulfillment/config/FulfillmentPropertiesTest.java`
- Create: `docs/runbooks/north-america-fulfillment-phase1.md`

**Interfaces:**

```yaml
yudao:
  trade:
    fulfillment:
      enabled: false
      write-new-model: false
      read-from-new-model: false
      customer-ui-enabled: false
      legacy-migration-write-enabled: false
      provider-code: ${FULFILLMENT_PROVIDER_CODE:}
      idempotency-hmac-key: ${FULFILLMENT_IDEMPOTENCY_HMAC_KEY:}
```

- [ ] **Step 1: Write failing property-binding tests**

Assert every flag defaults false, production startup rejects blank HMAC key when writes are enabled, provider code defaults to `mock` only in local/test, and `customer-ui-enabled` cannot be true unless `enabled` and `read-from-new-model` are true.

- [ ] **Step 2: Run and verify RED**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=FulfillmentPropertiesTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

- [ ] **Step 3: Implement guarded configuration**

Use `@ConfigurationProperties(prefix = "yudao.trade.fulfillment")`. Keep all shared/default flags false and the shared provider code blank in `application.yaml`; a blank provider is valid only while writes are disabled. In `application-local.yaml`, enable only `enabled=true`, `write-new-model=true`, provider `mock`, and a clearly local-only HMAC value; keep read, migration writes and customer UI false. Unit-test YAML uses deterministic test-only values.

The runbook must document: flags and dependencies; migration dry run; carrier/provider seed examples without credentials; smoke commands; metrics to inspect; disable order; and rollback that turns off reads/writes without dropping V015 tables.

- [ ] **Step 4: Run complete verification**

From `D:\code\furniture web`:

```powershell
npm.cmd test -- databaseFulfillmentMigration.test.js databaseBaselineGenerator.test.js databaseSafetyWorkflow.test.js dbMigrations.test.js
```

From `D:\code\yudao电商管理平台前后端\yudao-cloud`:

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am test
mvn.cmd -pl yudao-server -am "-DskipTests" package
```

Expected: all tests PASS; backend package exits 0; no external provider request is made.

- [ ] **Step 5: Run repository hygiene checks**

```powershell
git diff --check
git status --short
git diff --name-only HEAD
```

Expected: no whitespace errors; only fulfillment work and the pre-existing unrelated user changes appear. Review the list and stage only files from this plan.

- [ ] **Step 6: Commit**

```powershell
git add -- 'docs/runbooks/north-america-fulfillment-phase1.md' 'yudao电商管理平台前后端/yudao-cloud/yudao-server/src/main/resources/application.yaml' 'yudao电商管理平台前后端/yudao-cloud/yudao-server/src/main/resources/application-local.yaml' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/framework/fulfillment/config' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/resources/application-unit-test.yaml' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/framework/fulfillment/config'
git commit -m "docs: add fulfillment rollout controls"
```

## Phase 1 Acceptance Gate

Before treating this plan as complete, record evidence for every item:

- [ ] V015 applies cleanly after V014 and the generated baseline is deterministic.
- [ ] Both US domestic and Canada domestic draft/dispatch scenarios pass.
- [ ] Both cross-border directions and non-US/CA countries are rejected.
- [ ] Split shipment quantities cannot exceed purchased order-line quantities.
- [ ] Duplicate idempotency requests and tracking events are successful no-ops; conflicting reuse is rejected.
- [ ] Late and duplicate tracking events remain visible but never regress current state.
- [ ] Shipment, summary and outbox changes commit or roll back atomically.
- [ ] Payment status is unchanged by every fulfillment test.
- [ ] App legacy tracking still enforces order ownership.
- [ ] New logs, cache keys and exceptions contain no address, phone, signature, raw provider payload or secret.
- [ ] Feature flags can return all reads and writes to the old path without schema rollback.
- [ ] The full trade module test suite and backend package command pass.

## Phase 0 Input Required Before the Next Provider Plan

The next `parcel-provider-integration` plan may start only when the repository contains an approved provider decision record with:

- provider and account type;
- real US and Canada carrier/tracking results;
- webhook signature and replay rules from official provider documentation;
- rate limits, retry guidance and `Retry-After` behavior;
- data processing regions, subprocessors, retention and deletion terms;
- per-shipment/query/webhook costs and minimum spend;
- supported/unsupported carrier restrictions for externally created labels;
- chosen primary path, fallback path and explicit exit conditions.

Until those facts exist, do not add provider-specific DTOs, signatures, webhook paths, polling schedules or credential fields.
