# Mall ERP Orders and Fulfillment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create idempotent ERP sales orders from paid mall orders, synchronize fulfillment, and synchronize returns with exactly-once stock effects.

**Architecture:** Publish durable integration events after mall transaction commits, consume them through a retryable integration service, and persist order mappings plus sync logs. Payment facts stay in the mall/payment modules; ERP owns fulfillment and physical stock movements.

**Tech Stack:** Java 8, Spring Boot events, MyBatis Plus, MySQL 8, scheduled retry jobs, JUnit 5, Mockito, Vue 3, Vitest.

## Global Constraints

- Phase-one product mappings and ERP-backed stock must be complete first.
- A paid mall order creates at most one ERP sale order.
- ERP cannot alter payment status.
- A return can increase physical stock at most once.
- Integration failure never reverses a successful payment automatically.
- All data is tenant-scoped and sensitive member/payment data is excluded from logs.

---

### Task 1: Order Mapping and Durable Event Schema

**Files:**
- Modify: `yudao电商管理平台前后端/yudao-cloud/sql/mysql/mall-erp-integration.sql`
- Create order mapping and outbox DO/mapper classes in ERP integration packages.
- Test schema and mapper uniqueness.

- [ ] Write failing tests for unique `(tenant_id, mall_order_id)` and `(tenant_id, erp_sale_order_id)` plus unique event idempotency key.
- [ ] Add `mall_erp_order_mapping` and `mall_erp_outbox_event` with statuses `PENDING`, `PROCESSING`, `SUCCEEDED`, `FAILED`.
- [ ] Add tenant-scoped mappers and optimistic version fields.
- [ ] Run focused tests and import migration twice.
- [ ] Commit with `feat: persist mall ERP order integration events`.

### Task 2: Publish Paid-Order Events After Commit

**Files:**
- Modify trade order payment-success service/listener.
- Add `MallOrderPaidIntegrationEvent` and publisher.
- Test payment success, duplicate callback and rollback behavior.

- [ ] Write failing tests proving the event is persisted only after a successful payment transition and duplicate payment callbacks reuse the same idempotency key `tenantId:orderId:MALL_ORDER_PAID`.
- [ ] Implement after-commit outbox publication without direct ERP controller calls.
- [ ] Verify transaction rollback produces no consumable event.
- [ ] Run trade tests and commit with `feat: publish paid mall orders for ERP sync`.

### Task 3: ERP Customer and Sale Order Creation

**Files:**
- Add ERP integration order API/service, DTOs and tests.
- Use existing `ErpCustomerService` and `ErpSaleOrderService`.

- [ ] Write failing tests for customer match/create, 26-product mapping lookup, price/count conversion, missing mapping failure and duplicate-event replay.
- [ ] Implement minimum-data customer creation and ERP sale-order construction from mall order items.
- [ ] Persist mapping before marking the outbox event successful.
- [ ] On retry, return the existing ERP order instead of creating another.
- [ ] Run focused tests and commit with `feat: create ERP sales orders from mall payments`.

### Task 4: Retry, Recovery and Admin Operations

**Files:**
- Add scheduled outbox consumer, retry policy and admin retry endpoints.
- Add sync-log admin page/actions.

- [ ] Write failing tests for bounded exponential retry, stale `PROCESSING` recovery, terminal failure and manual retry.
- [ ] Implement retry counts and next-at timestamps; exclude secrets and full addresses from stored payload summaries.
- [ ] Add admin list/detail/retry permissions.
- [ ] Run backend and admin tests; commit with `feat: retry failed mall ERP order sync`.

### Task 5: ERP Fulfillment State Back to Mall

**Files:**
- Add ERP sale-order/outbound state integration listener/service.
- Extend mall order response DTO with an ERP fulfillment summary without changing payment status fields.
- Update `furniture web/src/services/yudaoClient.js` mapping and order pages.
- Add Java and Vitest tests.

- [ ] Write failing tests mapping ERP draft/audited/outbound/void states to mall fulfillment states.
- [ ] Implement versioned, idempotent state updates; reject stale events.
- [ ] Expose ERP order number, fulfillment label and last update time in mall order APIs.
- [ ] Update account/order pages to display the summary.
- [ ] Run focused Java/Vitest tests and commit with `feat: show ERP fulfillment in mall orders`.

### Task 6: Refund, Return and Exactly-Once Stock Recovery

**Files:**
- Add mall after-sale integration event and ERP sale-return service adapter.
- Add return mapping fields/table if separate from order mapping.
- Test refund-only, return-required, duplicate callbacks and void behavior.

- [ ] Write failing tests: unshipped refund creates no ERP return; shipped return creates one ERP sale return; completed return adds stock once; duplicate completion adds none; void does not add stock.
- [ ] Implement after-sale event publication and ERP return creation from mapped order items.
- [ ] Use ERP sale-return inbound completion as the single physical-stock recovery trigger.
- [ ] Synchronize resulting sellable stock through phase-one stock integration.
- [ ] Run focused tests and commit with `feat: synchronize mall returns with ERP stock`.

### Task 7: Full Failure-Mode and Browser Verification

- [ ] Run all ERP, product, trade, member and payment Java tests.
- [ ] Run storefront `npm.cmd test` and `npm.cmd run build`.
- [ ] Bootstrap tenant `121`, audit product mappings and seed one test member/order.
- [ ] Verify a successful storefront checkout creates one ERP sale order.
- [ ] Replay payment notification and verify ERP order count remains one.
- [ ] Complete ERP outbound and verify mall order fulfillment changes.
- [ ] Process a shipped return and verify ERP/mall stock rises once.
- [ ] Stop ERP processing temporarily, pay an order, restore processing and verify retry succeeds without changing payment truth.
- [ ] Run final audit for orphan mappings, duplicate ERP orders, stuck events and stock divergence; all counts must be zero.
- [ ] Commit any verification fixes with scoped messages.
