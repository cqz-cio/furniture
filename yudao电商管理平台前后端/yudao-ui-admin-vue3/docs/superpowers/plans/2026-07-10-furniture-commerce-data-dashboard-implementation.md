# Furniture Commerce Data Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a tenant-safe furniture commerce dashboard with separately scoped SITE and PRODUCT analytics, server-trusted cart events, payment/refund-day financial attribution, explainable data quality, stage-size and attention views, and permission-separated profit/export access.

**Architecture:** Extend `yudao-module-statistics-server` and its existing MySQL/XXL-Job stack. Public browsing events enter only through the gateway's exact `POST /app-api/statistics/behavior/track` route (plus CORS `OPTIONS`); no broader Statistics app path is public. Successful add-to-cart events originate in Trade after the cart write commits. Daily site and product aggregates are rebuilt transactionally for enabled furniture tenants, while the Vue 3 admin consumes summary, trend, stage-overview, attention, product-page and export APIs.

**Tech Stack:** Java 8, Spring Boot/Spring Cloud, MyBatis Plus, MySQL 8, H2 integration tests, XXL Job, Redis/Redisson, Vue 3, Element Plus, ECharts 6, Axios, Vite and Vitest.

## Global Constraints

- The repository is `2026.04-jdk8-SNAPSHOT` with `java.version=1.8`. Do not use `List.of`, `Map.of`, `Stream.toList`, records, switch expressions or other Java 9+ APIs.
- Business timezone is `Asia/Shanghai`. Admin requests use inclusive `startDate` and `endDate`; SQL uses `[startDate 00:00, endDate + 1 day 00:00)`.
- `scope=SITE` rejects `categoryId` and `spuId`. `scope=PRODUCT` allows them only when the SPU belongs to the supplied category; it never returns product-filtered homepage traffic.
- Payment orders, paid items, paid revenue and initial cost movements are attributed by `trade_order.pay_time`. Refund revenue and returned-item cost reversals are attributed by `trade_after_sale.refund_time`.
- Amount columns and Java fields use MySQL `bigint` and Java `Long`. API amounts remain integer minor currency units; furniture tenant 121 defaults to `USD`.
- Aggregate counters also use MySQL `bigint` and Java `Long`, including exact/estimated/missing cost movements and accepted/excluded events; do not narrow them to `int`/`Integer`.
- Cost quality counts exact, estimated and missing cost movement rows. Any missing row makes `costAmount`, `grossProfit` and `grossMarginPercent` null; `knownCostAmount` remains explanatory only.
- Profit quality values are `EXACT`, `MIXED`, `ESTIMATED`, `INCOMPLETE` and `NOT_APPLICABLE`.
- Public tracking accepts `HOME_VIEW`, `PRODUCT_DETAIL_VIEW` and `CHECKOUT_START` only. `ADD_TO_CART` is recorded from the successful Trade cart operation and cannot be created through the public endpoint.
- HMAC-SHA256 keys are versioned. A version activates only at `00:00:00 Asia/Shanghai`; one tenant/day may contain exactly one active write version. Old keys remain available until all corresponding raw events and deletion requests have expired or completed.
- Public tracking is disabled by default with `VITE_BEHAVIOR_TRACKING_ENABLED=false` and is enabled only after gateway, Statistics and monitoring gates pass.
- In regions requiring analytics consent, pre-consent behavior is: no event request, no persistent visitor/session ID and no cookieless substitute. UI/reports disclose the resulting traffic coverage bias.
- Only configured tenant IDs are aggregated; initial `enabled-tenant-ids` is `[121]`. Do not use `@TenantJob` because the existing aspect iterates every tenant.
- Tracking failure never rolls back or rejects page display, cart mutation, checkout or order creation.
- Coverage that is unavailable or partial returns null traffic/rates rather than numeric zero. Stale freshness may retain the last successful value but must mark the affected block `STALE`; confirmed complete collection with no business activity is zero.
- `trafficDataStatus` is coverage only (`COMPLETE`, `PARTIAL`, `UNAVAILABLE`). Each endpoint derives its separate `freshnessStatus` (`FRESH`, `DELAYED`, `STALE`) from the source watermarks and `lastSuccessfulRunAt`; `STALE` is never written to `traffic_data_status`.
- Base query, profit query, export and profit export use separate permissions.
- The dynamic component name is exactly `FurnitureDashboard` in SQL, Vue `defineOptions` and route contracts.
- Preserve unrelated workspace changes, especially `yudao-ui-admin-vue3/vite.config.ts`. Do not install React, Tailwind, Radix or shadcn runtime packages.

## Delivery Phases

- Phase A — trusted foundations: Tasks 1–5. No public tracking is enabled.
- Phase B — materialization and APIs: Tasks 6–9. Each query, product-risk and export slice has its own test/commit gate before UI work is accepted.
- Phase C — storefront and admin clients: Tasks 10–13. Core dashboard and product-operations UI are separately reviewable; the feature flag remains off in production builds.
- Phase D — release gate: Task 14. Enable tracking only after gateway and data-quality evidence is recorded.

## File Structure

### Database and tests

- Create `yudao-cloud/sql/mysql/statistics-commerce-dashboard.sql` for guarded schema, jobs, menus and permissions.
- Create `yudao-cloud/sql/mysql/statistics-commerce-dashboard-backfill.sql` for resumable cost backfill.
- Create `furniture web/tests/dashboardDatabaseMigration.test.js` for source contracts.
- Create Statistics H2 fixtures under `yudao-module-statistics-server/src/test/resources/sql/` and extend Trade H2 `create_tables.sql`.

### Gateway

- Modify `yudao-cloud/yudao-gateway/src/main/resources/application.yaml` with `statistics-app-api`.
- Create `BehaviorTrackingGatewayProperties.java`, `BehaviorTrackingGatewayFilter.java` and focused gateway tests.
- Modify the existing wildcard `CorsFilter.java` so the tracking route is owned by the exact-origin filter.

### Trade and Statistics APIs

- Extend the Trade order-item price snapshot.
- Correct after-sale stock restoration so refund-only success does not restore inventory.
- Add `StatisticsBehaviorApi` and `CartBehaviorRecordReqDTO` to `yudao-module-statistics-api`.
- Publish a cart-added event after the Trade transaction commits and report it asynchronously to Statistics.

### Statistics server

- Add versioned HMAC properties/validation, behavior ingestion, dedicated rate keys, data-quality classification, raw event storage and cleanup.
- Add traffic/product aggregation, enabled-tenant execution, four job modes and query/export APIs.
- Add explicit SITE/PRODUCT, stage-size, attention and profit-permission response models.

### Frontends

- Create `furniture web/src/services/analytics.js` and wire only public browsing/checkout events.
- Add optional analytics identity headers to the successful remote cart request; do not call public `ADD_TO_CART` tracking.
- Create typed admin API and `src/views/dashboard` components, with `FurnitureDashboard` as the component name.

---

### Task 1: Create guarded MySQL migration and executable H2 fixtures

**Files:**
- Create: `yudao-cloud/sql/mysql/statistics-commerce-dashboard.sql`
- Create: `yudao-cloud/sql/mysql/statistics-commerce-dashboard-backfill.sql`
- Modify: `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/resources/sql/create_tables.sql`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/test/resources/sql/create_tables.sql`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/test/resources/sql/clean.sql`
- Create: `furniture web/tests/dashboardDatabaseMigration.test.js`

**Interfaces:**
- Produces unique `(tenant_id,event_id)` and `(tenant_id,day)` keys.
- Produces nullable `cost_amount`/`gross_profit` and exact/estimated/missing cost counts.
- Produces component name `FurnitureDashboard` and four permission records.

- [ ] **Step 1: Write the failing SQL contract test**

~~~js
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const migration = readFileSync(
  new URL("../../yudao电商管理平台前后端/yudao-cloud/sql/mysql/statistics-commerce-dashboard.sql", import.meta.url),
  "utf8",
);
const backfill = readFileSync(
  new URL("../../yudao电商管理平台前后端/yudao-cloud/sql/mysql/statistics-commerce-dashboard-backfill.sql", import.meta.url),
  "utf8",
);

describe("dashboard database migration", () => {
  it("contains the revised schema and permissions", () => {
    for (const token of [
      "hash_key_version", "traffic_quality", "exact_cost_item_count",
      "estimated_cost_item_count", "missing_cost_item_count", "known_cost_amount",
      "statistics_dashboard_hmac_day", "fk_behavior_event_hmac_day",
      "statistics_behavior_ingestion_gap",
      "event_source", "chk_statistics_behavior_event_source_type",
      "traffic_data_status", "FurnitureDashboard",
      "statistics:dashboard:profit-query", "statistics:dashboard:profit-export",
      "ROLLING_7_COMPLETE_DAYS", "dashboardBehaviorCleanupJob", "productStatisticsJob",
    ]) expect(migration).toContain(token);
    expect(migration).toMatch(/`cost_amount` bigint DEFAULT NULL/i);
    expect(migration).toMatch(/`gross_profit` bigint DEFAULT NULL/i);
  });

  it("keeps the heavy backfill resumable and separate", () => {
    expect(backfill).toContain("@tenant_id");
    expect(backfill).toContain("@after_id");
    expect(backfill).toContain("@batch_size");
    expect(backfill).toContain("item.id > @after_id");
  });
});
~~~

- [ ] **Step 2: Run the test and verify the files are absent**

Run from `D:\code\furniture web`:

~~~powershell
npm test -- dashboardDatabaseMigration.test.js
~~~

Expected: FAIL with `ENOENT` for the migration.

- [ ] **Step 3: Implement the guarded schema**

The migration must first create `statistics_dashboard_hmac_day` with unique `(tenant_id, day)` and `(tenant_id, day, hash_key_version)` keys. Add a small `statistics_behavior_ingestion_gap` ledger keyed by tenant/day/reason/time bucket so fail-closed public-ingestion outages can durably force affected traffic coverage to `PARTIAL` without writing a behavior event. `statistics_behavior_event` then stores server-time events, HMAC version, accepted/excluded quality and soft-delete/audit/tenant fields, with a foreign key from `(tenant_id,event_day,hash_key_version)` to the daily registration. Its `event_source` constraint permits `PUBLIC_WEB` only with HOME/DETAIL/CHECKOUT and `SERVER_CART` only with ADD. `visitor_hash` is always present; `session_hash` is nullable only because an allowed server-cart event without analytics identity may derive the visitor from authenticated user ID. It must create `statistics_traffic_daily` with this quality core:

~~~sql
`exact_cost_item_count` bigint NOT NULL DEFAULT 0,
`estimated_cost_item_count` bigint NOT NULL DEFAULT 0,
`missing_cost_item_count` bigint NOT NULL DEFAULT 0,
`known_cost_amount` bigint NOT NULL DEFAULT 0,
`cost_amount` bigint DEFAULT NULL,
`gross_profit` bigint DEFAULT NULL,
`accepted_event_count` bigint NOT NULL DEFAULT 0,
`excluded_event_count` bigint NOT NULL DEFAULT 0,
`traffic_data_status` tinyint NOT NULL DEFAULT 3,
`traffic_watermark` datetime(3) DEFAULT NULL,
`trade_watermark` datetime(3) DEFAULT NULL,
`refund_watermark` datetime(3) DEFAULT NULL
~~~

The same guarded migration must add nullable `cost_amount`, `gross_profit` and `gross_margin_percent`, non-null `known_cost_amount`, and `bigint` `exact_cost_item_count`, `estimated_cost_item_count` and `missing_cost_item_count` columns to `product_statistics`; Task 6 must not assume those columns already exist.

Guard every column and index independently through `information_schema` plus prepared DDL. Before adding `uk_tenant_time_spu`, run a preflight query that returns duplicate groups and abort the release if any exist; do not silently delete production rows.

Insert these four XXL records disabled/stopped, with exact names, handlers, parameters and Cron expressions:

| Record name | Handler | Parameter | Cron |
|---|---|---|---|
| 数据看板-今日昨日滚动 | `dashboardStatisticsJob` | `TODAY_AND_YESTERDAY` | `0 */5 * * * ?` |
| 数据看板-昨日定稿 | `dashboardStatisticsJob` | `FINALIZE_YESTERDAY` | `0 10 0 * * ?` |
| 数据看板-近7日修复 | `dashboardStatisticsJob` | `ROLLING_7_COMPLETE_DAYS` | `0 40 2 * * ?` |
| 数据看板-事件物理清理 | `dashboardBehaviorCleanupJob` | empty; reads `DASHBOARD_EVENT_RETENTION_DAYS=180` | `0 30 3 * * ?` |

The migration/runbook gate also disables the legacy `productStatisticsJob`; it must be stopped before any new `dashboardStatisticsJob` writer is enabled, preventing concurrent writes to `product_statistics`.

Insert menu/button rows for query, profit-query, export and profit-export. The root menu component name is `FurnitureDashboard`.

- [ ] **Step 4: Implement resumable backfill and H2 schemas**

The backfill updates only `cost_price IS NULL`, filters by tenant and monotonically increasing item ID, and commits each bounded batch. H2 fixtures must include every new DO field so mapper tests execute SQL rather than mocks.

- [ ] **Step 5: Run source and disposable-schema verification**

~~~powershell
Set-Location 'D:\code\furniture web'
npm test -- dashboardDatabaseMigration.test.js

Set-Location 'D:\code\yudao电商管理平台前后端\yudao-cloud'
mvn -pl yudao-module-mall/yudao-module-trade-server,yudao-module-mall/yudao-module-statistics-server -am -DskipITs test
~~~

Then apply the MySQL migration twice to a disposable clone. Both runs must exit 0, and duplicate-group queries for tenant/day/event/SPU must return no rows.

- [ ] **Step 6: Commit**

~~~powershell
git add 'yudao电商管理平台前后端/yudao-cloud/sql/mysql/statistics-commerce-dashboard.sql' 'yudao电商管理平台前后端/yudao-cloud/sql/mysql/statistics-commerce-dashboard-backfill.sql' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/resources/sql/create_tables.sql' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/test/resources' 'furniture web/tests/dashboardDatabaseMigration.test.js'
git commit -m "feat: add guarded commerce dashboard schema"
~~~

### Task 2: Snapshot order cost and correct refund-only inventory behavior

**Files:**
- Modify: `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/price/bo/TradePriceCalculateRespBO.java`
- Modify: `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/price/calculator/TradePriceCalculatorHelper.java`
- Modify: `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/dal/dataobject/order/TradeOrderItemDO.java`
- Modify: `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/order/handler/TradeOrderHandler.java`
- Modify: `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/order/handler/TradeProductSkuOrderHandler.java`
- Modify: `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/order/TradeOrderUpdateService.java`
- Modify: `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/order/TradeOrderUpdateServiceImpl.java`
- Modify: `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/aftersale/AfterSaleServiceImpl.java`
- Modify/Test: `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/convert/order/TradeOrderConvertTest.java`
- Modify/Test: `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/order/TradeOrderUpdateServiceImplTest.java`

**Interfaces:**
- Produces `TradeOrderItemDO.costPrice: Long` and non-null `costEstimated: Boolean`.
- Produces `afterAfterSaleSuccess(order,item,afterSale)` while preserving ordinary order-cancel stock restoration.

- [ ] **Step 1: Write failing snapshot and after-sale tests**

~~~java
@Test
public void convertList_copiesCostAndDefaultsExact() {
    TradePriceCalculateRespBO.OrderItem source = new TradePriceCalculateRespBO.OrderItem()
            .setSkuId(10L).setSpuId(20L).setCount(2)
            .setPrice(5000).setPayPrice(10000).setCostPrice(3500L);
    TradeOrderDO order = new TradeOrderDO().setId(1L).setUserId(9L);
    TradePriceCalculateRespBO calculation = new TradePriceCalculateRespBO()
            .setItems(Collections.singletonList(source));
    TradeOrderItemDO item = TradeOrderConvert.INSTANCE.convertList(order, calculation).get(0);
    assertEquals(Long.valueOf(3500L), item.getCostPrice());
    assertEquals(Boolean.FALSE, item.getCostEstimated());
}

@Test
public void refundOnly_doesNotRestoreSkuStock() {
    AfterSaleDO afterSale = new AfterSaleDO()
            .setWay(AfterSaleWayEnum.REFUND.getWay()).setCount(1);
    handler.afterAfterSaleSuccess(order, item, afterSale);
    verify(productSkuApi, never()).updateSkuStock(any());
}
~~~

Add a companion test proving `RETURN_AND_REFUND` restores exactly `afterSale.count`, not the full original item count.

- [ ] **Step 2: Run focused tests**

~~~powershell
mvn -pl yudao-module-mall/yudao-module-trade-server -am -Dtest=TradeOrderConvertTest,TradeOrderUpdateServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
~~~

Expected: FAIL on missing fields/methods.

- [ ] **Step 3: Implement snapshot mapping with Java 8 types**

~~~java
// TradePriceCalculateRespBO.OrderItem
private Long costPrice;

// TradePriceCalculatorHelper
orderItem.setPrice(sku.getPrice())
        .setCostPrice(sku.getCostPrice() == null ? null : Long.valueOf(sku.getCostPrice()))
        .setPayPrice(sku.getPrice() * item.getCount());

// TradeOrderItemDO
private Long costPrice;
private Boolean costEstimated = Boolean.FALSE;
~~~

Keep the default on the DO and also set `Boolean.FALSE` in the production `convertList` path.

- [ ] **Step 4: Separate after-sale success from ordinary cancellation**

Add a default `afterAfterSaleSuccess` method to `TradeOrderHandler` so non-stock handlers retain existing refund behavior. Override it in `TradeProductSkuOrderHandler` and restore stock only when:

~~~java
if (AfterSaleWayEnum.RETURN_AND_REFUND.getWay().equals(afterSale.getWay())
        && afterSale.getCount() != null && afterSale.getCount() > 0) {
    ProductSkuUpdateStockReqDTO.Item stock = new ProductSkuUpdateStockReqDTO.Item()
            .setId(item.getSkuId()).setIncrCount(afterSale.getCount());
    productSkuApi.updateSkuStock(new ProductSkuUpdateStockReqDTO(
            Collections.singletonList(stock))).checkError();
}
~~~

Pass the full `AfterSaleDO` from `AfterSaleServiceImpl.updateAfterSaleRefunded` into `updateOrderItemWhenAfterSaleSuccess`. Keep `afterCancelOrderItem` for real order cancellation.

- [ ] **Step 5: Run the Trade module**

~~~powershell
mvn -pl yudao-module-mall/yudao-module-trade-server -am -DskipITs test
~~~

Expected: BUILD SUCCESS; refund-only stock is unchanged and return/refund restores the returned count.

- [ ] **Step 6: Commit**

~~~powershell
git add 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server'
git commit -m "fix: snapshot cost and preserve stock on refund only"
~~~

### Task 3: Add the controlled Statistics app route at the gateway

**Files:**
- Modify: `yudao-cloud/yudao-gateway/src/main/resources/application.yaml`
- Modify: `yudao-cloud/yudao-gateway/src/main/java/cn/iocoder/yudao/gateway/filter/cors/CorsFilter.java`
- Modify: `yudao-cloud/yudao-gateway/src/main/java/cn/iocoder/yudao/gateway/filter/logging/AccessLogFilter.java`
- Modify: `yudao-cloud/yudao-gateway/src/main/java/cn/iocoder/yudao/gateway/handler/GlobalExceptionHandler.java`
- Create: `yudao-cloud/yudao-gateway/src/main/java/cn/iocoder/yudao/gateway/filter/statistics/BehaviorTrackingGatewayProperties.java`
- Create: `yudao-cloud/yudao-gateway/src/main/java/cn/iocoder/yudao/gateway/filter/statistics/BehaviorTrackingGatewayFilter.java`
- Create: `yudao-cloud/yudao-gateway/src/test/java/cn/iocoder/yudao/gateway/filter/statistics/BehaviorTrackingGatewayFilterTest.java`
- Create: `yudao-cloud/yudao-gateway/src/test/java/cn/iocoder/yudao/gateway/filter/statistics/BehaviorTrackingGatewayLogSafetyTest.java`

**Interfaces:**
- Produces only `POST /app-api/statistics/behavior/track -> grayLb://statistics-server`, plus `OPTIONS` for that exact path.
- Maps an exact `(Host, Origin)` pair to the configured tenant, replaces an external `tenant-id` and removes internal identity headers.

- [ ] **Step 1: Write failing gateway tests**

Cover exact allowed Host+Origin, correct Origin with wrong Host, correct Host with wrong/unknown Origin, `Origin: null`, spoofed tenant replacement, bearer-token tenant mismatch propagation, 8 KB request limit and internal-header removal. An allowed `POST /app-api/statistics/behavior/track` must forward tenant 121 and its `OPTIONS` preflight must use the exact CORS policy. Assert GET/PUT on that path and every other `/app-api/statistics/*` path are not routed publicly; either member of the Host+Origin pair mismatching returns 403 before routing.

- [ ] **Step 2: Add the route and properties**

~~~yaml
- id: statistics-app-api
  uri: grayLb://statistics-server
  predicates:
    - Path=/app-api/statistics/behavior/track
    - Method=POST,OPTIONS
  filters:
    - name: RequestSize
      args:
        maxSize: 8KB
~~~

Bind configuration without secrets in source:

~~~yaml
yudao:
  gateway:
    behavior-tracking:
      enabled: ${BEHAVIOR_TRACKING_GATEWAY_ENABLED:false}
       allowed-sites:
         - host: ${FURNITURE_TRACKING_HOST:shop.oakved.example}
           origin: ${FURNITURE_TRACKING_ORIGIN:https://shop.oakved.example}
           tenant-id: ${FURNITURE_TRACKING_TENANT_ID:121}
~~~

The filter runs before authentication forwarding, removes incoming `tenant-id` and internal user headers, validates the exact non-null Host+Origin pair, then adds the mapped tenant. Do not map by Origin alone and do not trust an unvalidated forwarded host. Modify the wildcard CORS filter to skip only this exact path so it cannot add `Access-Control-Allow-Origin: *`; never add a catch-all Statistics app route.

For the exact public tracking route, `AccessLogFilter` records only controlled route/status/duration/trace fields and never caches or emits the request body, full headers, IP or User-Agent. On every route, redact `x-analytics-visitor-id`, `x-analytics-session-id` and consent-evidence headers before access or exception logging, so the real cart request is safe too. `GlobalExceptionHandler` must not echo these headers or exception text containing them. `BehaviorTrackingGatewayLogSafetyTest` scans success/403/413/429/500 logs with sentinel values and proves none appear.

- [ ] **Step 3: Run gateway tests**

~~~powershell
mvn -pl yudao-gateway -am -Dtest=BehaviorTrackingGatewayFilterTest,BehaviorTrackingGatewayLogSafetyTest -Dsurefire.failIfNoSpecifiedTests=false test
~~~

Expected: PASS, including spoofed tenant and disallowed Origin cases.

- [ ] **Step 4: Commit**

~~~powershell
git add 'yudao电商管理平台前后端/yudao-cloud/yudao-gateway'
git commit -m "feat: protect statistics app gateway route"
~~~

### Task 4: Implement versioned HMAC behavior ingestion and dedicated rate keys

**Files:**
- Modify: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/pom.xml`
- Modify: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/resources/application.yaml`
- Modify: `yudao-cloud/yudao-framework/yudao-spring-boot-starter-web/src/main/java/cn/iocoder/yudao/framework/apilog/core/filter/ApiAccessLogFilter.java`
- Modify: `yudao-cloud/yudao-framework/yudao-spring-boot-starter-web/src/main/java/cn/iocoder/yudao/framework/web/core/handler/GlobalExceptionHandler.java`
- Modify: `yudao-cloud/yudao-framework/yudao-spring-boot-starter-protection/src/main/java/cn/iocoder/yudao/framework/ratelimiter/core/annotation/RateLimiter.java`
- Modify: `yudao-cloud/yudao-framework/yudao-spring-boot-starter-protection/src/main/java/cn/iocoder/yudao/framework/ratelimiter/core/aop/RateLimiterAspect.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/framework/config/BehaviorTrackingProperties.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/framework/config/BehaviorTrackingConfiguration.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/framework/ratelimit/BehaviorTrackRateLimiterKeyResolver.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/framework/web/BehaviorTrackingSensitiveRequestFilter.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/enums/dashboard/BehaviorEventTypeEnum.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/enums/dashboard/BehaviorEventSourceEnum.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/enums/dashboard/TrafficQualityEnum.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/controller/app/dashboard/AppBehaviorEventController.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/controller/app/dashboard/vo/AppBehaviorEventTrackReqVO.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/dal/dataobject/dashboard/BehaviorEventDO.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/dal/dataobject/dashboard/HmacDayVersionDO.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/dal/dataobject/dashboard/BehaviorIngestionGapDO.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/dal/mysql/dashboard/BehaviorEventMapper.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/dal/mysql/dashboard/HmacDayVersionMapper.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/dal/mysql/dashboard/BehaviorIngestionGapMapper.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/service/dashboard/BehaviorIdentityHasher.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/service/dashboard/TenantBehaviorHmacKeyProvider.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/service/dashboard/BehaviorDuplicateKeyClassifier.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/service/dashboard/BehaviorEventDeduplicator.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/service/dashboard/BehaviorIngestionGapService.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/service/dashboard/BehaviorIngestionGapServiceImpl.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/service/dashboard/BehaviorHmacDayVersionService.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/service/dashboard/BehaviorHmacDayVersionServiceImpl.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/service/dashboard/TrustedBehaviorEventCommand.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/service/dashboard/BehaviorEventService.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/service/dashboard/BehaviorEventServiceImpl.java`
- Create/Test: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/test/java/cn/iocoder/yudao/module/statistics/service/dashboard/BehaviorIdentityHasherTest.java`
- Create/Test: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/test/java/cn/iocoder/yudao/module/statistics/service/dashboard/BehaviorHmacDayVersionServiceTest.java`
- Create/Test: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/test/java/cn/iocoder/yudao/module/statistics/service/dashboard/BehaviorEventDeduplicatorTest.java`
- Create/Test: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/test/java/cn/iocoder/yudao/module/statistics/service/dashboard/BehaviorEventServiceImplTest.java`
- Create/Test: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/test/java/cn/iocoder/yudao/module/statistics/controller/app/dashboard/AppBehaviorEventControllerTest.java`
- Create/Test: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/test/java/cn/iocoder/yudao/module/statistics/controller/app/dashboard/BehaviorTrackingLogSafetyTest.java`

**Interfaces:**
- Public service: `void trackPublic(AppBehaviorEventTrackReqVO reqVO, Long userId)`.
- Internal service: `void recordTrusted(TrustedBehaviorEventCommand command)`; it has no dependency on the API DTO introduced in Task 5.
- All public and trusted behavior-event writes use normal MyBatis `insert` and treat only `uk_tenant_event` as idempotent success.

- [ ] **Step 1: Write failing HMAC, duplicate-key and rate-key tests**

Tests must prove:

- the same tenant/key/version/raw ID gives the same 64-char HMAC;
- tenant or key version changes the result;
- activation time must be exactly a Shanghai natural-day boundary;
- the active write version is constant throughout one tenant/day;
- concurrent registration of the same tenant/day/version is idempotent, while concurrent different versions leave one row and reject the loser before its event insert;
- two consecutive event days using the same key version create two registrations whose `activatedAt` values are each that `eventDay` at `00:00:00 Asia/Shanghai`, not the key version's original activation timestamp;
- production-style enabled configuration with a missing tenant/version active key reference refuses startup;
- two request VOs with different event IDs resolve to the same tenant+IP rate key;
- the client IP comes from the configured trusted-proxy chain rather than an arbitrary forwarded header;
- default minute limits are 120 per IP, 120 per visitor HMAC and 6000 per tenant, with a separately configured global breaker;
- success, validation 400, rate-limit 429 and injected 500 logs/access-log/error-log DTOs contain none of the sentinel request body, visitor/session/event IDs, full IP or full User-Agent;
- concurrent public requests with the same composite key create one event; a retry at 4.999 seconds is duplicate success and one at 5.001 seconds is accepted;
- Redis failure rejects the public write, emits an alert and durable ingestion-gap marker, while SERVER_CART bypasses the public five-second key and remains idempotent by server event ID;
- a duplicate on `uk_tenant_event` succeeds, while a different `DuplicateKeyException` and any other database error propagate.
- PUBLIC_WEB accepts only HOME/DETAIL/CHECKOUT with non-null visitor/session hashes, while SERVER_CART accepts only ADD, always has a visitor hash and may have a null session hash.

- [ ] **Step 2: Add explicit HMAC configuration**

~~~yaml
yudao:
  statistics:
    behavior:
      enabled-tenant-ids: [121]
      hmac:
        tenants:
          "121":
            active-version: ${STATISTICS_BEHAVIOR_HMAC_TENANT_121_ACTIVE_VERSION:1}
            active-key-ref: ${STATISTICS_BEHAVIOR_HMAC_TENANT_121_ACTIVE_KEY_REF:}
            activates-at: ${STATISTICS_BEHAVIOR_HMAC_TENANT_121_ACTIVATES_AT:2026-07-11T00:00:00+08:00}
            previous-version: ${STATISTICS_BEHAVIOR_HMAC_TENANT_121_PREVIOUS_VERSION:}
            previous-key-ref: ${STATISTICS_BEHAVIOR_HMAC_TENANT_121_PREVIOUS_KEY_REF:}
~~~

`BehaviorTrackingConfiguration` validates each enabled tenant independently: `activates-at` is midnight in `Asia/Shanghai`, active and previous versions differ, enabled non-local deployments have nonblank KMS/secret-manager references, and only one pending rotation exists. `TenantBehaviorHmacKeyProvider` resolves or HKDF-derives distinct key material for `(tenantId, version)`; production configuration never uses one global key or stores plaintext key bytes. New writes select one tenant version from server receive time. Aggregation must fail and alert if one tenant/day contains multiple versions; it must not attempt cross-version visitor matching. Retain the previous tenant key only for deletion/verification of raw events written with that version.

- [ ] **Step 3: Implement HMAC-SHA256 and the dedicated key**

~~~java
public String hash(Long tenantId, int version, String rawId) throws GeneralSecurityException {
    byte[] keyBytes = keyProvider.keyForTenantVersion(tenantId, version);
    SecretKeySpec key = new SecretKeySpec(keyBytes, "HmacSHA256");
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(key);
    byte[] bytes = mac.doFinal((tenantId + ":" + rawId).getBytes(StandardCharsets.UTF_8));
    return HexUtil.encodeHexStr(bytes);
}

@Override
public String resolver(JoinPoint joinPoint, RateLimiter rateLimiter) {
    return SecureUtil.sha256(TenantContextHolder.getRequiredTenantId()
            + ":" + ServletUtils.getClientIP());
}
~~~

The resolver must not read method arguments. It uses only the server-bound tenant plus the client IP resolved through the configured trusted-proxy policy; untrusted `Forwarded`/`X-Forwarded-For` values are ignored. Add Redis counters after HMAC calculation for 120/IP/minute, 120/visitor/minute and 6000/tenant/minute, plus a separately configured global breaker whose threshold is supplied by operations configuration rather than source code. Add `logArguments=false` to `@RateLimiter` and make `RateLimiterAspect` omit `joinPoint.getArgs()` for this endpoint. Excluded events increment reason-coded metrics but never enter business aggregates.

- [ ] **Step 4: Register the tenant/day HMAC version transactionally**

Before either a public or trusted event is inserted, call `ensureRegistered(tenantId, eventDay, version)` in the same transaction. The service derives `activatedAt` exclusively as `eventDay.atStartOfDay(Asia/Shanghai)` for that daily row; it never reuses the key configuration's first `activates-at` timestamp on later days. It performs a normal insert into `statistics_dashboard_hmac_day`. On `uk_hmac_tenant_day`/`uk_hmac_tenant_day_version` `DuplicateKeyException`, read the existing `(tenantId,day)` row: the same version and exact daily midnight are idempotent; a different version/boundary throws a mixed-version error and the event is not written. Do not update or replace an existing day registration. The database check and event foreign key remain final guards.

- [ ] **Step 5: Implement strict public ingestion**

The public VO contains only `eventId`, `eventType`, `visitorId`, `sessionId`, optional SPU/SKU/quantity, `pagePath` and `referrerHost`. It has no client time, device type, user ID or full referrer. The controller sets `PUBLIC_WEB`, requires both identity hashes and rejects public `ADD_TO_CART`; callers cannot choose `eventSource`.

`BehaviorTrackingSensitiveRequestFilter` marks only exact `POST /app-api/statistics/behavior/track` requests before the common access-log filter runs. For marked requests, `ApiAccessLogFilter` must not read or persist query/body/IP/User-Agent, and `GlobalExceptionHandler` must not build the generic body-bearing error DTO or log exception messages that may echo rejected values; emit only trace ID, bound tenant, controlled result/reason code and aggregate metrics. This applies to success, 400, 429 and 500 paths. The filter and handler must not weaken logging for unrelated endpoints.

Validate enabled tenant, mapped token tenant, current-tenant SPU/SKU ownership and path/event rules. After HMAC and path normalization, `BehaviorEventDeduplicator` computes the public composite key from tenant, visitor HMAC, event type, normalized path, SPU and SKU, then performs one atomic Redis `SET key claimToken NX PX 5000`. A hit returns the same idempotent success response without a database insert. Use server receive time only; concurrency and 4.999/5.001-second boundary tests use a controllable Redis clock. Register transaction synchronization so any non-idempotent insert/commit rollback uses a compare-and-delete Lua script for that claim token; a failed database write must not leave a five-second key that turns the retry into false success.

If Redis is unavailable, fail closed for PUBLIC_WEB: do not fall back to `eventId`-only dedupe and do not insert the event. Return a controlled temporary failure that the storefront silently isolates, increment an alerting metric and coalesce a durable `DEDUP_REDIS_UNAVAILABLE` row in `statistics_behavior_ingestion_gap`; Task 6 treats any overlapping gap as `trafficDataStatus=PARTIAL`. SERVER_CART does not use the public five-second key and remains idempotent through its server-generated `eventId`. Classify public traffic from the truncated User-Agent and configured internal/test signals, store quality/reason, and never log the request body or raw identifiers.

Use normal insert:

~~~java
try {
    behaviorEventMapper.insert(event);
} catch (DuplicateKeyException ex) {
    if (!duplicateKeyClassifier.isConstraint(ex, "uk_tenant_event")) {
        throw ex;
    }
}
~~~

- [ ] **Step 6: Run Statistics ingestion tests**

~~~powershell
mvn -pl yudao-module-mall/yudao-module-statistics-server -am -Dtest=BehaviorIdentityHasherTest,BehaviorHmacDayVersionServiceTest,BehaviorEventDeduplicatorTest,BehaviorEventServiceImplTest,AppBehaviorEventControllerTest,BehaviorTrackingLogSafetyTest -Dsurefire.failIfNoSpecifiedTests=false test
~~~

Expected: PASS with no Java 9+ API use.

- [ ] **Step 7: Commit**

~~~powershell
git add 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-statistics-server'
git commit -m "feat: ingest versioned trusted behavior events"
~~~

### Task 5: Record ADD_TO_CART from the committed Trade operation

**Files:**
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-api/src/main/java/cn/iocoder/yudao/module/statistics/api/behavior/StatisticsBehaviorApi.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-api/src/main/java/cn/iocoder/yudao/module/statistics/api/behavior/dto/CartBehaviorRecordReqDTO.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/api/behavior/StatisticsBehaviorApiImpl.java`
- Modify: `yudao-cloud/yudao-module-mall/yudao-module-trade-server/pom.xml`
- Modify: `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/framework/rpc/config/RpcConfiguration.java`
- Modify: `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/cart/CartServiceImpl.java`
- Modify: `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/app/cart/AppCartController.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/cart/event/CartAddedEvent.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/cart/event/CartAddedEventListener.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/cart/behavior/BehaviorTrackingConsentPolicy.java`
- Create/Test: `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/cart/CartServiceImplTest.java`
- Create/Test: `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/cart/event/CartAddedEventListenerTest.java`
- Create/Test: `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/controller/app/cart/CartBehaviorLogSafetyTest.java`

**Interfaces:**
- Produces `CommonResult<Boolean> recordCartAdded(CartBehaviorRecordReqDTO reqDTO)`.
- The public track endpoint never accepts `ADD_TO_CART`.

- [ ] **Step 1: Write failing server-source tests**

Verify that a successful insert and a successful existing-cart increment each publish exactly one event only when the server tracking kill switch and regional consent policy allow it; validation/DB failure publishes none; Statistics RPC failure does not change the cart response. In a consent-required configuration, a successful pre-consent cart write publishes zero events and receives no visitor/session identifier, while verified consent publishes one after commit. Verify the public endpoint rejects `ADD_TO_CART`. With sentinel `x-analytics-*` values, scan Trade success/400/injected-500 console logs and captured access/error-log DTOs and assert the raw headers never appear.

- [ ] **Step 2: Add the internal API DTO**

~~~java
@Data
public class CartBehaviorRecordReqDTO {
    @NotBlank private String eventId;
    @NotNull private Long userId;
    @NotNull private Long spuId;
    @NotNull private Long skuId;
    @NotNull private Integer quantity;
    private String visitorId;
    private String sessionId;
}
~~~

Tenant is propagated through the existing RPC tenant interceptor, never supplied as a DTO field.
`StatisticsBehaviorApiImpl` maps this transport DTO into `TrustedBehaviorEventCommand` and calls `BehaviorEventService.recordTrusted`; the core service never imports the API module DTO.

- [ ] **Step 3: Publish after commit and report without blocking Trade**

Mark `addCart` transactional. `BehaviorTrackingConsentPolicy` first evaluates the server kill switch, site/tenant `consentRequired` configuration and server-verifiable CMP consent evidence. In a consent-required site, missing/invalid evidence means the cart mutation continues but no `CartAddedEvent` is created; a client boolean alone is not trusted. When allowed, publish after the mapper write and consume with `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` plus the existing application executor. The listener generates the server event ID and catches/records RPC errors:

~~~java
try {
    statisticsBehaviorApi.recordCartAdded(convert(event)).checkError();
} catch (Exception ex) {
    log.warn("[recordCartAdded][cartId({}) statistics unavailable]", event.getCartId());
    meterRegistry.counter("statistics.cart_event.failed").increment();
}
~~~

Visitor/session headers are correlation inputs only after tracking is allowed. `AppCartController` and `BehaviorTrackingConsentPolicy` must never include raw analytics/consent headers in validation messages, exceptions, MDC or structured logs. A consent-required site requires valid consent evidence and both identifiers; it never falls back to the authenticated user before consent. A site explicitly configured as not requiring consent may derive the required stable `visitor_hash` from the domain-separated value `auth-user:<userId>` under the tenant/day HMAC key and leave `session_hash` null when analytics identity is absent. In all cases the cart business result is independent, the event source is forced to `SERVER_CART`, the event type is forced to `ADD_TO_CART`, and no event is emitted while the server tracking kill switch is off.

- [ ] **Step 4: Register the Feign API and run tests**

~~~powershell
mvn -pl yudao-module-mall/yudao-module-trade-server,yudao-module-mall/yudao-module-statistics-server -am -Dtest=CartServiceImplTest,CartAddedEventListenerTest,CartBehaviorLogSafetyTest,BehaviorEventServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
~~~

Expected: PASS; cart writes survive a mocked Statistics outage.

- [ ] **Step 5: Commit**

~~~powershell
git add 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-statistics-api' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-statistics-server' 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-trade-server'
git commit -m "feat: record cart behavior from trade success"
~~~

### Task 6: Materialize daily site/product data and repair windows

**Files:**
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/dal/dataobject/dashboard/TrafficDailyDO.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/dal/mysql/dashboard/TrafficDailyMapper.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/dal/mysql/dashboard/DashboardAggregationMapper.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/resources/mapper/dashboard/DashboardAggregationMapper.xml`
- Modify: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/dal/dataobject/product/ProductStatisticsDO.java`
- Modify: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/dal/mysql/product/ProductStatisticsMapper.java`
- Modify: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/resources/mapper/product/ProductStatisticsMapper.xml`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/service/dashboard/DashboardAggregationService.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/service/dashboard/DashboardAggregationServiceImpl.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/service/dashboard/DashboardTenantExecutor.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/framework/config/DashboardAggregationProperties.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/job/dashboard/DashboardStatisticsJob.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/job/dashboard/DashboardBehaviorCleanupJob.java`
- Create/Test: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/test/java/cn/iocoder/yudao/module/statistics/dal/mysql/dashboard/DashboardAggregationMapperTest.java`
- Create/Test: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/test/java/cn/iocoder/yudao/module/statistics/service/dashboard/DashboardAggregationServiceImplTest.java`

**Interfaces:**
- Produces `void recomputeDay(long tenantId, LocalDate day)`.
- Produces modes `TODAY_AND_YESTERDAY`, `FINALIZE_YESTERDAY` and `ROLLING_7_COMPLETE_DAYS`.
- Cleanup accepts no retention task argument and deletes only rows older than the approved `DASHBOARD_EVENT_RETENTION_DAYS` value whose aggregation watermark has passed.

- [ ] **Step 1: Write failing SQL integration tests**

Load fixed data with payment before/after midnight, refund-only, return/refund, partial refund, missing cost, exact and estimated cost, deleted rows, and identical IDs in two tenants. Assert payment metrics land on `pay_time` day and refunds/cost reversals land on `refund_time` day. Call `recomputeDay` twice for the same tenant/day and assert there is still exactly one site row and one row per product with no unique-key failure; force the second insert to fail and prove the prior committed rows remain visible after rollback.

- [ ] **Step 2: Implement grouped SQL with explicit tenant and delete predicates**

Every joined table must include matching `tenant_id` and `deleted = 0`. Do not use per-SPU correlated subqueries. Build separate site and product aggregates; SITE never accepts product filters. A durable ingestion-gap row overlapping the day forces traffic fields/rates to null and coverage to `PARTIAL`, regardless of the raw event count.

For each day calculate:

~~~text
paidRevenue    = sum paid item pay_price where order.pay_time is in day
refundAmount   = sum completed after-sale refund_price where refund_time is in day
knownCost      = paid cost movements - completed RETURN_AND_REFUND cost reversals
netRevenue     = paidRevenue - refundAmount
averageOrderValue = round(paidRevenue / paidOrderCount) in minor units; null when paidOrderCount = 0
refundPercent  = refundAmount / paidRevenue * 100; null when paidRevenue = 0 and never capped at 100%
costAmount     = null when missingCostItemCount > 0, otherwise knownCost
grossProfit    = null when costAmount is null, otherwise netRevenue - costAmount
grossMargin    = null when grossProfit is null or netRevenue <= 0
~~~

Cost counts are cost-movement rows, never `paidItemCount`: payment creates the positive movement on `pay_time`, and successful `RETURN_AND_REFUND` creates the reversal on `refund_time` with the original item's exact/estimated/missing quality. The same order item may therefore count once on its payment day and once on a later return day. All three counts are zero and quality is `NOT_APPLICABLE` only when the day has no cost movement; refund-only creates no reversal, so it can produce negative net revenue/gross profit while cost quality remains `NOT_APPLICABLE`. `netRevenue` may be negative when refund-day amounts exceed that day's paid revenue; do not clamp it or the refund percentage.

- [ ] **Step 3: Implement transactional delete-and-rebuild**

~~~java
@Transactional(rollbackFor = Exception.class)
public void recomputeDay(long tenantId, LocalDate day) {
    TenantUtils.execute(tenantId, () -> {
        assertSingleHashVersion(day);
        trafficDailyMapper.physicalDeleteByTenantAndDay(tenantId, day);
        productStatisticsMapper.physicalDeleteByTenantAndDay(tenantId, day);
        trafficDailyMapper.insert(aggregationMapper.selectTrafficDaily(day));
        productStatisticsMapper.insertBatch(aggregationMapper.selectProductDaily(day));
    });
}
~~~

Both delete methods are custom parameter-bound SQL in mapper XML:

~~~sql
DELETE FROM statistics_traffic_daily WHERE tenant_id = #{tenantId} AND day = #{day};
DELETE FROM product_statistics WHERE tenant_id = #{tenantId} AND time = #{day};
~~~

They must execute physical `DELETE`, not MyBatis `@TableLogic` updates: the unique keys omit `deleted`, so a soft-deleted row would make the replacement insert fail. Invoke `recomputeDay` through the Spring proxy, wrap each tenant/day in a Redis lock and keep physical delete plus inserts in one transaction. Readers continue seeing the prior committed day until replacement commits, and any insert failure rolls the physical deletes back.

- [ ] **Step 4: Implement explicit job windows without `@TenantJob`**

~~~java
List<LocalDate> resolveDays(DashboardJobMode mode, LocalDate today) {
    if (mode == DashboardJobMode.TODAY_AND_YESTERDAY) {
        return Arrays.asList(today, today.minusDays(1));
    }
    if (mode == DashboardJobMode.FINALIZE_YESTERDAY) {
        return Collections.singletonList(today.minusDays(1));
    }
    return IntStream.rangeClosed(1, 7)
            .mapToObj(today::minusDays)
            .collect(Collectors.toList());
}
~~~

`DashboardTenantExecutor` iterates only configured enabled tenant IDs and calls `TenantUtils.execute`. The 7-day repair is low-frequency and separately monitored; a manual history mode requires an explicit start/end bounded to 366 days. At startup/release validation, refuse to enable the new writer jobs while legacy `productStatisticsJob` is active.

- [ ] **Step 5: Run aggregation tests**

~~~powershell
mvn -pl yudao-module-mall/yudao-module-statistics-server -am -Dtest=DashboardAggregationMapperTest,DashboardAggregationServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
~~~

Expected: PASS for tenant isolation, midnight attribution, null profit and all three repair modes.

- [ ] **Step 6: Commit**

~~~powershell
git add 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-statistics-server'
git commit -m "feat: materialize trusted dashboard daily data"
~~~

### Task 7: Expose scoped summary/trend queries and per-endpoint metadata

**Files:**
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/controller/admin/dashboard/DashboardStatisticsController.java`
- Create under `controller/admin/dashboard/vo`: `DashboardQueryReqVO.java`, `DashboardSummaryRespVO.java` and `DashboardTrendRespVO.java`.
- Create under `enums/dashboard`: `DashboardScopeEnum.java`, `ProfitDataQualityEnum.java`, `TrafficDataStatusEnum.java` and `FreshnessStatusEnum.java`.
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/dal/mysql/dashboard/DashboardQueryMapper.java`
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/resources/mapper/dashboard/DashboardQueryMapper.xml`
- Create under `service/dashboard`: `DashboardQueryService.java` and `DashboardQueryServiceImpl.java`.
- Create/Test: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/test/java/cn/iocoder/yudao/module/statistics/service/dashboard/DashboardQueryServiceImplTest.java`
- Create/Test: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/test/java/cn/iocoder/yudao/module/statistics/controller/admin/dashboard/DashboardStatisticsControllerTest.java`

**Interfaces:**
- `DashboardQueryReqVO` contains `scope`, `startDate`, `endDate`, `compare`, optional `categoryId`/`spuId`.
- Produces `GET /statistics/dashboard/summary` and `GET /statistics/dashboard/trend`.
- Every endpoint calculates its own `asOf`, `snapshotId`, source watermarks, coverage `trafficDataStatus` and independent `freshnessStatus`.
- Profit fields are removed before serialization without `statistics:dashboard:profit-query`.

- [ ] **Step 1: Write failing scope, quality, metadata and permission tests**

Reject SITE with product filters and mismatched category/SPU. PRODUCT trend must omit homepage fields. Tests distinguish real zero from unknown, keep coverage separate from derived freshness (including `STALE`), use per-endpoint watermarks, and omit profit fields without permission. Site/product conversion is a same-period size ratio rather than cohort attribution; preserve a fixture above 100% instead of clamping it.

- [ ] **Step 2: Implement Java 8-safe calculations**

~~~java
private ProfitDataQualityEnum quality(Long exact, Long estimated, Long missing) {
    long exactCount = exact == null ? 0L : exact.longValue();
    long estimatedCount = estimated == null ? 0L : estimated.longValue();
    long missingCount = missing == null ? 0L : missing.longValue();
    if (missingCount > 0L) return ProfitDataQualityEnum.INCOMPLETE;
    if (exactCount == 0L && estimatedCount == 0L) return ProfitDataQualityEnum.NOT_APPLICABLE;
    if (exactCount > 0L && estimatedCount > 0L) return ProfitDataQualityEnum.MIXED;
    return estimatedCount > 0L ? ProfitDataQualityEnum.ESTIMATED : ProfitDataQualityEnum.EXACT;
}

private BigDecimal percent(Long numerator, Long denominator) {
    if (numerator == null || denominator == null || denominator.longValue() == 0L) return null;
    return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100L))
            .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
}
~~~

Rate comparisons return percentage-point change; amount/count comparisons return absolute and relative change. Site/product conversion may exceed 100% because payment and browsing are not a cohort and direct/cross-period behavior exists; backend calculations must not clamp it. Default date is the 30 complete days ending yesterday.

- [ ] **Step 3: Implement scoped SQL and metadata**

SITE never accepts product predicates. PRODUCT summary/trend use only product-dimension traffic and omit global homepage data. `trafficDataStatus` is `COMPLETE|PARTIAL|UNAVAILABLE`; derive `FRESH|DELAYED|STALE` from this endpoint's relevant minimum watermark and `lastSuccessfulRunAt`, with thresholds of 600 and 1200 seconds.

- [ ] **Step 4: Run the core query slice**

~~~powershell
mvn -pl yudao-module-mall/yudao-module-statistics-server -am -Dtest=DashboardQueryServiceImplTest,DashboardStatisticsControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
~~~

Expected: PASS for the two endpoints, SITE/PRODUCT isolation, metadata and query/profit permissions.

- [ ] **Step 5: Commit**

~~~powershell
git add 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-statistics-server'
git commit -m "feat: expose scoped dashboard summaries and trends"
~~~

### Task 8: Add stage overview, attention and product-page queries

**Files:**
- Create under `controller/admin/dashboard/vo`: `DashboardStageOverviewRespVO.java`, `DashboardAttentionRespVO.java`, `DashboardProductPageReqVO.java` and `DashboardProductRespVO.java`.
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/enums/dashboard/DashboardRiskTypeEnum.java`
- Modify: `DashboardStatisticsController.java`, `DashboardQueryMapper.java`, `DashboardQueryMapper.xml`, `DashboardQueryService.java` and `DashboardQueryServiceImpl.java` from Task 7.
- Create/Test: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/test/java/cn/iocoder/yudao/module/statistics/service/dashboard/DashboardStageAttentionServiceTest.java`

**Interfaces:**
- Produces `GET /statistics/dashboard/stage-overview`, `/attention` and `/product-page`.
- Attention always evaluates product rules: with no category/SPU it returns all-product attention for the requested date range, while category/SPU filters are allowed only in PRODUCT context. Product-page is PRODUCT-only; stage overview supports both scopes.

- [ ] **Step 1: Write failing stage, risk and product-page tests**

Cover both stage variants, all-product attention with no product filters, category/SPU validation in PRODUCT, all four risk predicates, no-profit permission behavior, sorting whitelist, pagination defaults/maximums and reuse of identical threshold predicates by attention and product-page. Add incomplete-quality cases proving high-traffic/low-conversion is not evaluated unless traffic coverage is `COMPLETE`, low-margin is not evaluated for `INCOMPLETE` profit, and missing-cost can still hit.

- [ ] **Step 2: Implement scope-specific stage overview**

- SITE returns HOME_UV, PRODUCT_DETAIL_UV, ADD_CART_USER, CHECKOUT_SESSION and PAID_BUYER.
- PRODUCT returns PRODUCT_DETAIL_UV, ADD_CART_USER and PAID_BUYER; HOME_UV and CHECKOUT_SESSION have null value and `applicability=NOT_APPLICABLE`.
- Every item includes `unit`, `dedupeScope` and `applicability`; the response includes `cohortAligned=false` and explanatory copy. Never calculate step conversion or “largest drop.”

- [ ] **Step 3: Implement exact tenant-configured attention rules**

- `HIGH_TRAFFIC_LOW_CONVERSION`: only when traffic coverage is `COMPLETE`; then detail PV >= 100 and product PV conversion < 1.00%.
- `HIGH_REFUND`: paid order count >= 10, paid revenue >= 100000 minor units (tenant-configurable), and refund percent > 10.00%.
- `LOW_OR_NEGATIVE_MARGIN`: only when profit quality is not `INCOMPLETE`; gross profit < 0 hits directly, otherwise paid order count >= 5 and gross margin < 10.00%.
- `MISSING_COST`: `missingCostItemCount > 0`.

Return all seven threshold values plus “rule hint, not an automatic diagnosis.” `items[]` contains only matched rules. A skipped permitted rule is added to the top-level `notEvaluated[]` as `{ riskType, reasonCode, copy }`, using controlled reasons such as `TRAFFIC_INCOMPLETE` or `PROFIT_INCOMPLETE`; it never appears as a zero hit count that looks evaluated. `MISSING_COST` remains evaluable when profit is incomplete. Suppress profit/cost rules without profit permission. Product-page `riskType` reuses the exact predicates and applies only whitelisted sort mappings.

- [ ] **Step 4: Run the stage/product slice**

~~~powershell
mvn -pl yudao-module-mall/yudao-module-statistics-server -am -Dtest=DashboardStageAttentionServiceTest,DashboardStatisticsControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
~~~

- [ ] **Step 5: Commit**

~~~powershell
git add 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-statistics-server'
git commit -m "feat: add dashboard stage and product risks"
~~~

### Task 9: Add permission-separated safe exports

**Files:**
- Create: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/main/java/cn/iocoder/yudao/module/statistics/controller/admin/dashboard/vo/DashboardProductExcelVO.java`
- Create under `service/dashboard`: `DashboardExportService.java`, `DashboardExportServiceImpl.java`, `DashboardExportAuditService.java` and `DashboardExportRateLimiter.java`.
- Modify: `DashboardStatisticsController.java` and `DashboardQueryMapper.java` from Tasks 7–8.
- Create/Test: `yudao-cloud/yudao-module-mall/yudao-module-statistics-server/src/test/java/cn/iocoder/yudao/module/statistics/service/dashboard/DashboardExportServiceTest.java`

**Interfaces:**
- Normal export requires `statistics:dashboard:export` and excludes cost/profit.
- Profit export additionally requires `statistics:dashboard:profit-export`.

- [ ] **Step 1: Write failing permission, formula, limit and audit tests**

Test both export permissions independently, response-field exclusion, formula prefixes, 10,000-row cap, three exports per user per 10 minutes, sort whitelist and complete audit records for success/failure.

- [ ] **Step 2: Implement bounded exports**

Escape text beginning with `=`, `+`, `-`, `@`, tab or carriage return. Export the normalized current scope/date/filter/risk/sort without pagination. Audit tenant, user, filter hash, row count, file SHA-256 and result without logging sensitive values.

- [ ] **Step 3: Run the export slice**

~~~powershell
mvn -pl yudao-module-mall/yudao-module-statistics-server -am -Dtest=DashboardExportServiceTest,DashboardStatisticsControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
~~~

- [ ] **Step 4: Commit**

~~~powershell
git add 'yudao电商管理平台前后端/yudao-cloud/yudao-module-mall/yudao-module-statistics-server'
git commit -m "feat: add safe dashboard exports"
~~~

### Task 10: Add gated storefront tracking and server-cart identity headers

**Files:**
- Create: `furniture web/src/services/analytics.js`
- Modify: `furniture web/src/services/yudaoCartApi.js`
- Modify: `furniture web/src/App.vue`
- Modify: `furniture web/src/pages/SofaPdpPage.vue`
- Modify: `furniture web/.env.example` and `.env.production.example`
- Create: `furniture web/tests/analytics.test.js` and `dashboardTrackingIntegration.test.js`

**Interfaces:**
- Produces public `trackHomeView`, `trackProductDetailView` and `trackCheckoutStart`.
- Produces `analyticsIdentityHeaders()` for the authenticated cart request.
- Does not produce a public `trackAddToCart` call.

- [ ] **Step 1: Write failing feature-flag, consent, session and payload tests**

Verify disabled-by-default sends nothing and creates no IDs. In consent-required mode, pre-consent behavior also sends nothing, creates no persistent visitor/session ID, has no cookieless request and adds no cart analytics/consent headers; the successful server cart operation must therefore emit no analytics event. Verify consent enables tracking, withdrawal clears identifiers, visitor records persist `createdAt` and rotate at the configured TTL boundary, session rotates after 30 inactive minutes but not on refresh, retry reuses eventId, payload omits user ID/full referrer/device type/client time, and cart analytics headers plus server-verifiable CMP evidence are attached only when tracking and consent permit them.

- [ ] **Step 2: Implement the gate and session clock**

~~~js
export const isBehaviorTrackingEnabled = () =>
  String(import.meta.env.VITE_BEHAVIOR_TRACKING_ENABLED || "false").toLowerCase() === "true";

const SESSION_IDLE_MS = 30 * 60 * 1000;
const VISITOR_TTL_DAYS = Math.min(
  Number(import.meta.env.VITE_ANALYTICS_VISITOR_TTL_DAYS || 180),
  180,
);

export const getSessionId = (now = Date.now()) => {
  const last = Number(sessionStorage.getItem("oakved_analytics_last_active") || 0);
  if (!sessionStorage.getItem("oakved_session_id") || now - last >= SESSION_IDLE_MS) {
    sessionStorage.setItem("oakved_session_id", crypto.randomUUID());
  }
  sessionStorage.setItem("oakved_analytics_last_active", String(now));
  return sessionStorage.getItem("oakved_session_id");
};
~~~

Store the visitor as `{ id, createdAt }`, not a bare UUID. On every allowed access, rotate it when `now - createdAt >= VISITOR_TTL_DAYS`; reject nonpositive/invalid configuration. The effective visitor TTL is capped at 180 days and release validation requires it to be no greater than `DASHBOARD_EVENT_RETENTION_DAYS`. Session expiry remains 30 inactive minutes and is independent of visitor rotation.

The consent adapter has an explicit `consentRequired` setting. When true and consent is absent, every public tracking function and `analyticsIdentityHeaders` returns before touching storage or network. No cookieless fallback is implemented. After consent, `analyticsIdentityHeaders` includes visitor/session IDs and opaque server-verifiable CMP evidence; it never invents a client-only `consent=true` trust signal. The dashboard exposes `trafficDataAvailableFrom/status` and explanatory copy so users understand consent-related coverage bias.

- [ ] **Step 3: Implement two-second send and one retry**

Use the storefront's existing request wrapper with `requestOptions.url = "/statistics/behavior/track"`, `keepalive: true`, an AbortController timeout and exactly one retry of the same serialized event/eventId. Send `referrerHost` only.

- [ ] **Step 4: Wire trusted moments**

- Watch normalized route signature and track HOME only when it becomes `/`.
- Track PRODUCT_DETAIL only after a valid Yudao product resolves.
- Track CHECKOUT_START only after cart/auth validation and immediately before entering the real checkout route.
- Remove client ADD_TO_CART behavior submission. `yudaoCartApi.addCartItem` adds `x-analytics-visitor-id`, `x-analytics-session-id` and opaque consent evidence only when tracking and policy permit; otherwise it adds none, so a consent-required server writes the cart but skips the analytics event.

- [ ] **Step 5: Add environment defaults and run tests**

~~~dotenv
VITE_BEHAVIOR_TRACKING_ENABLED=false
VITE_ANALYTICS_CONSENT_REQUIRED=true
VITE_ANALYTICS_VISITOR_TTL_DAYS=180
~~~

~~~powershell
npm test -- analytics.test.js dashboardTrackingIntegration.test.js
npm test
npm run build
~~~

Expected: all tests/build pass with tracking off by default.

- [ ] **Step 6: Commit**

~~~powershell
git add 'furniture web/src/services/analytics.js' 'furniture web/src/services/yudaoCartApi.js' 'furniture web/src/App.vue' 'furniture web/src/pages/SofaPdpPage.vue' 'furniture web/tests/analytics.test.js' 'furniture web/tests/dashboardTrackingIntegration.test.js' 'furniture web/.env.example' 'furniture web/.env.production.example'
git commit -m "feat: gate storefront behavior tracking"
~~~

### Task 11: Add typed admin contracts and dynamic route identity

**Files:**
- Create: `yudao-ui-admin-vue3/src/api/mall/statistics/dashboard.ts`
- Modify: `yudao-ui-admin-vue3/src/config/furnitureLite.ts`
- Create: `yudao-ui-admin-vue3/scripts/check-dashboard-contract.mjs`
- Modify: `yudao-ui-admin-vue3/package.json`

**Interfaces:**
- Produces `getSummary`, `getTrend`, `getStageOverview`, `getAttention`, `getProductPage` and `exportProductExcel`.
- Produces `DashboardScope = 'SITE' | 'PRODUCT'` and nullable traffic/profit fields.

- [ ] **Step 1: Write a failing source contract**

~~~js
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const config = readFileSync(new URL('../src/config/furnitureLite.ts', import.meta.url), 'utf8');
const api = readFileSync(new URL('../src/api/mall/statistics/dashboard.ts', import.meta.url), 'utf8');
assert.match(config, /['"]\/dashboard['"]/);
for (const token of [
  "scope", "startDate", "endDate", "getStageOverview", "getAttention",
  "trafficDataStatus", "freshnessStatus", "exactCostItemCount", "knownCostAmount"
]) assert.ok(api.includes(token), "missing dashboard token: " + token);
~~~

- [ ] **Step 2: Implement exact API serialization**

Use inclusive `YYYY-MM-DD` dates. Do not send category/SPU in SITE. Export reuses scope, dates, filters, risk type and sorting but omits pagination. Add `check:dashboard` to package scripts.

- [ ] **Step 3: Run contract and type checks**

~~~powershell
pnpm check:dashboard
pnpm ts:check
~~~

Expected: both exit 0.

- [ ] **Step 4: Commit**

~~~powershell
git add 'yudao电商管理平台前后端/yudao-ui-admin-vue3/src/api/mall/statistics/dashboard.ts' 'yudao电商管理平台前后端/yudao-ui-admin-vue3/src/config/furnitureLite.ts' 'yudao电商管理平台前后端/yudao-ui-admin-vue3/scripts/check-dashboard-contract.mjs' 'yudao电商管理平台前后端/yudao-ui-admin-vue3/package.json'
git commit -m "feat: add scoped dashboard client contracts"
~~~

### Task 12: Build the FurnitureDashboard shell, filters, metrics and trend

**Files:**
- Create: `yudao-ui-admin-vue3/src/views/dashboard/index.vue`
- Create: `yudao-ui-admin-vue3/src/views/dashboard/components/DashboardFilters.vue`
- Create: `yudao-ui-admin-vue3/src/views/dashboard/components/DashboardMetricCard.vue`
- Create: `yudao-ui-admin-vue3/src/views/dashboard/components/DashboardTrendChart.vue`
- Create/Test: `yudao-ui-admin-vue3/src/views/dashboard/__tests__/FurnitureDashboardCore.test.ts`
- Modify: `yudao-ui-admin-vue3/src/styles/furniture-admin.scss`
- Modify: `yudao-ui-admin-vue3/scripts/check-dashboard-contract.mjs`

**Interfaces:**
- Vue component name is exactly `FurnitureDashboard`.
- This slice loads summary and trend only; the later stage/product slice plugs into the same query state.
- Profit fields are requested and rendered only with profit-query permission.

- [ ] **Step 1: Extend the failing core component contract**

Check `defineOptions({ name: 'FurnitureDashboard' })`, SITE/PRODUCT, today/yesterday/7/30/90, the existing Echart wrapper, four permission strings and absence of React/Radix/shadcn imports. Mount tests cover URL restoration, stale-response rejection and rendering a server conversion value above 100% without client-side clamping or cohort wording.

- [ ] **Step 2: Implement one URL-synchronized query state**

~~~ts
const query = reactive<DashboardQuery>({
  scope: 'SITE',
  startDate: dayjs().subtract(30, 'day').format('YYYY-MM-DD'),
  endDate: dayjs().subtract(1, 'day').format('YYYY-MM-DD'),
  compare: true
});
~~~

Today sets start/end to today, marks partial and disables compare by default. Switching to SITE clears category, SPU and risk type. Changing category clears a non-member SPU with a visible notice. Cancel old requests or discard them with per-loader sequence numbers.

- [ ] **Step 3: Implement metric/trend states and permissions**

Use null for “— / unavailable reason” and zero for confirmed no activity. Display currency, timezone, `asOf`, `snapshotId` and all source watermarks per block. Render coverage from `trafficDataStatus` and lag from separate `freshnessStatus`; `STALE` marks the block but retains the last successful value. Missing cost hides cost/profit/margin. PRODUCT never renders homepage series.

- [ ] **Step 4: Implement trend accessibility**

Unknown dates break the line and real zero touches the axis. Enable ECharts aria, provide a data-table alternative, announce refresh state with `aria-live`, preserve focus on retry and keep keyboard-visible controls at 200% zoom.

- [ ] **Step 5: Run and commit the core UI slice**

~~~powershell
pnpm check:dashboard
pnpm exec vitest run src/views/dashboard/__tests__/FurnitureDashboardCore.test.ts
pnpm ts:check
pnpm build:local
git add 'yudao电商管理平台前后端/yudao-ui-admin-vue3/src/views/dashboard' 'yudao电商管理平台前后端/yudao-ui-admin-vue3/src/styles/furniture-admin.scss' 'yudao电商管理平台前后端/yudao-ui-admin-vue3/scripts/check-dashboard-contract.mjs'
git commit -m "feat: build dashboard summary and trend"
~~~

### Task 13: Add stage, attention, product table and export UI

**Files:**
- Create: `yudao-ui-admin-vue3/src/views/dashboard/components/DashboardStageOverview.vue`
- Create: `yudao-ui-admin-vue3/src/views/dashboard/components/DashboardAttention.vue`
- Create: `yudao-ui-admin-vue3/src/views/dashboard/components/DashboardProductTable.vue`
- Create/Test: `yudao-ui-admin-vue3/src/views/dashboard/__tests__/FurnitureDashboardProduct.test.ts`
- Modify: `yudao-ui-admin-vue3/src/views/dashboard/index.vue`
- Modify: `yudao-ui-admin-vue3/src/styles/furniture-admin.scss`
- Modify: `yudao-ui-admin-vue3/scripts/check-dashboard-contract.mjs`

**Interfaces:**
- SITE loads stage plus all-product attention using the same dates and no category/SPU; PRODUCT loads stage, filtered attention and product-page.
- Normal/profit exports call their permission-separated endpoints and reuse normalized query state.

- [ ] **Step 1: Write failing product-slice component tests**

Cover both stage variants, no fabricated conversion/loss, SITE's initial all-product attention request (`scope=PRODUCT`, same dates, no category/SPU), attention-to-PRODUCT/table focus, evaluated-empty versus `notEvaluated` copy, profit-hidden behavior, sorting/pagination, formula-safe export endpoint selection, keyboard operation and old-response rejection per independent loader.

- [ ] **Step 2: Implement scope-aware stage size**

SITE shows homepage, detail, cart, checkout and buyer. PRODUCT shows detail, cart and buyer; homepage and checkout have `applicability=NOT_APPLICABLE` and null values. Display unit, dedupe scope and fixed `cohortAligned=false` wording; never compute stage conversion or “largest loss.”

- [ ] **Step 3: Implement attention, product table and exports**

On SITE, attention is labeled “all-product attention” and calls the product-rule endpoint with the current dates but no category/SPU; it never applies SITE traffic as a product filter. Attention cards render `items[]` hit counts, while a separate partial-evaluation notice renders each top-level `notEvaluated[]` reason and never masquerades as zero. Show “no items matched the current rules” only when every rule the user is permitted to see was evaluated, `notEvaluated` is empty and `items` is empty; otherwise show a partial/unavailable evaluation explanation. Clicking a hit switches to PRODUCT, sets `riskType`, reloads and focuses the table. In PRODUCT, attention follows category/SPU. The table uses fixed columns, server-whitelisted sorting, risk views and permission-aware profit columns. Normal export never requests profit; profit export is offered only with profit-export permission.

- [ ] **Step 4: Verify responsive and accessible behavior**

Verify keyboard flow, visible focus, `aria-live` refresh status, focus-preserving retries, table/chart alternatives, 200% zoom and 1440/1280/1024/768/390 widths.

- [ ] **Step 5: Run and commit the product UI slice**

~~~powershell
pnpm check:dashboard
pnpm exec vitest run src/views/dashboard/__tests__/FurnitureDashboardProduct.test.ts
pnpm ts:check
pnpm build:local
git add 'yudao电商管理平台前后端/yudao-ui-admin-vue3/src/views/dashboard' 'yudao电商管理平台前后端/yudao-ui-admin-vue3/src/styles/furniture-admin.scss' 'yudao电商管理平台前后端/yudao-ui-admin-vue3/scripts/check-dashboard-contract.mjs'
git commit -m "feat: add dashboard product operations"
~~~

### Task 14: Run cross-system acceptance and release gates

**Files:**
- Modify only defects proven by the gates below.
- Use the revised overall design as the source of truth.
- Record execution evidence in the release ticket, not in repository secrets.

**Interfaces:**
- Produces a release candidate with tracking still disabled.
- Enables gateway and frontend tracking only after the explicit canary decision.

- [ ] **Step 1: Apply schema twice and run bounded backfill**

Verify migration idempotence, duplicate preflight, batch checkpoints, nullable profit columns, four permissions, the four exact stopped job records/handlers/parameters/Cron expressions, disabled legacy `productStatisticsJob` and no core-table row loss.

- [ ] **Step 2: Load a fixed two-tenant, cross-midnight dataset**

Assert SITE/PRODUCT separation; payment-day and refund-day attribution; exact/estimated/missing counts; null incomplete profit; refund-only stock unchanged; return/refund stock restored; unknown traffic null; real zero zero; today+yesterday and seven-day repair convergence.

- [ ] **Step 3: Verify gateway and server-origin cart behavior**

Prove app route reachability only through the gateway, exact Origin enforcement, spoofed tenant overwrite, internal-header removal, request limit, dedicated rate keys, public ADD rejection, committed cart event creation and cart success during Statistics outage.

- [ ] **Step 4: Run all automated gates fresh**

~~~powershell
Set-Location 'D:\code\furniture web'
npm test
npm run build

Set-Location 'D:\code\yudao电商管理平台前后端\yudao-cloud'
mvn -pl yudao-gateway,yudao-module-mall/yudao-module-trade-server,yudao-module-mall/yudao-module-statistics-server -am -DskipITs test

Set-Location 'D:\code\yudao电商管理平台前后端\yudao-ui-admin-vue3'
pnpm check:dashboard
pnpm ts:check
pnpm build:prod
~~~

Expected: every command exits 0 under Java 8.

- [ ] **Step 5: Perform permission, privacy and accessibility acceptance**

Test base query, profit query, normal export and profit export separately. Confirm HMAC key material and raw IDs never enter logs, mixed-version tenant/day is rejected, old key retention is recorded, exports escape formulas, and SITE/PRODUCT/stage/attention copy is accurate. In a consent-required run, prove pre-consent causes zero event requests and zero persistent IDs, and confirm the dashboard discloses the coverage bias.

- [ ] **Step 6: Canary enablement**

Keep `VITE_BEHAVIOR_TRACKING_ENABLED=false` until the Statistics service, gateway Host+Origin mapping, tenant 121, HMAC keys, enabled-tenant list, timezone, USD currency and alerts are verified. Reject release configuration when `VITE_ANALYTICS_VISITOR_TTL_DAYS` is invalid, above 180 or above `DASHBOARD_EVENT_RETENTION_DAYS`. Enable one consented furniture canary, observe at least two current-window runs and one server-cart event, then expand. Roll back first by disabling the frontend flag and gateway route; retain schema and raw data.

- [ ] **Step 7: Review the final diff without an empty commit**

~~~powershell
git diff --check
git status --short
git diff --stat
~~~

Expected: no credentials, generated build output or unrelated `vite.config.ts` changes. If no verified defects remain, Task 14 is complete without an empty commit.

## Spec Coverage Checklist

- Java 8 and real repository APIs: Tasks 1–14.
- SITE/PRODUCT isolation, scope-specific stage size and PRODUCT trend exclusion: Tasks 6–8 and 11–13.
- Payment/refund day attribution and today+yesterday/7-day repair: Task 6.
- Exact/estimated/missing cost rows and null incomplete profit: Tasks 1, 6, 7 and 12–13.
- Cost snapshot and refund-only inventory correction: Task 2.
- Gateway app route, site tenant binding and dedicated rate keys: Tasks 3–4.
- HMAC versioning, natural-day rotation and production configuration: Task 4.
- Normal insert plus duplicate-key-only idempotence: Task 4.
- Server-origin ADD_TO_CART and nonblocking failure: Task 5.
- Frontend feature flag, consent/no-cookieless behavior and session rotation: Task 10.
- Component name `FurnitureDashboard` and split permissions: Tasks 1, 7, 9 and 11–13.
- Explainable attention items: Tasks 8 and 13.
- Migration, H2/SQL integration, privacy, accessibility and canary release: Tasks 1 and 14.
