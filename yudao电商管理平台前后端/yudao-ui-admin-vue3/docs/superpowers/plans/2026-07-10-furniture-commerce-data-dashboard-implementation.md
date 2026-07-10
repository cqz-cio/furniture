# Furniture Commerce Data Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an independent `/dashboard` navigation entry that reports storefront traffic, product detail views, paid orders, conversion, revenue, refunds, cost and gross profit with daily filtering and product-level drill-down.

**Architecture:** Extend the existing `yudao-module-statistics-server` instead of creating a new service. The furniture Vue app emits anonymous-safe behavior events; the statistics service stores raw events and materializes site/product daily aggregates; trade order items snapshot SKU cost; the Vue 3 admin consumes summary, trend, funnel and product-page APIs.

**Tech Stack:** Java 17, Spring Boot/Spring Cloud, MyBatis Plus, MySQL 8, XXL Job, Redis rate limiting, Vue 3, Element Plus, ECharts 6, Axios, Vite, Vitest.

## Global Constraints

- Use `Asia/Shanghai` and half-open date ranges `[start, end)`.
- Money is stored and transported as integer cents; percentages use two decimal places.
- Dashboard PV/UV comes only from `statistics_behavior_event`; do not mix `product_spu.browse_count` or `product_browse_history` into the new dashboard.
- Product conversion is distinct paid orders containing the SPU divided by product-detail PV.
- New orders use SKU cost captured at order creation; current SKU cost is never substituted at query time.
- Raw behavior events are retained for 180 days; daily aggregates are retained indefinitely.
- Tracking failures never block browsing, cart, checkout or order creation.
- Preserve Yudao navigation, theme and components; do not install React, Tailwind, Radix or shadcn runtime packages.
- Preserve unrelated workspace changes, especially `yudao-ui-admin-vue3/vite.config.ts`.

---

## File Structure

### Database

- Create `yudao-cloud/sql/mysql/statistics-commerce-dashboard.sql`: idempotent schema, cost backfill, jobs and menu permissions.

### Trade service

- Modify `TradePriceCalculateRespBO.java`: add `costPrice` to calculated order item.
- Modify `TradePriceCalculatorHelper.java`: copy SKU cost into calculated item.
- Modify `TradeOrderItemDO.java`: persist `costPrice` and `costEstimated`.
- Modify `TradeOrderConvertTest.java`: verify snapshot mapping.

### Statistics service

- Modify `pom.xml`: add Yudao protection/rate-limit starter.
- Modify `src/main/resources/application.yaml`: bind the behavior hash salt from `STATISTICS_BEHAVIOR_HASH_SALT`.
- Create `controller/app/dashboard/AppBehaviorEventController.java` and `controller/app/dashboard/vo/AppBehaviorEventTrackReqVO.java`.
- Create `controller/admin/dashboard/DashboardStatisticsController.java` and the exact VO files listed in Task 5.
- Create `dal/dataobject/dashboard/BehaviorEventDO.java` and `TrafficDailyDO.java`.
- Create `dal/mysql/dashboard/BehaviorEventMapper.java`, `TrafficDailyMapper.java`, `DashboardAggregationMapper.java`, `DashboardQueryMapper.java` and their two XML query mappers.
- Modify `ProductStatisticsDO.java`, `ProductStatisticsMapper.java` and `ProductStatisticsMapper.xml`: new event-based product fields and aggregate queries.
- Create the `BehaviorEventService`, `DashboardAggregationService` and `DashboardQueryService` interfaces and implementations listed in Tasks 3–5.
- Create `job/dashboard/DashboardStatisticsJob.java` and `DashboardBehaviorCleanupJob.java`.
- Create statistics service tests for validation, aggregation, query calculations and controllers.

### Furniture storefront

- Create `src/services/behaviorTracking.js`: IDs, suppression, event construction and fire-and-forget sending.
- Modify `src/App.vue`: home, cart and checkout events.
- Modify `src/pages/SofaPdpPage.vue`: product detail event after successful product load.
- Create `tests/behaviorTracking.test.js` and `tests/dashboardTrackingIntegration.test.js`.

### Admin frontend

- Create `src/api/mall/statistics/dashboard.ts`: typed dashboard client.
- Create `src/views/dashboard/index.vue`: page orchestration and filters.
- Create `src/views/dashboard/components/DashboardMetricCards.vue`.
- Create `src/views/dashboard/components/DashboardTrend.vue`.
- Create `src/views/dashboard/components/DashboardFunnel.vue`.
- Create `src/views/dashboard/components/DashboardProductTable.vue`.
- Modify `src/config/furnitureLite.ts`: allow `/dashboard`.
- Create `scripts/check-dashboard-contract.mjs` and add `check:dashboard` to `package.json`.

---

### Task 1: Add idempotent database migration

**Files:**
- Create: `yudao-cloud/sql/mysql/statistics-commerce-dashboard.sql`
- Create: `furniture web/tests/dashboardDatabaseMigration.test.js`

**Interfaces:**
- Produces: `statistics_behavior_event`, `statistics_traffic_daily`, extended `product_statistics`, `trade_order_item.cost_price`, menu permissions and job rows.

- [ ] **Step 1: Write the failing SQL contract test**

```js
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const sql = readFileSync(
  new URL("../../yudao电商管理平台前后端/yudao-cloud/sql/mysql/statistics-commerce-dashboard.sql", import.meta.url),
  "utf8"
);

describe("commerce dashboard migration", () => {
  it("defines event, aggregate, cost, menu and jobs", () => {
    expect(sql).toContain("statistics_behavior_event");
    expect(sql).toContain("statistics_traffic_daily");
    expect(sql).toContain("cost_price");
    expect(sql).toContain("statistics:dashboard:query");
    expect(sql).toContain("dashboardStatisticsJob");
  });
});
```

- [ ] **Step 2: Run the test and verify it fails because the SQL file is absent**

Run: `npm test -- dashboardDatabaseMigration.test.js`

Expected: FAIL with an `ENOENT` error for `statistics-commerce-dashboard.sql`.

- [ ] **Step 3: Create the migration exactly from the database design**

Use the executable DDL, backfill, menu and job definitions in `docs/superpowers/specs/2026-07-10-furniture-dashboard-database-migration.md`. For every `ALTER TABLE`, guard the column/index using `information_schema` plus prepared SQL so the complete file succeeds twice.

Required migration order is: create `statistics_behavior_event`; create `statistics_traffic_daily`; add guarded `product_statistics` columns and unique key; add guarded `trade_order_item` cost columns; backfill only null historical costs; insert jobs idempotently; insert `/dashboard` menu and permissions idempotently. Copy the complete SQL statements from the database migration design without abbreviating table definitions.

- [ ] **Step 4: Run migration checks**

Run: `npm test -- dashboardDatabaseMigration.test.js dbMigrations.test.js`

Expected: PASS, 0 failed tests.

On a disposable MySQL schema run the migration twice; both executions must exit 0 and this query must return zero duplicate groups:

```sql
SELECT tenant_id, day, COUNT(*)
FROM statistics_traffic_daily
GROUP BY tenant_id, day
HAVING COUNT(*) > 1;
```

- [ ] **Step 5: Commit**

```powershell
git add 'yudao电商管理平台前后端/yudao-cloud/sql/mysql/statistics-commerce-dashboard.sql' 'furniture web/tests/dashboardDatabaseMigration.test.js'
git commit -m "feat: add commerce dashboard database migration"
```

### Task 2: Capture order-item cost snapshots

**Files:**
- Modify: `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/price/bo/TradePriceCalculateRespBO.java`
- Modify: `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/price/calculator/TradePriceCalculatorHelper.java`
- Modify: `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/dal/dataobject/order/TradeOrderItemDO.java`
- Modify: `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/convert/order/TradeOrderConvertTest.java`

**Interfaces:**
- Consumes: `ProductSkuRespDTO.getCostPrice()`.
- Produces: `TradeOrderItemDO.getCostPrice(): Integer` and `getCostEstimated(): Boolean`.

- [ ] **Step 1: Write the failing converter test**

```java
@Test
void convertOrderItem_copiesCostSnapshot() {
    TradePriceCalculateRespBO.OrderItem source = new TradePriceCalculateRespBO.OrderItem()
            .setSpuId(20L).setSkuId(10L).setCount(2)
            .setPrice(5000).setPayPrice(10000).setCostPrice(3500);
    TradeOrderItemDO actual = TradeOrderConvert.INSTANCE.convert(source);
    assertEquals(3500, actual.getCostPrice());
    assertFalse(actual.getCostEstimated());
}
```

- [ ] **Step 2: Run the focused test and verify compilation fails on missing accessors**

Run from `yudao-cloud`:

`mvn -pl yudao-module-mall/yudao-module-trade-server -am -Dtest=TradeOrderConvertTest#convertOrderItem_copiesCostSnapshot -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because `setCostPrice`/`getCostPrice` do not exist.

- [ ] **Step 3: Add the snapshot fields and assignment**

In `TradePriceCalculateRespBO.OrderItem`:

```java
private Integer costPrice;
```

In `TradePriceCalculatorHelper.buildCalculateResp` after setting SKU price:

```java
orderItem.setPrice(sku.getPrice())
        .setCostPrice(sku.getCostPrice())
        .setPayPrice(sku.getPrice() * item.getCount())
        .setDiscountPrice(0).setDeliveryPrice(0)
        .setCouponPrice(0).setPointPrice(0).setVipPrice(0);
```

In `TradeOrderItemDO` directly after `price`:

```java
private Integer costPrice;
private Boolean costEstimated;
```

Set the default in `TradeOrderConvert.convertList` after mapping each item:

```java
orderItem.setCostEstimated(Boolean.FALSE);
```

- [ ] **Step 4: Run trade tests**

Run: `mvn -pl yudao-module-mall/yudao-module-trade-server -am -DskipITs test`

Expected: BUILD SUCCESS, 0 failures.

- [ ] **Step 5: Commit**

```powershell
git add 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server'
git commit -m "feat: snapshot sku cost on order items"
```

### Task 3: Add anonymous-safe behavior event ingestion

**Files:**
- Modify: `yudao-module-statistics-server/pom.xml`
- Create: `controller/app/dashboard/AppBehaviorEventController.java`
- Create: `controller/app/dashboard/vo/AppBehaviorEventTrackReqVO.java`
- Create: `enums/dashboard/BehaviorEventTypeEnum.java`
- Create: `enums/dashboard/DeviceTypeEnum.java`
- Create: `dal/dataobject/dashboard/BehaviorEventDO.java`
- Create: `dal/mysql/dashboard/BehaviorEventMapper.java`
- Create: `service/dashboard/BehaviorEventService.java`
- Create: `service/dashboard/BehaviorEventServiceImpl.java`
- Create: `service/dashboard/BehaviorIdentityHasher.java`
- Create: `framework/config/BehaviorTrackingProperties.java`
- Modify: `src/main/resources/application.yaml`
- Create: `src/test/java/cn/iocoder/yudao/module/statistics/service/dashboard/BehaviorEventServiceImplTest.java`

**Interfaces:**
- Produces: `void track(AppBehaviorEventTrackReqVO reqVO, Long userId)`.
- Produces: `POST /statistics/behavior/track` returning `CommonResult<Boolean>`.

- [ ] **Step 1: Write failing service tests**

```java
@Test
void track_productView_hashesIdentityAndStoresSpu() {
    AppBehaviorEventTrackReqVO req = validRequest("PRODUCT_DETAIL_VIEW")
            .setSpuId(2048L).setPagePath("/product");
    service.track(req, null);
    ArgumentCaptor<BehaviorEventDO> captor = ArgumentCaptor.forClass(BehaviorEventDO.class);
    verify(mapper).insertIgnore(captor.capture());
    assertEquals(64, captor.getValue().getVisitorHash().length());
    assertEquals(2048L, captor.getValue().getSpuId());
}

@Test
void track_addCart_requiresSkuAndPositiveQuantity() {
    AppBehaviorEventTrackReqVO req = validRequest("ADD_TO_CART").setSpuId(1L);
    assertThrows(IllegalArgumentException.class, () -> service.track(req, 9L));
}
```

- [ ] **Step 2: Run tests and verify missing-class failure**

Run: `mvn -pl yudao-module-mall/yudao-module-statistics-server -am -Dtest=BehaviorEventServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because behavior event classes do not exist.

- [ ] **Step 3: Add DTO validation and event service**

The request VO fields and exact validation rules come from `2026-07-10-furniture-dashboard-api-tracking-contract.md`. Implement the service core as:

```java
@Override
public void track(AppBehaviorEventTrackReqVO reqVO, Long userId) {
    validateEventFields(reqVO);
    LocalDateTime occurredAt = normalizeOccurredAt(reqVO.getOccurredAt(), LocalDateTime.now());
    BehaviorEventDO event = BeanUtils.toBean(reqVO, BehaviorEventDO.class)
            .setEventType(BehaviorEventTypeEnum.valueOf(reqVO.getEventType()).getType())
            .setVisitorHash(identityHasher.hash(reqVO.getVisitorId()))
            .setSessionHash(identityHasher.hash(reqVO.getSessionId()))
            .setUserId(userId)
            .setOccurredAt(occurredAt);
    behaviorEventMapper.insertIgnore(event);
}
```

`insertIgnore` must translate duplicate `(tenant_id,event_id)` to success and must rethrow other database errors.

`BehaviorIdentityHasher` computes `SHA-256(tenantId + ':' + configuredSalt + ':' + rawId)`. Add this configuration and never log the salt or raw identifier:

```yaml
yudao:
  statistics:
    behavior:
      hash-salt: ${STATISTICS_BEHAVIOR_HASH_SALT:local-only-change-before-production}
```

- [ ] **Step 4: Add public controller and IP limiter**

```java
@PostMapping("/track")
@Operation(summary = "上报家具前台行为事件")
@PermitAll
@RateLimiter(time = 60, count = 120, keyResolver = ClientIpRateLimiterKeyResolver.class)
public CommonResult<Boolean> track(@Valid @RequestBody AppBehaviorEventTrackReqVO reqVO) {
    behaviorEventService.track(reqVO, getLoginUserId());
    return success(true);
}
```

Add `yudao-spring-boot-starter-protection` to the statistics POM.

- [ ] **Step 5: Run focused and module tests**

Run: `mvn -pl yudao-module-mall/yudao-module-statistics-server -am -DskipITs test`

Expected: BUILD SUCCESS, 0 failures.

- [ ] **Step 6: Commit**

```powershell
git add 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-statistics-server'
git commit -m "feat: ingest storefront behavior events"
```

### Task 4: Materialize site and product daily statistics

**Files:**
- Create: `dal/dataobject/dashboard/TrafficDailyDO.java`
- Create: `dal/mysql/dashboard/TrafficDailyMapper.java`
- Create: `dal/mysql/dashboard/DashboardAggregationMapper.java`
- Create: `resources/mapper/dashboard/DashboardAggregationMapper.xml`
- Create: `service/dashboard/DashboardAggregationService.java`
- Create: `service/dashboard/DashboardAggregationServiceImpl.java`
- Modify: `dal/dataobject/product/ProductStatisticsDO.java`
- Modify: `dal/mysql/product/ProductStatisticsMapper.java`
- Modify: `resources/mapper/product/ProductStatisticsMapper.xml`
- Create: `src/test/java/cn/iocoder/yudao/module/statistics/service/dashboard/DashboardAggregationServiceImplTest.java`

**Interfaces:**
- Produces: `void recomputeDay(LocalDate day)` and `String recomputeRecentDays(int daysBack)`.
- Consumes: behavior events, paid orders, order items, successful after-sales and cost snapshots.

- [ ] **Step 1: Write failing aggregation tests using the fixed acceptance dataset**

Use the dataset in `2026-07-10-furniture-dashboard-test-acceptance.md` and assert:

```java
assertEquals(10, daily.getHomePv());
assertEquals(3, daily.getHomeUv());
assertEquals(3, daily.getPaidOrderCount());
assertEquals(23000L, daily.getPaidRevenue());
assertEquals(6000L, daily.getRefundAmount());
assertEquals(10000L, daily.getCostAmount());
assertEquals(7000L, daily.getGrossProfit());
```

Add a second test that calls `recomputeDay` twice and verifies exactly one tenant/day row and unchanged values.

- [ ] **Step 2: Run and verify missing-service failure**

Run: `mvn -pl yudao-module-mall/yudao-module-statistics-server -am -Dtest=DashboardAggregationServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because aggregation classes do not exist.

- [ ] **Step 3: Add DO fields and aggregate SQL**

Add to `ProductStatisticsDO`:

```java
import java.math.BigDecimal;

private Integer cartUserCount;
private Integer paidOrderCount;
private Integer paidBuyerCount;
private Long costAmount;
private Long grossProfit;
private BigDecimal grossMarginPercent;
private Integer estimatedCostItemCount;
private Integer missingCostItemCount;
```

The XML uses grouped subqueries, never one correlated subquery per SPU. Revenue and cost rules must implement:

```text
netRevenue = paidRevenue - successfulRefundAmount
costAmount = paid item cost - cost of returned-and-restocked quantity
grossProfit = netRevenue - costAmount
```

Only-return refunds reduce revenue but do not reduce cost. Missing cost contributes to `missingCostItemCount` and never becomes numeric zero cost.

- [ ] **Step 4: Implement transactional delete-and-rebuild**

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void recomputeDay(LocalDate day) {
    LocalDateTime begin = day.atStartOfDay();
    LocalDateTime end = day.plusDays(1).atStartOfDay();
    trafficDailyMapper.deleteByDay(day);
    productStatisticsMapper.deleteByDay(day);
    trafficDailyMapper.insert(aggregationMapper.selectTrafficDaily(begin, end));
    productStatisticsMapper.insertBatch(aggregationMapper.selectProductDaily(begin, end));
}
```

Wrap it with the tenant/day Redis lock described in the database design.

`recomputeRecentDays` has non-ambiguous job semantics:

```java
public String recomputeRecentDays(int daysBack) {
    DashboardAggregationServiceImpl self = getSelf();
    if (daysBack == 0) {
        self.recomputeDay(LocalDate.now());
        return "重算当天完成";
    }
    IntStream.rangeClosed(1, daysBack)
            .mapToObj(offset -> LocalDate.now().minusDays(offset))
            .forEach(self::recomputeDay);
    return "重算最近" + daysBack + "个完整自然日完成";
}

private DashboardAggregationServiceImpl getSelf() {
    return SpringUtil.getBean(getClass());
}
```

Calling through `getSelf()` is required so each `recomputeDay` executes through the Spring transaction proxy instead of bypassing `@Transactional` through self-invocation.

- [ ] **Step 5: Run tests**

Run: `mvn -pl yudao-module-mall/yudao-module-statistics-server -am -DskipITs test`

Expected: BUILD SUCCESS and fixed dataset values match exactly.

- [ ] **Step 6: Commit**

```powershell
git add 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-statistics-server'
git commit -m "feat: aggregate commerce dashboard metrics"
```

### Task 5: Add dashboard query, export and scheduled jobs

**Files:**
- Create: `controller/admin/dashboard/DashboardStatisticsController.java`
- Create: `controller/admin/dashboard/vo/DashboardQueryReqVO.java`
- Create: `controller/admin/dashboard/vo/DashboardSummaryRespVO.java`
- Create: `controller/admin/dashboard/vo/DashboardTrendRespVO.java`
- Create: `controller/admin/dashboard/vo/DashboardFunnelRespVO.java`
- Create: `controller/admin/dashboard/vo/DashboardProductPageReqVO.java`
- Create: `controller/admin/dashboard/vo/DashboardProductRespVO.java`
- Create: `controller/admin/dashboard/vo/DashboardProductExcelVO.java`
- Create: `enums/dashboard/ProfitDataQualityEnum.java`
- Create: `service/dashboard/DashboardQueryService.java`
- Create: `service/dashboard/DashboardQueryServiceImpl.java`
- Create: `dal/mysql/dashboard/DashboardQueryMapper.java`
- Create: `resources/mapper/dashboard/DashboardQueryMapper.xml`
- Create: `job/dashboard/DashboardStatisticsJob.java`
- Create: `job/dashboard/DashboardBehaviorCleanupJob.java`
- Create: `src/test/java/cn/iocoder/yudao/module/statistics/service/dashboard/DashboardQueryServiceImplTest.java`
- Create: `src/test/java/cn/iocoder/yudao/module/statistics/controller/admin/dashboard/DashboardStatisticsControllerTest.java`

**Interfaces:**
- Produces the five admin endpoints defined in the API contract.
- Produces XXL handlers `dashboardStatisticsJob` and `dashboardBehaviorCleanupJob`.

- [ ] **Step 1: Write failing calculation and controller tests**

```java
@Test
void summary_usesPaidOrdersOverPv() {
    DashboardSummaryRespVO result = service.getSummary(query).getValue();
    assertEquals(new BigDecimal("30.00"), result.getSitePvConversionPercent());
    assertEquals(new BigDecimal("41.18"), result.getGrossMarginPercent());
}

@Test
void trend_fillsMissingCalendarDays() {
    assertEquals(List.of(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2)),
            service.getTrend(twoDayQuery).stream().map(DashboardTrendRespVO::getDay).toList());
}
```

- [ ] **Step 2: Run tests and verify missing-class failure**

Run: `mvn -pl yudao-module-mall/yudao-module-statistics-server -am -Dtest=DashboardQueryServiceImplTest,DashboardStatisticsControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because query/controller classes do not exist.

- [ ] **Step 3: Implement query service and quality calculation**

```java
private ProfitDataQualityEnum resolveQuality(int exact, int estimated, int missing) {
    if (missing > 0) return ProfitDataQualityEnum.INCOMPLETE;
    if (exact > 0 && estimated > 0) return ProfitDataQualityEnum.MIXED;
    if (estimated > 0) return ProfitDataQualityEnum.ESTIMATED;
    return ProfitDataQualityEnum.EXACT;
}

private BigDecimal percent(long numerator, long denominator) {
    if (denominator == 0) return BigDecimal.ZERO.setScale(2);
    return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
}
```

Validate sorting fields through an enum map; never concatenate an unchecked request field into SQL.

- [ ] **Step 4: Implement controllers and jobs**

Controller mappings and permissions must exactly match the API contract. Use `ExcelUtils.write` for export and `@TenantJob` on both handlers.

```java
@XxlJob("dashboardStatisticsJob")
@TenantJob
public String aggregate(String param) {
    int daysBack = Convert.toInt(ObjUtil.defaultIfBlank(param, "0"));
    Assert.isTrue(daysBack >= 0 && daysBack <= 366, "重算天数必须在0到366之间");
    return aggregationService.recomputeRecentDays(daysBack);
}
```

- [ ] **Step 5: Run module tests**

Run: `mvn -pl yudao-module-mall/yudao-module-statistics-server -am -DskipITs test`

Expected: BUILD SUCCESS, controller permissions and calculations pass.

- [ ] **Step 6: Commit**

```powershell
git add 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-statistics-server'
git commit -m "feat: expose commerce dashboard statistics"
```

### Task 6: Add storefront tracking client and event hooks

**Files:**
- Create: `furniture web/src/services/behaviorTracking.js`
- Modify: `furniture web/src/App.vue`
- Modify: `furniture web/src/pages/SofaPdpPage.vue`
- Create: `furniture web/tests/behaviorTracking.test.js`
- Create: `furniture web/tests/dashboardTrackingIntegration.test.js`

**Interfaces:**
- Produces: `trackHomeView()`, `trackProductDetailView(product)`, `trackAddToCart(product, quantity)`, `trackCheckoutStart()`.

- [ ] **Step 1: Write failing tracking unit tests**

```js
it("suppresses identical events for five seconds", () => {
  const first = buildTrackableEvent("HOME_VIEW", { pagePath: "/" }, 1000);
  const second = buildTrackableEvent("HOME_VIEW", { pagePath: "/" }, 4000);
  expect(first).toBeTruthy();
  expect(second).toBeNull();
});

it("does not throw when fetch rejects", async () => {
  globalThis.fetch = vi.fn().mockRejectedValue(new Error("offline"));
  expect(() => trackHomeView()).not.toThrow();
  await Promise.resolve();
});
```

- [ ] **Step 2: Run tests and verify missing-module failure**

Run: `npm test -- behaviorTracking.test.js dashboardTrackingIntegration.test.js`

Expected: FAIL because `behaviorTracking.js` does not exist.

- [ ] **Step 3: Implement the tracking service**

```js
const VISITOR_KEY = "oakved_visitor_id";
const SESSION_KEY = "oakved_session_id";
const suppression = new Map();

export const trackBehavior = (eventType, payload = {}) => {
  const event = buildTrackableEvent(eventType, payload, Date.now());
  if (!event) return false;
  sendBehaviorEvent(event);
  return true;
};
```

Use `crypto.randomUUID()`, normalize the path, send only `document.referrer` host, and never store authentication tokens in an event. `sendBehaviorEvent` calls `requestYudao` with `keepalive: true`; this preserves tenant and optional member authorization headers while remaining fire-and-forget.

- [ ] **Step 4: Wire events to successful business moments**

In `App.vue`:

- watch the route signature with `{ immediate: true }` and call `trackHomeView()` only when normalized path is `/`;
- call `trackAddToCart(product, quantity)` immediately after remote `addCartItem` succeeds;
- do not call it in the local preview branch;
- call `trackCheckoutStart()` after cart validation and immediately before navigation.

In `SofaPdpPage.vue`, call `trackProductDetailView(product.value)` after `getProductDetail` resolves to a valid Yudao product. The component key already changes with route signature, so each real route entry can create one event subject to 5-second suppression.

- [ ] **Step 5: Run storefront verification**

Run:

```powershell
npm test -- behaviorTracking.test.js dashboardTrackingIntegration.test.js
npm test
npm run build
```

Expected: all Vitest tests pass and Vite build exits 0.

- [ ] **Step 6: Commit**

```powershell
git add 'furniture web/src/services/behaviorTracking.js' 'furniture web/src/App.vue' 'furniture web/src/pages/SofaPdpPage.vue' 'furniture web/tests/behaviorTracking.test.js' 'furniture web/tests/dashboardTrackingIntegration.test.js'
git commit -m "feat: track storefront commerce behavior"
```

### Task 7: Add typed admin API and route contract

**Files:**
- Create: `yudao-ui-admin-vue3/src/api/mall/statistics/dashboard.ts`
- Modify: `yudao-ui-admin-vue3/src/config/furnitureLite.ts`
- Create: `yudao-ui-admin-vue3/scripts/check-dashboard-contract.mjs`
- Modify: `yudao-ui-admin-vue3/package.json`

**Interfaces:**
- Produces: `DashboardStatisticsApi.getSummary/getTrend/getFunnel/getProductPage/exportProductExcel`.

- [ ] **Step 1: Create a failing source-contract script**

```js
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const config = readFileSync(new URL('../src/config/furnitureLite.ts', import.meta.url), 'utf8')
const api = readFileSync(new URL('../src/api/mall/statistics/dashboard.ts', import.meta.url), 'utf8')
assert.match(config, /['"]\/dashboard['"]/, 'furniture-lite must allow /dashboard')
for (const field of ['homePv', 'productDetailPv', 'paidOrderCount', 'grossProfit']) {
  assert.match(api, new RegExp(field), `dashboard API is missing ${field}`)
}
```

Add `"check:dashboard": "node scripts/check-dashboard-contract.mjs"` to `package.json`.

- [ ] **Step 2: Run and verify failure**

Run: `pnpm check:dashboard`

Expected: FAIL because API file and `/dashboard` allow-list entry do not exist.

- [ ] **Step 3: Add API types and client methods**

Define types exactly from the API contract. The client object is:

```ts
export const DashboardStatisticsApi = {
  getSummary: (params: DashboardQuery) => request.get({ url: '/statistics/dashboard/summary', params }),
  getTrend: (params: DashboardQuery) => request.get({ url: '/statistics/dashboard/trend', params }),
  getFunnel: (params: DashboardQuery) => request.get({ url: '/statistics/dashboard/funnel', params }),
  getProductPage: (params: DashboardProductPageQuery) => request.get({ url: '/statistics/dashboard/product-page', params }),
  exportProductExcel: (params: DashboardProductPageQuery) => request.download({ url: '/statistics/dashboard/export-product-excel', params })
}
```

Add `'/dashboard'` to `allowedMenuPaths`.

- [ ] **Step 4: Run contract and type checks**

Run:

```powershell
pnpm check:dashboard
pnpm ts:check
```

Expected: both commands exit 0.

- [ ] **Step 5: Commit**

```powershell
git add 'yudao电商管理平台前后端/yudao-ui-admin-vue3/src/api/mall/statistics/dashboard.ts' 'yudao电商管理平台前后端/yudao-ui-admin-vue3/src/config/furnitureLite.ts' 'yudao电商管理平台前后端/yudao-ui-admin-vue3/scripts/check-dashboard-contract.mjs' 'yudao电商管理平台前后端/yudao-ui-admin-vue3/package.json'
git commit -m "feat: add dashboard admin API contract"
```

### Task 8: Build the Vue dashboard page

**Files:**
- Create: `src/views/dashboard/index.vue`
- Create: `src/views/dashboard/components/DashboardMetricCards.vue`
- Create: `src/views/dashboard/components/DashboardTrend.vue`
- Create: `src/views/dashboard/components/DashboardFunnel.vue`
- Create: `src/views/dashboard/components/DashboardProductTable.vue`
- Modify: `scripts/check-dashboard-contract.mjs`

**Interfaces:**
- Consumes all methods from `DashboardStatisticsApi`.
- Produces component name `FurnitureDashboard` for the dynamic menu.

- [ ] **Step 1: Extend the contract check so it fails on missing page features**

```js
const page = readFileSync(new URL('../src/views/dashboard/index.vue', import.meta.url), 'utf8')
for (const token of ['7 天', '30 天', '90 天', 'el-date-picker', 'DashboardTrend', 'DashboardFunnel', 'DashboardProductTable']) {
  assert.ok(page.includes(token), `dashboard page is missing ${token}`)
}
assert.ok(!/from ['"](react|@radix-ui|shadcn)/.test(page), 'React/shadcn runtime is forbidden')
```

- [ ] **Step 2: Run and verify missing-page failure**

Run: `pnpm check:dashboard`

Expected: FAIL because `src/views/dashboard/index.vue` does not exist.

- [ ] **Step 3: Build page state and loading behavior**

The page owns one reactive query and independent loaders:

```ts
const query = reactive<DashboardQuery>({
  times: defaultLast30Days(),
  compare: true
})
const loading = reactive({ summary: false, trend: false, funnel: false, products: false })

const refreshAll = () => Promise.allSettled([
  loadSummary(), loadTrend(), loadFunnel(), productTableRef.value?.reload()
])
```

Use request sequence numbers in each loader so a slow old response cannot overwrite a newer filter result. Keep previous successful data visible during refresh.

- [ ] **Step 4: Build the visual sections**

- `DashboardMetricCards`: four `el-card` blocks for homepage PV, detail PV, paid orders and gross profit; each shows current value, comparison, help text and profit quality tag.
- auxiliary row: net revenue, product conversion, average order value and refund rate.
- `DashboardTrend`: existing `Echart` component with traffic/order/revenue/profit/conversion segmented modes.
- `DashboardFunnel`: five horizontal stages with value, previous-step percentage and largest-drop label; use CSS layout and Element Plus progress primitives, not handcrafted SVG.
- `DashboardProductTable`: image/name/category, PV/UV, cart, paid order/item, revenue/refund/net revenue, cost/profit/margin, conversion/refund rate, quality tag, sorting, pagination and export.

Styling uses `furniture-admin.scss` variables, 1px neutral borders, 8–12px radii, low/no shadow, green positive, red negative and amber data-quality warnings. Preserve existing sidebar, top bar, breadcrumbs and tabs.

- [ ] **Step 5: Implement page states and responsive rules**

- initial load uses layout-matching `el-skeleton`;
- each section owns its error and retry action;
- empty data says “当前条件下暂无数据”;
- `lastUpdatedAt` older than20 minutes shows “数据更新延迟”;
- desktop cards 4 columns and chart/funnel 2:1;
- medium cards 2 columns and chart/funnel stacked;
- small cards 1 column, filters in drawer and table horizontal scroll.

- [ ] **Step 6: Run admin verification**

Run:

```powershell
pnpm check:dashboard
pnpm ts:check
pnpm build:local
```

Expected: all commands exit 0. Open `/dashboard` at desktop, tablet and phone widths and complete UI-01 through UI-12 from the test plan.

- [ ] **Step 7: Commit**

```powershell
git add 'yudao电商管理平台前后端/yudao-ui-admin-vue3/src/views/dashboard' 'yudao电商管理平台前后端/yudao-ui-admin-vue3/scripts/check-dashboard-contract.mjs'
git commit -m "feat: build commerce data dashboard page"
```

### Task 9: Run end-to-end data acceptance and release gate

**Files:**
- Modify only files that fail a verified requirement; do not add unrelated cleanup.
- Use: `docs/superpowers/specs/2026-07-10-furniture-dashboard-test-acceptance.md`
- Use: `docs/superpowers/specs/2026-07-10-furniture-dashboard-release-rollback-runbook.md`

**Interfaces:**
- Produces a release candidate where database, services and both frontends agree on the contract.

- [ ] **Step 1: Apply migration twice on a disposable MySQL schema**

Expected: both executions exit0, schema/menu/job duplication queries return zero duplicates.

- [ ] **Step 2: Load the fixed acceptance dataset and run aggregation**

Execute `dashboardStatisticsJob` with parameter `0` for the test tenant/date-adjusted fixture. Query summary and product-page endpoints.

Expected: values exactly match Section3 of the test plan, including gross profit7000 and site PV conversion30.00%.

- [ ] **Step 3: Run all automated gates fresh**

```powershell
Set-Location 'D:\code\furniture web'
npm test
npm run build

Set-Location 'D:\code\yudao电商管理平台前后端\yudao-cloud'
mvn -pl yudao-module-mall/yudao-module-trade-server,yudao-module-mall/yudao-module-statistics-server -am -DskipITs test

Set-Location 'D:\code\yudao电商管理平台前后端\yudao-ui-admin-vue3'
pnpm check:dashboard
pnpm ts:check
pnpm build:prod
```

Expected: every command exits0 with zero test failures and zero TypeScript errors.

- [ ] **Step 4: Perform manual business and permission acceptance**

Complete EVT-01 through EVT-10 and UI-01 through UI-12. Record observed event IDs, aggregate update time, tested account roles and exported filename in the release ticket.

- [ ] **Step 5: Review the final diff**

Run:

```powershell
git diff --check
git status --short
git diff --stat
```

Expected: no whitespace errors, no generated build output, no credentials and no unrelated `vite.config.ts` changes staged.

- [ ] **Step 6: Close the integration gate without an empty commit**

If Step 5 shows no uncommitted dashboard changes, Task 9 is complete. If it shows a verified dashboard defect, return to the task that owns that exact file, apply its test-first fix, use that task's explicit `git add` and `git commit` command, then repeat every command in Task 9 Step 3. Do not create an empty integration commit.

---

## Spec Coverage Checklist

- Independent navigation and permissions: Tasks1,7,8.
- Home PV/UV and daily/30-day filtering: Tasks3,4,5,6,8.
- Product detail click PV/UV: Tasks3,4,6.
- Paid orders/items, sales, refund and net revenue: Tasks4,5.
- Cost snapshot, gross profit and data quality: Tasks1,2,4,5,8.
- PV/UV conversion, cart rate, AOV and refund rate: Tasks4,5,8.
- Trend, funnel, product table and export: Tasks5,7,8.
- 5-minute aggregation, retention and delayed-data recovery: Tasks1,4,5.
- Privacy, anonymous tracking, de-duplication and non-blocking failure: Tasks3,6.
- Responsive shadcn-inspired Vue implementation: Task8.
- Tests, migration rehearsal, release and rollback: Tasks1–9 plus companion runbook.
