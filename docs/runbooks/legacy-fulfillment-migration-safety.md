# Legacy fulfillment migration safety

`LEGACY_ORDER_MIGRATION` is a permanent idempotency domain, not a short-lived API retry record.

- Retention or cleanup jobs must never delete rows whose `trade_fulfillment_idempotency.operation` is
  `LEGACY_ORDER_MIGRATION`, including completed rows with the sentinel year-9999 expiry.
- Keep the HMAC key version that created these rows available for replay verification. Rotate it only with an
  explicit compatibility plan; deleting the rows or silently changing the key can create a second fulfillment
  aggregate for the same legacy order.
- A write run must remain disabled until dry-run exceptions are reconciled. Stop a write run by disabling the
  migration-write flag; do not remove migration shipments, events, summaries, outbox rows, or idempotency rows.
- Tracking-number collisions are reported only as `TRACKING_CONFLICT`. Logs and batch output must not include the
  tracking number, phone number, address, or provider payload.

The per-order writer locks the order first and then uses this fixed lock order: approved facts, carrier, order
items by ID, warehouse, provider, existing shipments by ID, and carrier/tracking key. Changing this order requires
a real-MySQL concurrency regression test.
