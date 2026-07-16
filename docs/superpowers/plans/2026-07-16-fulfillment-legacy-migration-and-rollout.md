# North America Fulfillment Legacy Migration and Rollout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变现有管理端/App 物流查询契约的前提下，安全接入新履约轨迹投影、消除旧物流缓存与 provider 日志中的敏感信息，并通过逐订单审批事实将可证明的美国/加拿大境内旧物流单幂等回填到新模型。

**Architecture:** Task 9 分成三个边界：9A 只负责兼容读取、HMAC 缓存和旧 provider 日志脱敏；9B 只负责候选分页和完全只读的 dry-run；9C 追加 V020 逐订单权威事实表，并以“一订单一 `REQUIRES_NEW` 事务”写入完整迁移聚合。Task 10 再把已有配置开关真正接到查询、写入、迁移和 mock provider 边界，并提供默认关闭、可回滚的 YAML 与运行手册。

**Tech Stack:** Java 17, Spring Boot, Spring Cache, MyBatis-Plus, MySQL 8, H2, Jakarta Validation, XXL-Job, Vitest/Node.js migration contracts, Maven.

## Global Constraints

- 只支持 `US -> US` 和 `CA -> CA` 境内履约；`US <-> CA`、中国到北美和任何第三国路线都不在本阶段范围内。
- 保留 `GET /admin-api/trade/order/get-express-track-list?id=...`、`GET /app-api/trade/order/get-express-track-list?id=...`、请求/响应结构和管理端权限 `trade:order:query`。
- App 必须先以 `orderId + loginUserId` 验证所有权，再查询任何新模型表；不存在、跨用户和跨租户均保持 `ORDER_NOT_FOUND`。
- V015-V019 已发布内容不可修改；本计划只能追加 V020，并同步生成基线及 H2 测试夹具。
- 缓存 HMAC secret 为空时必须绕过缓存并继续旧 provider 查询；不得使用 SHA-256、MD5、明文参数或进程随机 key 作为降级 key。KeyGenerator 被直接调用时仍须 fail closed。
- 任何新模型写入开启时，HMAC secret 和 provider code 都必须非空；迁移写入还必须同时满足 `enabled && writeNewModel && legacyMigrationWriteEnabled`。
- 轨迹兼容响应只允许 `occurredAt` 和 allowlist `standardStatus`；禁止暴露 description、location、provider status、raw payload、人工原因、trace、外部事件 ID、tracking、phone 或内部 provider 身份。
- 回填只使用逐订单 V020 审批事实；不得猜测国家、时区、仓库或 provider，不得采用租户级默认、第一条启用记录、carrier 能力或地址文本推断。
- dry-run 不分配有副作用的编号、不写任何表、不加写锁、不调用 provider；写模式对每个订单独立提交或完整回滚。
- 回填不得修改 `trade_order` 的支付、订单状态、deliveryTime、logisticsId、logisticsNo 或收件信息。
- 日志、异常、job 返回值、outbox 和迁移结果不得包含 tracking、phone、地址、签名、secret、provider 原始请求/响应、HMAC 输入或 digest。
- `MockLogisticsProviderClient` 只能在 `local` 和 `unit-test` profile 注册；生产不能把 mock 当真实物流服务。
- 保留用户现有无关改动；实施提交到 `codex/agent-rag`，不创建 PR，不修改 `main`。

---

## Parallel Execution Boundaries

1. 第一波可并行：9A；9B；9C-1（V020 migration、Node contract、H2 fixture）。三者不得同时修改同一文件；V020 stream 独占 baseline 和 migration catalog tests。
2. 第二波必须串行集成：先合并 9B 与 V020，再做 9C-2 atomic writer；writer 依赖扫描结果类型和事实 mapper。
3. 9C-3 job 依赖完整 migration service/writer，可与 9A 的最终安全复核并行。
4. Task 10 的 properties/config 骨架可提前独立开发；feature guard 实际接线必须等 9A/9C API 稳定后完成。
5. Runbook 和最终全量验证最后执行；不得基于未合并的测试数量或不存在的 metrics 编写结论。

---

### Task 9A-1: Add tri-state legacy projection and preserve endpoint ownership

**Files:**
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment/FulfillmentLegacyProjectionResult.java`
- Create: `.../service/fulfillment/FulfillmentLegacyProjectionService.java`
- Create: `.../service/fulfillment/FulfillmentLegacyProjectionServiceImpl.java`
- Modify: `.../dal/mysql/fulfillment/TrackingEventMapper.java`
- Modify: `.../service/order/TradeOrderQueryServiceImpl.java`
- Test: `.../src/test/java/cn/iocoder/yudao/module/trade/service/fulfillment/FulfillmentLegacyProjectionServiceTest.java`
- Test: `.../src/test/java/cn/iocoder/yudao/module/trade/service/order/TradeOrderQueryServiceImplTest.java`
- Modify test: `.../src/test/java/cn/iocoder/yudao/module/trade/controller/app/order/AppTradeOrderControllerTest.java`

**Interfaces:**

```java
public record FulfillmentLegacyProjectionResult(
        Mode mode, List<ExpressTrackRespDTO> events) {
    public enum Mode { FALLBACK, AUTHORITATIVE_EMPTY, AUTHORITATIVE_EVENTS }
    public FulfillmentLegacyProjectionResult {
        events = List.copyOf(events);
    }
    public static FulfillmentLegacyProjectionResult fallback() {
        return new FulfillmentLegacyProjectionResult(Mode.FALLBACK, List.of());
    }
    public static FulfillmentLegacyProjectionResult authoritative(List<ExpressTrackRespDTO> events) {
        return new FulfillmentLegacyProjectionResult(events.isEmpty()
                ? Mode.AUTHORITATIVE_EMPTY : Mode.AUTHORITATIVE_EVENTS, events);
    }
}

public interface FulfillmentLegacyProjectionService {
    FulfillmentLegacyProjectionResult project(Long tenantId, Long orderId);
}
```

`FALLBACK` 只用于“无 shipment”或“已选 subject 完全无 event”；只要 event 行已存在但全部 status 非法/不安全，就返回 `AUTHORITATIVE_EMPTY`，阻止攻击者通过坏 event 触发 provider 外呼。

- [ ] **Step 1: Write projection RED tests**

覆盖：显式 tenant；无 shipment fallback；有 shipment 无 event fallback；有 event 但全部非法 status authoritative empty；安全 event 只输出 `occurredAt/status.name()`；同时间按 event ID；跨 tenant/order 不可见；第一条非 CANCELED shipment 的第一条有事件 package 为唯一 subject，不合并 sibling package。

```java
assertEquals(Mode.AUTHORITATIVE_EMPTY, service.project(TENANT_ID, ORDER_ID).mode());
assertEquals(List.of(new ExpressTrackRespDTO(EVENT_TIME, "IN_TRANSIT")),
        service.project(TENANT_ID, ORDER_ID).events());
assertFalse(serialized.contains("RAW_DESCRIPTION_CANARY"));
assertFalse(serialized.contains("MANUAL_REASON_CANARY"));
```

- [ ] **Step 2: Run projection tests and confirm RED**

```powershell
cd 'D:\code\yudao电商管理平台前后端\yudao-cloud'
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=FulfillmentLegacyProjectionServiceTest,TradeOrderQueryServiceImplTest,AppTradeOrderControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: FAIL because the tri-state service does not exist and query service has no new-model branch.

- [ ] **Step 3: Implement deterministic projection**

Use existing tenant-aware `ShipmentMapper.selectListByOrderId`, package/leg ordering, and add one mapper method that always includes tenant and shipment IDs:

```java
default List<TrackingEventDO> selectLegacySubjectEvents(Long tenantId, Long shipmentId,
                                                         Long packageId, Long shipmentLegId) {
    return selectList(new LambdaQueryWrapperX<TrackingEventDO>()
            .eq(TrackingEventDO::getTenantId, tenantId)
            .eq(TrackingEventDO::getShipmentId, shipmentId)
            .and(packageId != null, q -> q.eq(TrackingEventDO::getPackageId, packageId)
                    .or().isNull(TrackingEventDO::getPackageId))
            .and(packageId == null && shipmentLegId != null,
                    q -> q.eq(TrackingEventDO::getShipmentLegId, shipmentLegId)
                          .or().isNull(TrackingEventDO::getShipmentLegId))
            .orderByAsc(TrackingEventDO::getOccurredAt)
            .orderByAsc(TrackingEventDO::getId));
}
```

The mapper expression may be split into two explicit methods if MyBatis wrapper condition readability suffers; both must include tenant/shipment predicates. Validate `standardStatus` with `ShipmentStatusEnum.valueOf` inside a safe helper; skip blank/unknown status without copying any raw field.

- [ ] **Step 4: Wire projection after existing ownership checks**

Keep both current order lookups exactly where they are. In the shared private method:

```java
if (fulfillmentProperties.isReadFromNewModel()) {
    Long tenantId = TenantContextHolder.getRequiredTenantId();
    FulfillmentLegacyProjectionResult projected = projectionService.project(tenantId, order.getId());
    if (projected.mode() != Mode.FALLBACK) {
        return projected.events();
    }
}
return getLegacyExpressTrackList(order);
```

Do not call projection from either controller before `selectByIdAndUserId`/`selectById` returns a verified order.

- [ ] **Step 5: Add ownership/non-regression tests**

Assert flag false never calls projection; app foreign user throws `ORDER_NOT_FOUND` and calls neither projection nor provider; flag true + fallback calls provider exactly once; authoritative empty calls provider zero times; admin permission annotation/URI are unchanged.

- [ ] **Step 6: Run GREEN and commit**

Run Step 2 again. Expected: all selected tests PASS.

```powershell
git add -- 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/order/TradeOrderQueryServiceImpl.java' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/dal/mysql/fulfillment/TrackingEventMapper.java' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade'
git commit -m "feat: project fulfillment events to legacy tracking"
```

### Task 9A-2: Replace plaintext cache keys and redact provider logs

**Files:**
- Create: `.../framework/fulfillment/cache/ExpressTrackCachePolicy.java`
- Create: `.../framework/fulfillment/cache/ExpressTrackCacheKeyGenerator.java`
- Modify: `.../service/order/TradeOrderQueryServiceImpl.java`
- Modify: `.../framework/delivery/core/client/impl/kd100/Kd100ExpressClient.java`
- Modify: `.../framework/delivery/core/client/impl/kdniao/KdNiaoExpressClient.java`
- Test: `.../framework/fulfillment/cache/ExpressTrackCacheKeyGeneratorTest.java`
- Test: `.../service/order/TradeOrderExpressTrackCacheTest.java`
- Test: `.../framework/delivery/core/client/impl/ExpressProviderLogRedactionTest.java`

**Interfaces:**

```java
@Component("expressTrackCachePolicy")
@RequiredArgsConstructor
public class ExpressTrackCachePolicy {
    private final FulfillmentProperties properties;
    public boolean hasHmacKey() {
        return properties.getIdempotencyHmacKey() != null
                && !properties.getIdempotencyHmacKey().isBlank();
    }
}
```

`ExpressTrackCacheKeyGenerator` expects exactly three String arguments and emits only `express-track:<64 lowercase hex>` using domain `express-track-cache:v1` and a length-prefixed tuple containing required tenant, code, tracking and phone.

- [ ] **Step 1: Write cache and log RED tests**

```java
assertTrue(key.matches("^express-track:[0-9a-f]{64}$"));
assertFalse(key.contains(TRACKING));
assertNotEquals(keyForTenant1, keyForTenant2);
assertThrows(IllegalStateException.class, () -> blankSecretGenerator.generate(target, method, args));
```

Create a proxied cache test with a counting fake provider: blank secret invokes provider on each call and creates no cache entry; configured secret invokes provider once for two identical calls; changing tenant or secret misses. Capture DEBUG logs from both providers and assert request body, response body, tracking, phone, sign, customer/business ID and secret canaries are absent.

- [ ] **Step 2: Run cache/log tests and confirm RED**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=ExpressTrackCacheKeyGeneratorTest,TradeOrderExpressTrackCacheTest,ExpressProviderLogRedactionTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: FAIL because the current SpEL key is plaintext and both providers log full bodies.

- [ ] **Step 3: Implement key generation and safe bypass**

Use `FulfillmentHashing.hmacSha256Hex(secret, canonical)`; canonicalize each value as UTF-8 byte length plus `:` plus exact value, normalizing only null to empty. Do not uppercase/truncate tracking.

```java
@Cacheable(cacheNames = RedisKeyConstants.EXPRESS_TRACK,
        condition = "@expressTrackCachePolicy.hasHmacKey()",
        keyGenerator = "expressTrackCacheKeyGenerator",
        unless = "#result == null")
public List<ExpressTrackRespDTO> getExpressTrackList(
        String code, String logisticsNo, String receiverMobile) { ... }
```

The Spring cache condition must be proven by the proxied test to execute before key generation. Empty secret is a deliberate no-cache compatibility path, not a weak-key path.

- [ ] **Step 4: Replace provider body logs**

Measure elapsed time without logging objects. Allowed success/failure fields are fixed provider name, HTTP status and elapsed milliseconds; exception logging must not include response body.

```java
log.debug("[httpRequest][provider=kd100 status={} elapsedMs={}]",
        responseEntity.getStatusCode().value(), elapsedMs);
```

Use the analogous fixed `kdniao` label. Never log `requestBody`, `responseEntity`, DTOs, signatures or account identifiers.

- [ ] **Step 5: Run GREEN, static leak scan, and commit**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=ExpressTrackCacheKeyGeneratorTest,TradeOrderExpressTrackCacheTest,ExpressProviderLogRedactionTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
rg -n "log\.(debug|info|warn|error).*requestBody|log\.(debug|info|warn|error).*responseEntity" yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/framework/delivery
```

Expected: tests PASS; `rg` returns no body-logging match.

```powershell
git add -- 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/framework/fulfillment/cache' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/framework/delivery/core/client/impl' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/order/TradeOrderQueryServiceImpl.java' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade'
git commit -m "fix: protect legacy tracking cache and provider logs"
```

### Task 9B: Add bounded candidate scan and truly read-only dry-run

**Files:**
- Create the 9B migration service/result/evaluator/fact-source files listed in the responsibility map.
- Modify: `.../dal/mysql/order/TradeOrderMapper.java`
- Modify: `.../dal/mysql/fulfillment/CarrierMapper.java`
- Test: `.../service/fulfillment/migration/FulfillmentLegacyMigrationServiceTest.java`

**Interfaces:**

```java
MigrationBatchResult migrateActiveOrders(Long tenantId, Long afterOrderId, int limit, boolean dryRun);

public record MigrationBatchResult(boolean dryRun, int scanned, int wouldMigrate,
        int migrated, int alreadyMigrated, int rejected, Long nextAfterOrderId,
        boolean hasMore, List<MigrationOrderResult> orders) {}

public record MigrationOrderResult(Long orderId, MigrationOutcome outcome, String reasonCode) {}
```

`MigrationOutcome` includes `WOULD_MIGRATE`, `MIGRATED`, `ALREADY_MIGRATED`, `NOT_SHIPPED`, `BLANK_TRACKING`, `INVALID_CARRIER`, `TRACKING_CONFLICT`, `EXISTING_FULFILLMENT`, `MISSING_ROUTE_FACTS`, `MISSING_WAREHOUSE`, `MISSING_PROVIDER`, `MISSING_DELIVERY_TIME`, `INVALID_ORDER_ITEMS`, `IDEMPOTENCY_CONFLICT`, `CONCURRENT_CHANGE`.

- [ ] **Step 1: Write cursor and zero-write RED tests**

Cover `afterOrderId` exclusive, ascending order, `limit+1` hasMore, limit `1..100`, tenant mismatch, scan statuses only 10/20, and candidate requiring at least one logistics field. Status 10 is visible for audit but must always report `NOT_SHIPPED`; only legacy status 20 may reach full eligibility or write. Verify cursor advances over rejected rows. Snapshot all fulfillment/idempotency/outbox row counts before and after dry-run; assert exact equality and provider client zero calls.

- [ ] **Step 2: Run RED**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=FulfillmentLegacyMigrationServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: FAIL because the migration service and candidate mapper do not exist.

- [ ] **Step 3: Add explicit candidate SQL semantics**

Implement a mapper query equivalent to:

```sql
WHERE tenant_id = :tenantId
  AND id > :afterOrderId
  AND status IN (10, 20)
  AND ((logistics_id IS NOT NULL AND logistics_id <> 0)
       OR (logistics_no IS NOT NULL AND TRIM(logistics_no) <> ''))
  AND deleted = FALSE
ORDER BY id ASC
LIMIT :limitPlusOne
```

Add `selectByIdAndTenantIdForUpdate(tenantId, orderId)` now for 9C. Never use the existing tenant-implicit lock method for migration.

- [ ] **Step 4: Implement shared eligibility with fail-closed facts**

Trim tracking only; preserve case/punctuation. Resolve carrier with a tenant/status/deleted-filtered list by `legacyExpressId` and require exactly one row. Validate nonempty positive order-item quantities, nonnull delivery time, no existing shipment, and no tracking collision. In 9B, `LegacyMigrationFactSourceImpl` returns `Optional.empty()`, so otherwise valid orders report `MISSING_ROUTE_FACTS` and never `WOULD_MIGRATE` until V020 is integrated.

- [ ] **Step 5: Reject non-dry-run until writer exists**

The 9B implementation must explicitly reject `dryRun=false` with a stable configuration/service error; it must not silently report success and must not insert an idempotency row.

- [ ] **Step 6: Run GREEN and commit**

Run Step 2 again. Expected: candidate and dry-run tests PASS, all table counts unchanged.

```powershell
git add -- 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment/migration' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/dal/mysql/order/TradeOrderMapper.java' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/dal/mysql/fulfillment/CarrierMapper.java' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/fulfillment/migration'
git commit -m "feat: scan legacy fulfillment migration candidates"
```

### Task 9C-1: Append V020 authoritative per-order migration facts

**Files:**
- Create: `yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V020__trade_fulfillment_legacy_migration_fact.sql`
- Modify: `.../sql/mysql/oakved-baseline.sql`
- Create: `furniture web/tests/databaseFulfillmentLegacyMigrationFact.test.js`
- Modify: `furniture web/tests/databaseFulfillmentMigration.test.js`
- Modify: `furniture web/tests/databaseFulfillmentPermissionsMigration.test.js`
- Modify: `furniture web/tests/databaseSafetyWorkflow.test.js`
- Modify: `furniture web/tests/dbMigrations.test.js`
- Create: `.../dal/dataobject/fulfillment/LegacyMigrationFactDO.java`
- Create: `.../dal/mysql/fulfillment/LegacyMigrationFactMapper.java`
- Create: `.../dal/mysql/fulfillment/LegacyMigrationReferenceMapper.java`
- Modify: `.../service/fulfillment/migration/LegacyMigrationFactSourceImpl.java`
- Modify: `.../src/test/resources/sql/create_tables.sql`
- Modify: `.../src/test/resources/sql/clean.sql`
- Test: `.../dal/mysql/fulfillment/LegacyMigrationFactPersistenceTest.java`

**V020 contract:** one absolute row per `(tenant_id, order_id)`, even when logically deleted. Operations update/reapprove the same row; they do not create historical duplicates.

```sql
CREATE TABLE IF NOT EXISTS `trade_fulfillment_legacy_migration_fact` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  `origin_country` char(2) NOT NULL,
  `destination_country` char(2) NOT NULL,
  `origin_timezone` varchar(64) NOT NULL,
  `destination_timezone` varchar(64) NOT NULL,
  `warehouse_id` bigint NOT NULL,
  `migration_provider_id` bigint NOT NULL,
  `approved_by` bigint NOT NULL,
  `approved_at` datetime(6) NOT NULL,
  `source_reference` varchar(255) NOT NULL,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_legacy_migration_fact_order` (`tenant_id`,`order_id`),
  KEY `idx_legacy_migration_fact_provider` (`tenant_id`,`migration_provider_id`),
  KEY `idx_legacy_migration_fact_warehouse` (`tenant_id`,`warehouse_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

- [ ] **Step 1: Write V020 RED contracts**

Assert catalog is contiguous 001-020; V015-V019 bytes are unchanged; exact columns are NOT NULL; tenant/order unique does not include `deleted`; no credentials/tracking/phone/address columns; baseline V020 section is byte-equivalent; H2 table and clean order include the new table.

- [ ] **Step 2: Run Node RED**

```powershell
cd 'D:\code\furniture web'
npm.cmd test -- databaseFulfillmentLegacyMigrationFact.test.js databaseFulfillmentMigration.test.js databaseFulfillmentPermissionsMigration.test.js databaseSafetyWorkflow.test.js dbMigrations.test.js
```

Expected: FAIL because V020 and its baseline section do not exist.

- [ ] **Step 3: Add SQL, DO and tenant-explicit mappers**

`LegacyMigrationFactDO` excludes `sourceReference` from `toString`. Mapper reads only `tenantId + orderId + deleted=false`. `LegacyMigrationReferenceMapper` returns booleans/counts only:

```java
@Select("SELECT COUNT(*) FROM erp_warehouse WHERE tenant_id=#{tenantId} " +
        "AND id=#{warehouseId} AND status=0 AND deleted=FALSE")
long countEnabledWarehouse(Long tenantId, Long warehouseId);
```

The fact source returns empty for no/deleted rows. It must not infer values from any other table.

- [ ] **Step 4: Validate fact semantics in persistence/service tests**

Require same uppercased country and membership in `{US, CA}`, both exact IANA zone IDs, positive and enabled tenant warehouse, enabled tenant migration provider, nonnull approval identity/time and nonblank source reference. Cross-border, missing, disabled or cross-tenant references return stable reason codes and zero writes.

- [ ] **Step 5: Regenerate baseline and run GREEN**

```powershell
cd 'D:\code\yudao电商管理平台前后端\yudao-cloud'
node .\sql\mysql\build-oakved-baseline.mjs
cd 'D:\code\furniture web'
npm.cmd test -- databaseFulfillmentLegacyMigrationFact.test.js databaseFulfillmentMigration.test.js databaseFulfillmentPermissionsMigration.test.js databaseSafetyWorkflow.test.js dbMigrations.test.js
cd 'D:\code\yudao电商管理平台前后端\yudao-cloud'
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=LegacyMigrationFactPersistenceTest,FulfillmentLegacyMigrationServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: generator reports 20 migrations; all selected tests PASS.

- [ ] **Step 6: Commit**

```powershell
git add -- 'furniture web/tests' 'yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V020__trade_fulfillment_legacy_migration_fact.sql' 'yudao电商管理平台前后端/yudao-cloud/sql/mysql/oakved-baseline.sql' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/dal/dataobject/fulfillment/LegacyMigrationFactDO.java' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/dal/mysql/fulfillment' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/resources/sql' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/dal/mysql/fulfillment'
git commit -m "feat: add approved legacy migration facts"
```

### Task 9C-2: Add fail-closed, per-order atomic backfill writer

**Files:**
- Create: `.../service/fulfillment/migration/FulfillmentLegacyMigrationWriter.java`
- Create: `.../service/fulfillment/migration/FulfillmentLegacyMigrationWriterImpl.java`
- Modify: `.../service/fulfillment/migration/FulfillmentLegacyMigrationServiceImpl.java`
- Modify: `.../service/fulfillment/migration/LegacyMigrationEligibilityEvaluator.java`
- Modify: `.../dal/mysql/fulfillment/FulfillmentIdempotencyMapper.java`
- Modify only if insert helpers are needed: shipment/item/package/leg/event/summary/outbox mappers.
- Test: `.../service/fulfillment/migration/FulfillmentLegacyMigrationWriterTest.java`
- Test: `.../service/fulfillment/migration/FulfillmentLegacyMigrationTransactionTest.java`

**Interfaces:**

```java
public interface FulfillmentLegacyMigrationWriter {
    MigrationOrderResult migrateOne(Long tenantId, Long orderId);
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public MigrationOrderResult migrateOne(Long tenantId, Long orderId) { ... }
```

- [ ] **Step 1: Write transaction/idempotency RED tests**

Assert one eligible order creates exactly 1 shipment, N full-quantity items, 1 package, 1 leg, 1 migration event, 1 summary, 1 outbox and 1 COMPLETED idempotency row. Repeat and concurrent calls retain those counts. Change source tracking/facts after completion and expect `IDEMPOTENCY_CONFLICT`, never overwrite. Inject failure at every write boundary and assert all rows for that order roll back while an earlier batch order remains committed.

- [ ] **Step 2: Run RED**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=FulfillmentLegacyMigrationWriterTest,FulfillmentLegacyMigrationTransactionTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: FAIL because no writer exists.

- [ ] **Step 3: Implement operation-scoped HMAC and exact replay first**

```java
String keyHash = hmac(secret, "legacy-migration:key:v1|" + tenantId + "|" + orderId);
String requestHash = hmac(secret, canonicalRequestIncludingOrderItemsAndAllApprovedFacts);
```

Load exact completed replay before stale/new validation. Return `ALREADY_MIGRATED` only if request hash matches and resource shipment still belongs to the same tenant/order. A mismatch, PROCESSING collision or missing/mismatched resource is a stable conflict. Set migration idempotency expiry to `9999-12-31T23:59:59` and document that cleanup must exclude operation `LEGACY_ORDER_MIGRATION`.

- [ ] **Step 4: Lock and revalidate every authoritative fact**

Inside the transaction: lock order by tenant/id; require legacy status exactly 20; recheck source snapshot; rerun fact/carrier/provider/warehouse/timezone/item/tracking validation; reject if any shipment already exists without exact migration replay. Status 10 returns `NOT_SHIPPED` with zero writes. This second defense prevents migration from appending to live fulfillment.

- [ ] **Step 5: Insert the complete historical aggregate without provider calls**

Use stable, non-sensitive generated shipment/package numbers. Insert:

- `PARCEL` shipment, status `HANDED_TO_CARRIER`, approved route/timezones/warehouse/provider;
- one shipment item per positive order item for full purchased count;
- `PARCEL` package with the trimmed legacy tracking and unique enabled carrier;
- package-bound sequence 1 `LAST_MILE` leg, status `HANDED_TO_CARRIER`, approved migration provider;
- one event at legacy `deliveryTime`, source/providerStatus `MIGRATION`, standard/previous/result `HANDED_TO_CARRIER`, transition `TIMELINE_ONLY`, no description/location/raw/manual fields, deterministic HMAC external identity;
- summary `SHIPPED`, shipmentCount 1, deliveredShipmentCount 0;
- `LEGACY_ORDER_MIGRATED` outbox payload containing only tenant/order/shipment IDs and safe statuses;
- completed idempotency `resourceType=SHIPMENT`.

Never call registry/client/dispatch/applyEvent and never update `trade_order`.

- [ ] **Step 6: Wire batch writes and preserve dry-run parity**

`dryRun=true` uses the shared evaluator and reports `WOULD_MIGRATE`; `dryRun=false` invokes the separate writer bean once per scanned order. Both paths must produce the same reason for an unchanged invalid snapshot. The batch catches only mapped per-order domain/concurrency outcomes; unknown infrastructure errors fail the invocation without logging source objects.

- [ ] **Step 7: Run GREEN and regress core transactions**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=FulfillmentLegacyMigrationWriterTest,FulfillmentLegacyMigrationTransactionTest,FulfillmentLegacyMigrationServiceTest,FulfillmentCommandTransactionTest,FulfillmentCommandAtomicityTest,FulfillmentDispatchTransactionTest,FulfillmentTrackingTransactionTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: all selected tests PASS; provider fake has zero interactions; payment/order state assertions remain unchanged.

- [ ] **Step 8: Commit**

```powershell
git add -- 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment/migration' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/dal/mysql/fulfillment' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/fulfillment/migration'
git commit -m "feat: backfill approved legacy fulfillment orders"
```

### Task 9C-3: Add bounded tenant migration job

**Files:**
- Create: `.../job/fulfillment/FulfillmentLegacyMigrationJob.java`
- Test: `.../job/fulfillment/FulfillmentLegacyMigrationJobTest.java`

**Interfaces:**

```java
@XxlJob("fulfillmentLegacyMigrationJob")
@TenantJob
public String execute(String param)
```

Input JSON contains only `afterOrderId`, `limit`, `dryRun`. Defaults are `0`, `100`, `true`. Tenant always comes from `TenantContextHolder.getRequiredTenantId()`; reject tenant-like fields rather than accepting them.

- [ ] **Step 1: Write job RED tests**

Cover empty param defaults, bounds, one bounded batch, tenant context, no internal loop, dry-run while all write flags are false, explicit write refusal unless all required flags are true, and a returned string containing only counts/cursor/reason counts.

- [ ] **Step 2: Run RED, implement, and run GREEN**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=FulfillmentLegacyMigrationJobTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected before implementation: FAIL. Expected after implementation: PASS. Captured logs/return string must not contain order payloads, tracking, phone, facts, exception messages or digests.

- [ ] **Step 3: Commit**

```powershell
git add -- 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/job/fulfillment' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/job/fulfillment'
git commit -m "feat: schedule bounded legacy fulfillment migration"
```

### Task 10-1: Bind, validate, and actually enforce rollout flags

**Files:**
- Modify: `.../framework/fulfillment/config/FulfillmentProperties.java`
- Create: `.../framework/fulfillment/config/FulfillmentConfiguration.java`
- Create: `.../framework/fulfillment/config/FulfillmentFeatureGuard.java`
- Modify: `.../framework/fulfillment/core/impl/MockLogisticsProviderClient.java`
- Modify: `.../service/fulfillment/FulfillmentCommandServiceImpl.java`
- Modify: `.../service/fulfillment/FulfillmentTrackingServiceImpl.java`
- Modify: `.../service/fulfillment/FulfillmentQueryServiceImpl.java`
- Modify: `.../service/fulfillment/migration/FulfillmentLegacyMigrationWriterImpl.java`
- Modify affected existing fulfillment service/controller tests to set explicit enabled test properties.
- Test: `.../framework/fulfillment/config/FulfillmentPropertiesTest.java`
- Test: `.../framework/fulfillment/config/FulfillmentFeatureGuardTest.java`

**Flag dependencies:**

| Flag | Required dependencies | Runtime boundary |
|---|---|---|
| `enabled` | none | master for new admin model |
| `writeNewModel` | enabled, nonblank HMAC, nonblank provider | all command/tracking mutations |
| `readFromNewModel` | enabled | legacy compatibility projection |
| `customerUiEnabled` | enabled + readFromNewModel | future customer UI only |
| `legacyMigrationWriteEnabled` | enabled + writeNewModel | non-dry-run migration writer/job |

Blank HMAC is valid only while all new-model writes are off; the old express query then bypasses cache as specified in 9A-2.

- [ ] **Step 1: Write property and guard RED tests**

Use `ApplicationContextRunner` and explicit profiles. Assert one properties bean, all flag defaults false, invalid dependency combinations fail startup, write with blank HMAC/provider fails, blank HMAC with all writes off succeeds, `mock` default/prod fails when write is enabled, and local/unit-test mock succeeds. Guard tests assert disabled query/write/migration creates zero DB changes.

- [ ] **Step 2: Run RED**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=FulfillmentPropertiesTest,FulfillmentFeatureGuardTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: FAIL because properties are still component-registered without cross-validation and no guard exists.

- [ ] **Step 3: Implement single registration and cross-validation**

Remove `@Component` from properties; add `@Validated` and `@AssertTrue` methods whose messages name only property relationships, never values. Register once:

```java
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FulfillmentProperties.class)
public class FulfillmentConfiguration { ... }
```

A startup validator may use `Environment` and `LogisticsProviderRegistry` to reject `mock` outside local/unit-test and ensure configured write provider client exists, without injecting registry back into properties.

- [ ] **Step 4: Enforce guards at service boundaries**

```java
public void requireReadEnabled() { require(properties.isEnabled()); }
public void requireWriteEnabled() { require(properties.isEnabled() && properties.isWriteNewModel()); }
public void requireMigrationWriteEnabled() {
    require(properties.isEnabled() && properties.isWriteNewModel()
            && properties.isLegacyMigrationWriteEnabled());
}
```

Call read guard from new-model admin query service; write guard at every public command and tracking mutation; migration guard inside writer and job non-dry branch. Do not guard old provider fallback or migration dry-run. Add `@Profile({"local", "unit-test"})` to mock provider.

- [ ] **Step 5: Update existing tests explicitly**

Existing unit/transaction tests that exercise new-model services must set `enabled=true`, `writeNewModel=true`, provider `mock`, and deterministic 32+ character HMAC. Tests for reads set `enabled=true`. Do not weaken production defaults to keep old tests green.

- [ ] **Step 6: Run focused integration GREEN**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=FulfillmentPropertiesTest,FulfillmentFeatureGuardTest,TradeFulfillmentControllerTest,FulfillmentQueryServiceImplTest,FulfillmentCommandServiceImplTest,FulfillmentTrackingServiceImplTest,FulfillmentLegacyMigrationServiceTest,FulfillmentLegacyMigrationJobTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: all selected tests PASS; disabled paths have zero writes.

- [ ] **Step 7: Commit**

```powershell
git add -- 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/framework/fulfillment' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade'
git commit -m "feat: enforce fulfillment rollout flags"
```

### Task 10-2: Add safe YAML profiles and rollout runbook

**Files:**
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-server/src/main/resources/application.yaml`
- Modify: `.../yudao-server/src/main/resources/application-local.yaml`
- Modify: `.../yudao-module-trade-server/src/test/resources/application-unit-test.yaml`
- Create: `docs/runbooks/north-america-fulfillment-phase1.md`
- Test: `.../framework/fulfillment/config/FulfillmentYamlBindingTest.java`

- [ ] **Step 1: Write YAML binding RED tests**

Shared must bind all flags false, provider/environment secret optional and blank. Local must bind only `enabled=true`, `writeNewModel=true`, provider `mock`, deterministic local-only 32+ key; read/customer/migration false. Unit-test must keep all flags false with mock and deterministic test-only key.

- [ ] **Step 2: Add exact YAML blocks**

Shared:

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

Local override under the existing `yudao.trade` tree:

```yaml
fulfillment:
  enabled: true
  write-new-model: true
  read-from-new-model: false
  customer-ui-enabled: false
  legacy-migration-write-enabled: false
  provider-code: mock
  idempotency-hmac-key: local-only-fulfillment-hmac-key-never-use-outside-local
```

- [ ] **Step 3: Write the runbook with executable, truthful procedures**

Required sections:

1. Scope/non-goals: US/CA domestic only; no China-North-America or US-CA cross-border; no real provider adapter yet.
2. Prerequisites: backup, V015-V020 contiguous, HMAC management, carrier/provider/facts approval, V019 RBAC assignment decision.
3. Flag dependency table and separate master/write/legacy-read/customer/migration meanings.
4. V020 fact insertion/reapproval template with no real credentials/PII and mandatory approval/source evidence.
5. Static migration verification via `npm.cmd run verify:db-migrations`; explicitly state `invoke-local-migrations.ps1` has no `-DryRun`.
6. Disposable MySQL rehearsal using a named temporary database and cleanup only after verification.
7. Migration job dry-run, reason-count review, cursor persistence, then explicit bounded write invocation.
8. Smoke: health, RBAC, US/CA domestic, cross-border rejection, idempotency, legacy fallback, authoritative empty, manual event, migration replay.
9. Observation using Actuator health, safe logs and DB queries for outbox backlog, stale PROCESSING, unknown mappings and shipment exceptions; explicitly state no fulfillment Micrometer counters exist yet.
10. Enable order: deploy schema/code/HMAC -> enabled -> small write -> migration dry-run/write -> staff read -> customer UI.
11. Disable order: customer -> read -> migration write -> write/provider ingestion -> enabled; retain HMAC.
12. Rollback: never drop V015-V020 or delete new data; restore old reads by flags; preserve replay/audit evidence.
13. Security and evidence-record template excluding secret, raw provider payload, tracking, phone and address.

- [ ] **Step 4: Run YAML tests and documentation checks**

```powershell
cd 'D:\code\yudao电商管理平台前后端\yudao-cloud'
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=FulfillmentYamlBindingTest,FulfillmentPropertiesTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
cd 'D:\code'
rg -n "invoke-local-migrations.*-DryRun|FULFILLMENT_IDEMPOTENCY_HMAC_KEY=.*[^}]|tracking(number)?|receiverMobile" docs/runbooks/north-america-fulfillment-phase1.md
```

Expected: tests PASS; review every `rg` hit and keep only explanatory prohibitions or variable names without values/PII; no nonexistent `-DryRun` command.

- [ ] **Step 5: Commit**

```powershell
git add -- 'docs/runbooks/north-america-fulfillment-phase1.md' 'yudao电商管理平台前后端/yudao-cloud/yudao-server/src/main/resources/application.yaml' 'yudao电商管理平台前后端/yudao-cloud/yudao-server/src/main/resources/application-local.yaml' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/resources/application-unit-test.yaml' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/framework/fulfillment/config'
git commit -m "docs: add fulfillment rollout controls"
```

### Task 10-3: Final verification and acceptance evidence

**Files:**
- Modify only if a failing test proves a scoped defect; do not make opportunistic refactors.
- Update: `.superpowers/sdd/progress.md` with command, exit code and test counts; this local ledger is not staged unless repository policy changes.

- [ ] **Step 1: Run migration catalog verification**

```powershell
cd 'D:\code\furniture web'
npm.cmd test -- databaseFulfillmentLegacyMigrationFact.test.js databaseFulfillmentMigration.test.js databaseFulfillmentPermissionsMigration.test.js databaseSafetyWorkflow.test.js dbMigrations.test.js
npm.cmd run verify:db-migrations
```

Expected: all five files PASS; verification reports contiguous V001-V020 and a deterministic baseline.

- [ ] **Step 2: Run complete fulfillment regression**

```powershell
cd 'D:\code\yudao电商管理平台前后端\yudao-cloud'
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=FulfillmentPropertiesTest,FulfillmentFeatureGuardTest,FulfillmentLegacyProjectionServiceTest,ExpressTrackCacheKeyGeneratorTest,TradeOrderExpressTrackCacheTest,ExpressProviderLogRedactionTest,FulfillmentLegacyMigrationServiceTest,FulfillmentLegacyMigrationWriterTest,FulfillmentLegacyMigrationTransactionTest,FulfillmentLegacyMigrationJobTest,TradeFulfillmentControllerTest,FulfillmentQueryServiceImplTest,FulfillmentCommandServiceImplTest,FulfillmentCommandTransactionTest,FulfillmentCommandAtomicityTest,FulfillmentDispatchServiceTest,FulfillmentDispatchTransactionTest,FulfillmentTrackingServiceImplTest,FulfillmentTrackingTransactionTest,FulfillmentPersistenceTest,TrackingStatusMappingPersistenceTest,ShipmentStateMachineTest,LogisticsProviderRegistryTest,AppTradeOrderControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: all selected tests PASS and no real external provider call occurs.

- [ ] **Step 3: Run full module and package**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am test
mvn.cmd -pl yudao-server -am "-DskipTests" package
```

Expected: both exit 0; `yudao-server/target/yudao-server.jar` is produced.

- [ ] **Step 4: Run security and repository hygiene checks**

```powershell
cd 'D:\code'
git diff --check
git status --short
git diff --name-only HEAD
rg -n "log\.(debug|info|warn|error).*?(tracking|receiver|phone|requestBody|responseEntity|DataSign|secret|rawPayload)" 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java'
```

Expected: no whitespace errors; only planned files plus pre-existing user changes appear; every log scan hit is manually proven safe before completion.

- [ ] **Step 5: Record every acceptance item**

- V020 applies after immutable V019 and baseline is deterministic.
- App foreign-user/cross-tenant requests fail before projection/provider.
- Blank HMAC bypasses cache without breaking old queries or creating weak/plaintext keys.
- Configured cache key is tenant-aware HMAC and contains no source values.
- Provider DEBUG logs contain no request/response body, tracking, phone, account ID or signature.
- No shipment/no event falls back; unsafe existing events return authoritative empty and never call provider.
- Dry-run writes zero rows and calls zero providers; cursor advances over invalid records.
- Missing/stale/cross-border/unapproved V020 facts write zero rows.
- Exact replay creates no duplicate; modified source facts conflict without overwrite.
- Each migrated order is all-or-nothing; one bad order does not roll back a prior committed order.
- Migration does not alter payment/order/logistics legacy columns.
- All new-model reads/writes can be disabled by flags without dropping V015-V020.
- Mock provider cannot register in production profile.

- [ ] **Step 6: Commit any final test-only corrections, then request review**

If a correction was needed, stage only the exact file named by the failing test and commit it with `git commit -m "test: complete fulfillment rollout verification"`. If no correction was needed, do not create an empty commit. Use `superpowers:requesting-code-review` for a final cross-task review before reporting completion.

---

## Execution Notes

- The original Task 9 statement that every legacy order can be backfilled is superseded: current `trade_order` cannot prove route countries, timezones, warehouse or provider. V020 is the required authority boundary.
- The empty-HMAC decision intentionally favors compatibility: old queries remain available but uncached. This is secure because no alternative key is generated; production cost/latency impact must be visible in deployment checks.
- `AUTHORITATIVE_EMPTY` is security-significant. Do not collapse it into fallback or `Optional.empty()`.
- Real provider selection/webhook/polling remains blocked on the separate Phase 0 provider decision record; this plan does not invent provider-specific credentials or protocol DTOs.
