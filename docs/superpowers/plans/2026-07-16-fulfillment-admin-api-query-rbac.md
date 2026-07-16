# Fulfillment Admin API, Query Read Models, and RBAC Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Task 7B 集成后交付北美履约管理端的九个最小 API、tenant-scoped 查询模型、物流标识符脱敏和可分配的五项 RBAC 按钮权限。

**Architecture:** 写接口只负责校验 HTTP 契约、从服务端上下文注入 tenant/operator/trace/path shipment ID，并调用既有 command/tracking service；读接口通过独立只读 query service 和显式 tenant 条件的 mapper 组装专用响应 VO。权限通过追加 V019 migration 写入 `system_menu`，不创建页面、不绑定角色；所有 mutation 成功后由客户端重新 GET detail 获取真实聚合版本。

**Tech Stack:** Java 17, Spring Boot 3.5, Spring MVC, Jakarta Validation, Spring Security method authorization, MyBatis-Plus 3.5, H2, JUnit 5, Mockito, Maven, MySQL 8, Node.js ESM, Vitest 4

## Global Constraints

- 执行前确认 Task 6B、Task 7 和 Task 7B 已集成；`V018__trade_manual_tracking_audit.sql` 必须是当前最后一条 migration。
- 只实现本计划列出的九个管理端端点；不实现通用 shipment update、retry-tracking、exceptions、appointments、POD、用户端 API、provider 管理或 provider 网络调用。
- Controller 类级路径固定为 `/trade/fulfillment/shipments`；框架统一增加 `/admin-api`，Controller 不得重复写该前缀。
- 六个 POST/PUT 写端点全部要求原样 `Idempotency-Key`，最大 128；key 不 trim、不落日志、不回响应，直接传给领域 service。
- create 不带 `expectedVersion`；ready、package、leg、dispatch、manual-event 必须携带非负 `expectedVersion`。
- create/package/leg 返回既有 resource `Long` ID，ready/dispatch 返回 `true`，manual-event 返回 `TrackingApplyRespVO`；任何成功写入后必须重新 GET detail，以响应中的真实 `version` 发起下一次写入。
- 禁止用 `expectedVersion + 1` 构造响应版本；幂等 replay、timeline-only 和并发写会使该值不可靠。
- tenant 固定来自 `TenantContextHolder.getRequiredTenantId()`；manual operator 固定来自 `SecurityFrameworkUtils.getLoginUserId()`；manual trace 优先来自 `TracerUtils.getTraceId()`，blank 时由服务端生成 UUID。
- HTTP body 不得声明 tenantId、shipmentId、operatorId、requestTraceId、creator、updater、provider credential、raw payload、external event ID、outbox status 或事件 priority。
- 查询 mapper 必须显式包含 tenant 条件，不能只依赖 MyBatis tenant 插件；普通 GET 不使用 `FOR UPDATE`。
- tracking number、PRO、BOL 在 query service 映射阶段变成 `***` 加末尾至多四位；响应 VO 不声明 raw 属性。
- 六个写方法都使用 `@ApiAccessLog(requestEnable = false)`；响应日志维持默认关闭。
- request/command 的 tracking、PRO、BOL、location、manual reason 使用 `@ToString.Exclude`；异常、领域日志和 outbox 不复制这些值或 trace。
- V019 只新增五个 type=3 按钮权限，父菜单为 `id=2076`；不写 `system_role_menu`，不自动给任何角色授权。
- 不修改已发布的 V015-V018，不直接编辑生成文件 `oakved-baseline.sql`；只通过 `npm.cmd run build:db-baseline` 生成。
- 单元和集成测试不得访问外部网络。

## Fixed HTTP and Permission Contract

| HTTP | Controller mapping | Permission | `CommonResult.data` |
|---|---|---|---|
| `POST /admin-api/trade/fulfillment/shipments` | `POST ""` | `trade:fulfillment:shipment:create` | new shipment `Long` |
| `PUT /admin-api/trade/fulfillment/shipments/{id}/ready` | `PUT "/{id}/ready"` | `trade:fulfillment:shipment:update` | `true` |
| `POST /admin-api/trade/fulfillment/shipments/{id}/packages` | `POST "/{id}/packages"` | `trade:fulfillment:shipment:update` | new package `Long` |
| `POST /admin-api/trade/fulfillment/shipments/{id}/legs` | `POST "/{id}/legs"` | `trade:fulfillment:shipment:update` | new leg `Long` |
| `POST /admin-api/trade/fulfillment/shipments/{id}/dispatch` | `POST "/{id}/dispatch"` | `trade:fulfillment:shipment:dispatch` | `true` |
| `POST /admin-api/trade/fulfillment/shipments/{id}/manual-event` | `POST "/{id}/manual-event"` | `trade:fulfillment:tracking:manual` | `TrackingApplyRespVO` |
| `GET /admin-api/trade/fulfillment/shipments/{id}` | `GET "/{id}"` | `trade:fulfillment:shipment:query` | `ShipmentDetailRespVO` |
| `GET /admin-api/trade/fulfillment/shipments/{id}/timeline` | `GET "/{id}/timeline"` | `trade:fulfillment:shipment:query` | `List<TrackingEventRespVO>` |
| `GET /admin-api/trade/fulfillment/shipments/page` | `GET "/page"` | `trade:fulfillment:shipment:query` | `PageResult<ShipmentPageItemRespVO>` |

## File Map and Dependency Order

Execute and review in this order:

1. **8A Query + mask:** produces response/page VO types and `FulfillmentQueryService` consumed by 8B.
2. **8B Controller + request VO + server context + access-log boundary:** consumes 8A and Task 7B's manual command/service contract.
3. **8C V019 RBAC + baseline:** independent of Java compilation, but lands last so the final regression sees the complete catalog.

### 8A files

- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/admin/fulfillment/vo/ShipmentPageReqVO.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/admin/fulfillment/vo/ShipmentPageItemRespVO.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/admin/fulfillment/vo/ShipmentDetailRespVO.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/admin/fulfillment/vo/ShipmentItemRespVO.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/admin/fulfillment/vo/ShipmentPackageRespVO.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/admin/fulfillment/vo/ShipmentLegRespVO.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/admin/fulfillment/vo/TrackingEventRespVO.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment/FulfillmentQueryService.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment/FulfillmentQueryServiceImpl.java`
- Modify: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/dal/mysql/fulfillment/ShipmentMapper.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/fulfillment/FulfillmentQueryServiceImplTest.java`

### 8B files

- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/admin/fulfillment/TradeFulfillmentController.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/admin/fulfillment/vo/ShipmentCreateReqVO.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/admin/fulfillment/vo/ShipmentCreateItemReqVO.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/admin/fulfillment/vo/ShipmentPackageCreateReqVO.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/admin/fulfillment/vo/ShipmentLegCreateReqVO.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/admin/fulfillment/vo/ShipmentVersionReqVO.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/admin/fulfillment/vo/ManualTrackingEventReqVO.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/admin/fulfillment/vo/TrackingApplyRespVO.java`
- Create: `yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/controller/admin/fulfillment/TradeFulfillmentControllerTest.java`

### 8C files

- Create: `yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V019__trade_fulfillment_admin_permissions.sql`
- Create: `furniture web/tests/databaseFulfillmentPermissionsMigration.test.js`
- Modify: `furniture web/tests/databaseFulfillmentMigration.test.js`
- Modify: `furniture web/tests/dbMigrations.test.js`
- Modify: `furniture web/tests/databaseSafetyWorkflow.test.js`
- Generate: `yudao电商管理平台前后端/yudao-cloud/sql/mysql/oakved-baseline.sql`

---

### Task 8A: Add tenant-scoped query read models and identifier masking

**Interfaces:**

- Consumes: `ShipmentMapper`, `ShipmentItemMapper.selectListByShipmentId`, `ShipmentPackageMapper.selectListByShipmentId`, `ShipmentLegMapper.selectListByShipmentId`, `TrackingEventMapper.selectListByShipmentId`, `FULFILLMENT_SHIPMENT_NOT_FOUND`.
- Produces:

```java
public interface FulfillmentQueryService {
    ShipmentDetailRespVO getShipment(Long tenantId, Long shipmentId);
    List<TrackingEventRespVO> getTimeline(Long tenantId, Long shipmentId);
    PageResult<ShipmentPageItemRespVO> getShipmentPage(Long tenantId, ShipmentPageReqVO reqVO);
}
```

- Read operations are side-effect free: no `FOR UPDATE`, provider calls, cache writes, summary writes or watermark updates.

- [ ] **Step 1: Write the failing H2 query contract tests**

Create `FulfillmentQueryServiceImplTest` as `BaseDbUnitTest` with `@Import(FulfillmentQueryServiceImpl.class)` and real fulfillment mappers. Use the existing fulfillment test SQL fixture/import pattern, then add these exact test methods:

```java
@Test void getShipment_masksIdentifiersAndReturnsOrderedChildren()
@Test void getShipment_returnsEmptyChildListsInsteadOfNull()
@Test void getShipment_rejectsCrossTenantLikeMissingShipment()
@Test void getTimeline_requiresTenantScopedParentAndOrdersByOccurredAtThenId()
@Test void getTimeline_exposesOnlyNormalizedProviderStatus()
@Test void getShipmentPage_isTenantScopedAndStableForEqualCreateTime()
@Test void getShipmentPage_filtersByOrderId()
@Test void getShipmentPage_filtersByShipmentNo()
@Test void getShipmentPage_filtersByShipmentType()
@Test void getShipmentPage_filtersByStatus()
@Test void getShipmentPage_filtersByOriginCountry()
@Test void getShipmentPage_filtersByDestinationCountry()
@Test void getShipmentPage_filtersByCreateTimeRange()
@Test void maskIdentifier_handlesNullBlankShortAndLongValues()
```

The fixtures must contain two tenants, two shipments with equal `create_time`, unordered child insert IDs/sequence values, provider and manual tracking events, and these identifiers:

```text
null       -> null
""         -> null
"AB"       -> "***AB"
"1234"     -> "***1234"
"1Z999999" -> "***9999"
```

For both cross-tenant and unknown shipment IDs, assert the same error code:

```java
assertServiceException(
        () -> queryService.getShipment(otherTenantId, shipmentId),
        FULFILLMENT_SHIPMENT_NOT_FOUND);
assertServiceException(
        () -> queryService.getShipment(tenantId, missingShipmentId),
        FULFILLMENT_SHIPMENT_NOT_FOUND);
```

Serialize detail/timeline response objects and assert the JSON does not contain any of:

```text
tenantId, creator, updater, deleted, lastEventOccurredAt, lastEventId,
trackingNumber, proNumber, bolNumber, originLocation, destinationLocation,
providerStatus, providerId (timeline only), externalEventId, eventHash,
description, location, rawPayloadRef, manualOperatorId, manualReason,
requestTraceId, idempotencyKeyHash
```

- [ ] **Step 2: Run the focused test and verify RED**

Run from `D:\code\yudao电商管理平台前后端\yudao-cloud`:

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=FulfillmentQueryServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: FAIL at test compilation because the query service and response VO types do not exist.

- [ ] **Step 3: Create the page request and response VO contract**

`ShipmentPageReqVO extends PageParam` and declares exactly:

```java
@Positive private Long orderId;
@Size(max = 32) private String shipmentNo;
private ShipmentTypeEnum shipmentType;
private ShipmentStatusEnum status;
@Pattern(regexp = "US|CA") @Size(max = 2) private String originCountry;
@Pattern(regexp = "US|CA") @Size(max = 2) private String destinationCountry;
@DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
private LocalDateTime[] createTime;
```

All VO classes use `@Data` and `@Schema`. Define response fields exactly as follows; do not inherit from DO types and do not add raw aliases:

```text
ShipmentPageItemRespVO:
  Long id, orderId, warehouseId, providerId
  String shipmentNo, originCountry, destinationCountry,
         originTimezone, destinationTimezone
  ShipmentTypeEnum shipmentType
  ShipmentStatusEnum status
  LocalDateTime estimatedDeliveryAt, deliveredAt, createTime, updateTime
  Integer version

ShipmentDetailRespVO:
  all page-item fields above
  List<ShipmentItemRespVO> items
  List<ShipmentPackageRespVO> packages
  List<ShipmentLegRespVO> legs

ShipmentItemRespVO:
  Long id, orderItemId, skuId
  BigDecimal quantity

ShipmentPackageRespVO:
  Long id, carrierId
  String packageNo, packageType, trackingNumberMasked,
         weightUnit, dimensionUnit
  BigDecimal weight, length, width, height
  ShipmentStatusEnum status
  Integer version

ShipmentLegRespVO:
  Long id, packageId, carrierId, providerId
  Integer sequenceNo, version
  String legType, serviceLevel, trackingNumberMasked,
         proNumberMasked, bolNumberMasked
  ShipmentStatusEnum status
  LocalDateTime startedAt, completedAt

TrackingEventRespVO:
  Long id, packageId, shipmentLegId
  ShipmentStatusEnum standardStatus
  String providerStatusNormalized, mappingVersion, transitionDecision,
         previousStatus, resultStatus, occurredTimezone, source
  Boolean mappingKnown
  LocalDateTime occurredAt, receivedAt
```

The current DO stores status as `String`; convert with `ShipmentStatusEnum.valueOf(status)` at the response boundary. No response VO declares `tenantId`, audit columns, raw logistics identifiers, raw provider status, description/location, provider identity, manual audit, hashes or payload references.

- [ ] **Step 4: Add read-only tenant methods to `ShipmentMapper`**

Add imports for `PageResult` and `ShipmentPageReqVO`, then implement:

```java
default ShipmentDO selectByIdAndTenantId(Long id, Long tenantId) {
    return selectOne(new LambdaQueryWrapperX<ShipmentDO>()
            .eq(ShipmentDO::getId, id)
            .eq(ShipmentDO::getTenantId, tenantId));
}

default PageResult<ShipmentDO> selectPage(Long tenantId, ShipmentPageReqVO reqVO) {
    return selectPage(reqVO, new LambdaQueryWrapperX<ShipmentDO>()
            .eq(ShipmentDO::getTenantId, tenantId)
            .eqIfPresent(ShipmentDO::getOrderId, reqVO.getOrderId())
            .eqIfPresent(ShipmentDO::getShipmentNo, reqVO.getShipmentNo())
            .eqIfPresent(ShipmentDO::getShipmentType,
                    reqVO.getShipmentType() == null ? null : reqVO.getShipmentType().name())
            .eqIfPresent(ShipmentDO::getStatus,
                    reqVO.getStatus() == null ? null : reqVO.getStatus().name())
            .eqIfPresent(ShipmentDO::getOriginCountry, reqVO.getOriginCountry())
            .eqIfPresent(ShipmentDO::getDestinationCountry, reqVO.getDestinationCountry())
            .betweenIfPresent(ShipmentDO::getCreateTime, reqVO.getCreateTime())
            .orderByDesc(ShipmentDO::getCreateTime)
            .orderByDesc(ShipmentDO::getId));
}
```

Do not route these calls through `selectByIdForUpdate`.

- [ ] **Step 5: Implement the query service with parent guards and explicit mapping**

Create `FulfillmentQueryServiceImpl` with `@Service` and inject the five mappers. The three public methods follow this exact sequence:

```java
@Override
public ShipmentDetailRespVO getShipment(Long tenantId, Long shipmentId) {
    ShipmentDO shipment = requireShipment(tenantId, shipmentId);
    ShipmentDetailRespVO response = mapDetail(shipment);
    response.setItems(itemMapper.selectListByShipmentId(tenantId, shipmentId)
            .stream().map(this::mapItem).toList());
    response.setPackages(packageMapper.selectListByShipmentId(tenantId, shipmentId)
            .stream().map(this::mapPackage).toList());
    response.setLegs(legMapper.selectListByShipmentId(tenantId, shipmentId)
            .stream().map(this::mapLeg).toList());
    return response;
}

@Override
public List<TrackingEventRespVO> getTimeline(Long tenantId, Long shipmentId) {
    requireShipment(tenantId, shipmentId);
    return trackingEventMapper.selectListByShipmentId(tenantId, shipmentId)
            .stream().map(this::mapTrackingEvent).toList();
}

@Override
public PageResult<ShipmentPageItemRespVO> getShipmentPage(Long tenantId, ShipmentPageReqVO reqVO) {
    PageResult<ShipmentDO> page = shipmentMapper.selectPage(tenantId, reqVO);
    return new PageResult<>(page.getList().stream().map(this::mapPageItem).toList(), page.getTotal());
}

private ShipmentDO requireShipment(Long tenantId, Long shipmentId) {
    ShipmentDO shipment = shipmentMapper.selectByIdAndTenantId(shipmentId, tenantId);
    if (shipment == null) {
        throw exception(FULFILLMENT_SHIPMENT_NOT_FOUND);
    }
    return shipment;
}

static String maskIdentifier(String value) {
    if (value == null || value.isBlank()) {
        return null;
    }
    return "***" + value.substring(Math.max(0, value.length() - 4));
}
```

Map packages and legs with `maskIdentifier` before storing values in response objects. `mapTrackingEvent` must use only `getProviderStatusNormalized()` and omit `getProviderStatus()`. Preserve the mapper-defined order: item/package by ID, leg by sequence then ID, event by occurred time then ID.

- [ ] **Step 6: Run 8A tests and existing persistence regression**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=FulfillmentQueryServiceImplTest,FulfillmentPersistenceTest,FulfillmentTrackingTransactionTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: BUILD SUCCESS; all query tenant-isolation, ordering and masking cases pass.

- [ ] **Step 7: Review and commit 8A independently**

```powershell
git diff --check
git diff --name-only
git add -- "yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/admin/fulfillment/vo" "yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment/FulfillmentQueryService.java" "yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/fulfillment/FulfillmentQueryServiceImpl.java" "yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/dal/mysql/fulfillment/ShipmentMapper.java" "yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/fulfillment/FulfillmentQueryServiceImplTest.java"
git commit -m "feat: add fulfillment query read models"
```

8A acceptance:

- Real H2 tests prove tenant isolation; Mockito-only verification is insufficient.
- Cross-tenant and nonexistent parent use the same not-found error.
- Response VO types contain no raw logistics or audit fields.
- `maskIdentifier` passes null, blank, short, four-character and long-value cases.
- Query methods produce no writes and do not call provider adapters.

---

### Task 8B: Expose the guarded controller and server-owned context mapping

**Interfaces:**

- Consumes: 8A `FulfillmentQueryService`; existing `FulfillmentCommandService`; Task 7B `FulfillmentTrackingService.applyManualEvent(String, ApplyManualTrackingEventCommand)`.
- Produces: the fixed nine endpoint mappings, six guarded write endpoints, request validation contract and safe `TrackingApplyRespVO`.

- [ ] **Step 1: Write failing controller metadata, validation and mapping tests**

Create `TradeFulfillmentControllerTest`. Use a real Jakarta `Validator`, Mockito mocks for the three services, `TenantContextHolder`, and a `LoginUser` installed with `SecurityFrameworkUtils.setLoginUser(loginUser, request)`. Add these exact test groups:

```java
@Test void exposesExactlyNineMappingsAndFivePermissions()
@Test void everyWriteMappingRequiresIdempotencyKeyAndDisablesRequestLogging()
@Test void getMappingsDoNotRequireIdempotencyKey()
@Test void createMapsTenantFromContextAndHasNoExpectedVersion()
@Test void packageAndLegOverrideTenantAndShipmentFromServerContext()
@Test void readyAndDispatchRequireNonNegativeVersion()
@Test void manualEventMapsOperatorTraceAndPathShipmentFromServerContext()
@Test void manualEventFailsClosedWithoutLoginUser()
@Test void mutationResponsesKeepExistingLongBooleanAndTrackingResultShapes()
@Test void requestAndResponseTypesExposeNoForbiddenProperties()
```

Reflection must enumerate every method carrying `@PostMapping` or `@PutMapping` and assert:

```text
count = 6
each has @PreAuthorize with the exact table value in this plan
each has @ApiAccessLog and requestEnable() == false
each has exactly one @RequestHeader(name="Idempotency-Key") parameter
the header parameter has @NotBlank and @Size(max=128)
```

Reflection must enumerate the three `@GetMapping` methods and prove none has `@RequestHeader`. Validate ID-key lengths blank/128/129 with `ExecutableValidator` or MockMvc. Validate all VO boundaries, including:

```text
manual reason: blank, 4 -> invalid; 5, 500 -> valid; 501 -> invalid
country: US, CA -> valid; CN, USA -> invalid
packageNo: 32 valid, 33 invalid
tracking/PRO/BOL/serviceLevel: 64 valid, 65 invalid
location: 256 valid, 257 invalid
timezone: 64 valid, 65 invalid
expectedVersion: 0 valid, -1 invalid
quantity: 0 invalid; 0.000001 valid; fraction > 6 invalid
weight/dimensions: 0 valid, negative invalid; fraction > 6 invalid
nested create item violation is detected through @Valid
```

Prohibited request property lists:

```java
assertNoProperties(ShipmentCreateReqVO.class,
        "tenantId", "shipmentId", "shipmentNo", "status", "version", "creator", "updater");
assertNoProperties(ShipmentPackageCreateReqVO.class,
        "tenantId", "shipmentId", "status", "creator", "updater");
assertNoProperties(ShipmentLegCreateReqVO.class,
        "tenantId", "shipmentId", "status", "creator", "updater");
assertNoProperties(ManualTrackingEventReqVO.class,
        "tenantId", "shipmentId", "operatorId", "requestTraceId", "providerId", "carrierId",
        "trackingNumber", "proNumber", "bolNumber", "priority", "mappingVersion",
        "rawPayloadRef", "externalEventId", "outboxStatus", "credential");
```

The manual command capture must prove `tenantId`, `shipmentId`, `operatorId`, and nonblank trace come from the server, while `packageId` remains optional. Add one test with an active trace and one with blank trace; blank trace must produce a UUID-length value of 36.

- [ ] **Step 2: Run the controller test and verify RED**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=TradeFulfillmentControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: FAIL at test compilation because the controller and request VO classes do not exist.

- [ ] **Step 3: Create request VO classes with exact validation**

All request VOs use `@Data` and `@Schema`. Implement these exact fields and annotations:

```java
// ShipmentCreateReqVO
@NotNull @Positive private Long orderId;
@NotNull private ShipmentTypeEnum shipmentType;
@NotBlank @Pattern(regexp = "US|CA") @Size(max = 2) private String originCountry;
@NotBlank @Pattern(regexp = "US|CA") @Size(max = 2) private String destinationCountry;
@NotBlank @Size(max = 64) private String originTimezone;
@NotBlank @Size(max = 64) private String destinationTimezone;
@NotNull @Positive private Long warehouseId;
@Positive private Long providerId;
@NotEmpty @Valid private List<ShipmentCreateItemReqVO> items;

// ShipmentCreateItemReqVO
@NotNull @Positive private Long orderItemId;
@NotNull @Positive private Long skuId;
@NotNull @DecimalMin(value = "0", inclusive = false)
@Digits(integer = 18, fraction = 6) private BigDecimal quantity;

// ShipmentPackageCreateReqVO
@NotNull @PositiveOrZero private Integer expectedVersion;
@NotBlank @Size(max = 32) private String packageNo;
@NotBlank @Pattern(regexp = "PARCEL|CARTON|PALLET|FURNITURE_ITEM")
@Size(max = 20) private String packageType;
@Positive private Long carrierId;
@Size(max = 64) @ToString.Exclude private String trackingNumber;
@PositiveOrZero @Digits(integer = 12, fraction = 6) private BigDecimal weight;
@Pattern(regexp = "LB|KG") @Size(max = 4) private String weightUnit;
@PositiveOrZero @Digits(integer = 12, fraction = 6) private BigDecimal length;
@PositiveOrZero @Digits(integer = 12, fraction = 6) private BigDecimal width;
@PositiveOrZero @Digits(integer = 12, fraction = 6) private BigDecimal height;
@Pattern(regexp = "IN|CM") @Size(max = 4) private String dimensionUnit;

// ShipmentLegCreateReqVO
@NotNull @PositiveOrZero private Integer expectedVersion;
@Positive private Long packageId;
@NotNull @Min(1) private Integer sequenceNo;
@NotBlank @Pattern(regexp = "FIRST_MILE|LINEHAUL|LAST_MILE")
@Size(max = 20) private String legType;
@NotNull @Positive private Long carrierId;
@NotNull @Positive private Long providerId;
@Size(max = 64) private String serviceLevel;
@Size(max = 64) @ToString.Exclude private String trackingNumber;
@Size(max = 64) @ToString.Exclude private String proNumber;
@Size(max = 64) @ToString.Exclude private String bolNumber;
@Size(max = 256) @ToString.Exclude private String originLocation;
@Size(max = 256) @ToString.Exclude private String destinationLocation;

// ShipmentVersionReqVO
@NotNull @PositiveOrZero private Integer expectedVersion;

// ManualTrackingEventReqVO
@Positive private Long packageId;
@NotNull @Positive private Long shipmentLegId;
@NotNull private ShipmentStatusEnum requestedStatus;
@NotNull private Instant occurredAt;
@NotNull @PositiveOrZero private Integer expectedVersion;
@NotBlank @Size(min = 5, max = 500) @ToString.Exclude
@Schema(description = "人工修正原因；禁止填写地址、电话、姓名等个人信息")
private String reason;
```

The `@Pattern` fields remain nullable unless also annotated `@NotBlank`; service validation owns unit/value combination checks and domestic US/CA/IANA business rules.

Create `TrackingApplyRespVO` with only:

```java
private Boolean inserted;
private Boolean stateChanged;
private String previousStatus;
private String currentStatus;
```

- [ ] **Step 4: Implement explicit request-to-command mapping**

Do not deserialize or copy server-owned fields from request objects. Add private mapping methods whose final setters are server values:

```java
private CreateShipmentCommand toCreateCommand(Long tenantId, ShipmentCreateReqVO reqVO) {
    return new CreateShipmentCommand()
            .setTenantId(tenantId)
            .setOrderId(reqVO.getOrderId())
            .setShipmentType(reqVO.getShipmentType())
            .setOriginCountry(reqVO.getOriginCountry())
            .setDestinationCountry(reqVO.getDestinationCountry())
            .setOriginTimezone(reqVO.getOriginTimezone())
            .setDestinationTimezone(reqVO.getDestinationTimezone())
            .setWarehouseId(reqVO.getWarehouseId())
            .setProviderId(reqVO.getProviderId())
            .setItems(reqVO.getItems().stream().map(item -> new CreateShipmentItemCommand()
                    .setOrderItemId(item.getOrderItemId())
                    .setSkuId(item.getSkuId())
                    .setQuantity(item.getQuantity())).toList());
}

private String currentTraceId() {
    String traceId = TracerUtils.getTraceId();
    if (traceId == null || traceId.isBlank()) {
        return UUID.randomUUID().toString();
    }
    return traceId.length() <= 64 ? traceId : traceId.substring(0, 64);
}
```

Package and leg mapping must copy their business fields, then set `.setTenantId(tenantId)` and `.setShipmentId(pathId)`. Dispatch maps only tenant/path shipment/expected version. Manual mapping calls `SecurityFrameworkUtils.getLoginUserId()` and fails before calling the service if it is null:

```java
Long operatorId = SecurityFrameworkUtils.getLoginUserId();
if (operatorId == null) {
    throw new IllegalStateException("login user is required");
}
ApplyManualTrackingEventCommand command = new ApplyManualTrackingEventCommand()
        .setPackageId(reqVO.getPackageId())
        .setShipmentLegId(reqVO.getShipmentLegId())
        .setRequestedStatus(reqVO.getRequestedStatus())
        .setOccurredAt(reqVO.getOccurredAt())
        .setExpectedShipmentVersion(reqVO.getExpectedVersion())
        .setReason(reqVO.getReason())
        .setTenantId(tenantId)
        .setShipmentId(pathId)
        .setOperatorId(operatorId)
        .setRequestTraceId(currentTraceId());
```

HTTP property `expectedVersion` must map to the integrated Task 7B command setter `setExpectedShipmentVersion(reqVO.getExpectedVersion())`; do not rename the HTTP property.

- [ ] **Step 5: Implement the nine-method controller**

Create the class with:

```java
@Tag(name = "管理后台 - 北美履约")
@RestController
@RequestMapping("/trade/fulfillment/shipments")
@Validated
public class TradeFulfillmentController {
    @Resource private FulfillmentCommandService commandService;
    @Resource private FulfillmentTrackingService trackingService;
    @Resource private FulfillmentQueryService queryService;
}
```

Every write method declares the same header parameter:

```java
@RequestHeader(name = "Idempotency-Key")
@NotBlank
@Size(max = 128)
String idempotencyKey
```

Implement method signatures and results exactly:

```java
@PostMapping
@PreAuthorize("@ss.hasPermission('trade:fulfillment:shipment:create')")
@ApiAccessLog(requestEnable = false)
public CommonResult<Long> createShipment(String idempotencyKey,
        @Valid @RequestBody ShipmentCreateReqVO reqVO) {
    Long tenantId = TenantContextHolder.getRequiredTenantId();
    return success(commandService.createShipment(idempotencyKey, toCreateCommand(tenantId, reqVO)));
}

@PutMapping("/{id}/ready")
@PreAuthorize("@ss.hasPermission('trade:fulfillment:shipment:update')")
@ApiAccessLog(requestEnable = false)
public CommonResult<Boolean> markReady(String idempotencyKey, @PathVariable("id") Long id,
        @Valid @RequestBody ShipmentVersionReqVO reqVO) {
    commandService.markReady(idempotencyKey, TenantContextHolder.getRequiredTenantId(),
            id, reqVO.getExpectedVersion());
    return success(true);
}

@PostMapping("/{id}/packages")
@PreAuthorize("@ss.hasPermission('trade:fulfillment:shipment:update')")
@ApiAccessLog(requestEnable = false)
public CommonResult<Long> addPackage(String idempotencyKey, @PathVariable("id") Long id,
        @Valid @RequestBody ShipmentPackageCreateReqVO reqVO)

@PostMapping("/{id}/legs")
@PreAuthorize("@ss.hasPermission('trade:fulfillment:shipment:update')")
@ApiAccessLog(requestEnable = false)
public CommonResult<Long> addLeg(String idempotencyKey, @PathVariable("id") Long id,
        @Valid @RequestBody ShipmentLegCreateReqVO reqVO)

@PostMapping("/{id}/dispatch")
@PreAuthorize("@ss.hasPermission('trade:fulfillment:shipment:dispatch')")
@ApiAccessLog(requestEnable = false)
public CommonResult<Boolean> dispatch(String idempotencyKey, @PathVariable("id") Long id,
        @Valid @RequestBody ShipmentVersionReqVO reqVO)

@PostMapping("/{id}/manual-event")
@PreAuthorize("@ss.hasPermission('trade:fulfillment:tracking:manual')")
@ApiAccessLog(requestEnable = false)
public CommonResult<TrackingApplyRespVO> applyManualEvent(String idempotencyKey,
        @PathVariable("id") Long id, @Valid @RequestBody ManualTrackingEventReqVO reqVO)

@GetMapping("/{id}")
@PreAuthorize("@ss.hasPermission('trade:fulfillment:shipment:query')")
public CommonResult<ShipmentDetailRespVO> getShipment(@PathVariable("id") Long id)

@GetMapping("/{id}/timeline")
@PreAuthorize("@ss.hasPermission('trade:fulfillment:shipment:query')")
public CommonResult<List<TrackingEventRespVO>> getTimeline(@PathVariable("id") Long id)

@GetMapping("/page")
@PreAuthorize("@ss.hasPermission('trade:fulfillment:shipment:query')")
public CommonResult<PageResult<ShipmentPageItemRespVO>> getShipmentPage(
        @Valid ShipmentPageReqVO reqVO)
```

In the actual Java declarations, place the header annotations shown above directly on each `idempotencyKey` parameter. All three GET methods call `TenantContextHolder.getRequiredTenantId()` and pass tenant explicitly to `queryService`.

Map `TrackingApplyResult` directly:

```java
TrackingApplyResult result = trackingService.applyManualEvent(idempotencyKey, command);
TrackingApplyRespVO response = new TrackingApplyRespVO();
response.setInserted(result.inserted());
response.setStateChanged(result.stateChanged());
response.setPreviousStatus(result.previousStatus());
response.setCurrentStatus(result.currentStatus());
return success(response);
```

Do not log request objects, commands, raw header values, tracking identifiers, locations, manual reason or trace.

- [ ] **Step 6: Run controller and full command/tracking regression**

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=TradeFulfillmentControllerTest,FulfillmentQueryServiceImplTest,FulfillmentCommandServiceImplTest,FulfillmentCommandTransactionTest,FulfillmentCommandAtomicityTest,FulfillmentDispatchServiceTest,FulfillmentDispatchTransactionTest,FulfillmentTrackingServiceImplTest,FulfillmentTrackingTransactionTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Task 7B manual cases are integrated into `FulfillmentTrackingServiceImplTest` and `FulfillmentTrackingTransactionTest`, so this command covers them. Expected: BUILD SUCCESS with no authorization-free write method.

- [ ] **Step 7: Review and commit 8B independently**

```powershell
git diff --check
git diff --name-only
git add -- "yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/admin/fulfillment" "yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/controller/admin/fulfillment/TradeFulfillmentControllerTest.java"
git commit -m "feat: expose guarded fulfillment admin APIs"
```

8B acceptance:

- Exactly nine endpoints exist; exactly six are writes and all six have permission, idempotency header validation and disabled request-body access logging.
- There are exactly five distinct permission strings matching the fixed contract.
- tenant/path shipment/operator/trace cannot be supplied from JSON.
- Missing manual operator fails before calling tracking service; blank trace is replaced by a nonblank UUID.
- create has no expected version; all other writes require nonnegative expected version.
- Mutation responses retain existing service shapes and never invent shipment version.
- Swagger text warns that manual reason must not contain name, phone or address.

---

### Task 8C: Append V019 RBAC seeds and regenerate the deterministic baseline

**Interfaces:**

- Consumes: immutable V001-V018 catalog, `system_menu` schema, existing parent order-list menu `id=2076`, baseline generator.
- Produces: five assignable type=3 permissions without assigning any role.

- [ ] **Step 1: Write the failing V019/RBAC migration test**

Create `databaseFulfillmentPermissionsMigration.test.js`:

```js
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

const root = join(import.meta.dirname, "../../yudao电商管理平台前后端/yudao-cloud/sql/mysql");
const migrationPath = join(root, "migrations/V019__trade_fulfillment_admin_permissions.sql");
const baselinePath = join(root, "oakved-baseline.sql");
const permissions = [
  "trade:fulfillment:shipment:query",
  "trade:fulfillment:shipment:create",
  "trade:fulfillment:shipment:update",
  "trade:fulfillment:shipment:dispatch",
  "trade:fulfillment:tracking:manual",
];

describe("V019 fulfillment admin permission migration", () => {
  it("adds five idempotent button permissions under the order list", () => {
    const sql = readFileSync(migrationPath, "utf8");
    for (const permission of permissions) {
      expect(sql).toContain(`'${permission}'`);
    }
    expect(sql.match(/INSERT INTO `system_menu`/g)).toHaveLength(5);
    expect(sql.match(/SELECT 1 FROM `system_menu` WHERE `id` = 2076 AND `deleted` = b'0'/g))
      .toHaveLength(5);
    expect(sql.match(/`type`, `sort`, `parent_id`/g)).toHaveLength(5);
    expect(sql).not.toMatch(/INSERT INTO `system_role_menu`|role_id|tenant_id|credential|secret|api[_-]?key/i);
  });

  it("keeps the generated baseline V019 section byte-equivalent", () => {
    const migration = readFileSync(migrationPath, "utf8").replace(/\r\n/g, "\n").replace(/\s+$/, "") + "\n";
    const baseline = readFileSync(baselinePath, "utf8").replace(/\r\n/g, "\n");
    const marker = "-- BEGIN V019__trade_fulfillment_admin_permissions.sql\n";
    const start = baseline.indexOf(marker) + marker.length;
    const end = baseline.indexOf("\n-- BEGIN Oakved demo catalog", start);
    expect(start).toBeGreaterThan(marker.length - 1);
    expect(end).toBeGreaterThan(start);
    expect(baseline.slice(start, end).replace(/\s+$/, "") + "\n").toBe(migration);
  });
});
```

Update the existing catalog tests from length 18 to 19 and last file V019. Update the V018 baseline section test so its end marker is `\n-- BEGIN V019__trade_fulfillment_admin_permissions.sql` instead of the demo catalog marker.

- [ ] **Step 2: Run Node tests and verify RED**

Run from `D:\code\furniture web`:

```powershell
npm.cmd test -- databaseFulfillmentPermissionsMigration.test.js databaseFulfillmentMigration.test.js databaseSafetyWorkflow.test.js dbMigrations.test.js
```

Expected: FAIL because V019 does not exist and the catalog still ends at V018.

- [ ] **Step 3: Create V019 with five repeat-safe menu inserts**

Use this column list for every row; omit `id` so MySQL auto increment assigns it:

```sql
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`,
 `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
```

Write five separate `INSERT INTO system_menu`/`SELECT` statements using these rows:

```text
('履约查询', 'trade:fulfillment:shipment:query',    3, 1, 2076)
('履约创建', 'trade:fulfillment:shipment:create',   3, 2, 2076)
('履约修改', 'trade:fulfillment:shipment:update',   3, 3, 2076)
('履约交运', 'trade:fulfillment:shipment:dispatch', 3, 4, 2076)
('人工轨迹', 'trade:fulfillment:tracking:manual',   3, 5, 2076)
```

Each statement uses this complete form, substituting only name, permission and sort:

```sql
INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`,
 `component`, `component_name`, `status`, `visible`, `keep_alive`,
 `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '履约查询', 'trade:fulfillment:shipment:query', 3, 1, 2076,
       '', '', '', NULL, 0, b'1', b'1', b'1',
       'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP, b'0'
FROM DUAL
WHERE EXISTS (
    SELECT 1 FROM `system_menu` WHERE `id` = 2076 AND `deleted` = b'0'
)
AND NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `permission` = 'trade:fulfillment:shipment:query' AND `deleted` = b'0'
);
```

Do not insert a page/menu node and do not insert into `system_role_menu`. Manual permission remains unassigned until an administrator explicitly grants it to a high-privilege operations role.

- [ ] **Step 4: Regenerate baseline and update catalog expectations**

Run from `D:\code\furniture web`:

```powershell
npm.cmd run build:db-baseline
```

Expected output ends with:

```text
Generated D:\code\yudao电商管理平台前后端\yudao-cloud\sql\mysql\oakved-baseline.sql with 19 migrations.
```

In both `dbMigrations.test.js` and `databaseSafetyWorkflow.test.js`, use:

```js
const expectedVersions = Array.from({ length: 19 }, (_, index) => index + 1);
```

In both `dbMigrations.test.js` and `databaseSafetyWorkflow.test.js`, assert the exact last filename `V019__trade_fulfillment_admin_permissions.sql`. Preserve the V015-V018 immutability checks.

- [ ] **Step 5: Run RBAC and database safety tests**

```powershell
npm.cmd test -- databaseFulfillmentPermissionsMigration.test.js databaseFulfillmentMigration.test.js databaseSafetyWorkflow.test.js dbMigrations.test.js
npm.cmd run verify:db-migrations
```

Expected: all four Vitest files PASS and migration verification reports versions 1 through 19 with no baseline/checksum errors.

- [ ] **Step 6: Review and commit 8C independently**

```powershell
git diff --check
git diff --name-only
git add -- "furniture web/tests/databaseFulfillmentPermissionsMigration.test.js" "furniture web/tests/databaseFulfillmentMigration.test.js" "furniture web/tests/databaseSafetyWorkflow.test.js" "furniture web/tests/dbMigrations.test.js" "yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V019__trade_fulfillment_admin_permissions.sql" "yudao电商管理平台前后端/yudao-cloud/sql/mysql/oakved-baseline.sql"
git commit -m "feat: seed fulfillment admin permissions"
```

8C acceptance:

- Catalog is contiguous V001-V019 and V015-V018 bytes are unchanged.
- Five permissions appear exactly once as intended type=3 children of parent 2076.
- Inserts are repeat-safe and conditional on an existing nondeleted parent.
- No explicit new menu IDs, role grants, tenant IDs, accounts, credentials or secrets are present.
- Baseline V018 ends at V019 marker; V019 ends at the demo catalog marker and is byte-equivalent to its migration.

---

## Final Cross-Task Verification

- [ ] **Step 1: Run the complete Task 8 Node suite**

From `D:\code\furniture web`:

```powershell
npm.cmd test -- databaseFulfillmentPermissionsMigration.test.js databaseFulfillmentMigration.test.js databaseSafetyWorkflow.test.js dbMigrations.test.js
npm.cmd run verify:db-migrations
```

- [ ] **Step 2: Run the complete fulfillment Maven suite**

From `D:\code\yudao电商管理平台前后端\yudao-cloud`:

```powershell
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=TradeFulfillmentControllerTest,FulfillmentQueryServiceImplTest,FulfillmentCommandServiceImplTest,FulfillmentCommandTransactionTest,FulfillmentCommandAtomicityTest,FulfillmentDispatchServiceTest,FulfillmentDispatchTransactionTest,FulfillmentTrackingServiceImplTest,FulfillmentTrackingTransactionTest,FulfillmentPersistenceTest,TrackingStatusMappingPersistenceTest,ShipmentStateMachineTest,LogisticsProviderRegistryTest,TradeOrderUpdateServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Task 7B manual service and transaction cases remain in `FulfillmentTrackingServiceImplTest` and `FulfillmentTrackingTransactionTest`, already present in this list. Expected: BUILD SUCCESS and zero test failures/errors.

- [ ] **Step 3: Run structural and sensitive-boundary checks**

```powershell
rg -n "@(PostMapping|PutMapping)|@PreAuthorize|@ApiAccessLog" "yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/admin/fulfillment/TradeFulfillmentController.java"
rg -n "tenantId|shipmentId|operatorId|requestTraceId|rawPayloadRef|externalEventId|outboxStatus|credential" "yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/admin/fulfillment/vo"
rg -n "system_role_menu|role_id|tenant_id|credential|secret|api[_-]?key" "sql/mysql/migrations/V019__trade_fulfillment_admin_permissions.sql"
git diff --check
git status --short
```

Expected:

- first search shows six writes, six exact permissions/access-log annotations, plus three query mappings;
- second search returns no forbidden request/response field declarations;
- third search returns no matches;
- `git diff --check` is clean;
- status contains only intended Task 8 changes plus pre-existing unrelated user changes.

## Final Acceptance Gate

- Nine and only nine management endpoints are exposed under the framework-added `/admin-api` prefix.
- All writes are authenticated by exact RBAC permission, require `Idempotency-Key`, and suppress request-body access logging.
- The five permissions are assignable through RBAC but no role receives them automatically.
- Every endpoint derives tenant from server context; manual operator and trace are server-owned and fail/fallback safely.
- Detail/page/timeline prove tenant isolation with H2 and do not use row locks or provider calls.
- Raw tracking/PRO/BOL/location/provider/manual audit/idempotency fields never appear in ordinary API responses.
- Write responses do not claim a synthetic version; callers must GET detail and reuse its current version.
- Task 6B, Task 7 and Task 7B command/tracking/transaction tests remain green.
