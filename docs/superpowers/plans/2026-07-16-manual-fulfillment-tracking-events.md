# Manual Fulfillment Tracking Events Implementation Plan

**Goal:** Add an audited, idempotent manual tracking correction path for authorized administrators without impersonating a carrier event or bypassing shipment concurrency controls.

**Architecture:** Extend the fulfillment tracking service with a separate `applyManualEvent` command. It reuses Task 7's state machine, three-part watermarks, shipment/package/leg projection, summary calculation, and safe outbox rules, but bypasses provider-status mapping and records explicit operator audit fields. HTTP endpoints remain Task 8 scope.

## Persistence: append-only V019

V018 is allocated to the fulfillment active-record uniqueness correction. Create
`V019__trade_manual_tracking_audit.sql`; do not modify V015-V018.

Add nullable columns to `trade_tracking_event`:

- `manual_operator_id BIGINT`
- `manual_reason VARCHAR(500)`
- `request_trace_id VARCHAR(64)`

Regenerate the deterministic baseline and update Node migration contracts and the H2 fixture. `TrackingEventDO.toString()` must exclude reason and trace ID. Existing provider events leave all three columns null.

## Service contract

```java
TrackingApplyResult applyManualEvent(String idempotencyKey,
                                    ApplyManualTrackingEventCommand command);
```

The command contains tenant ID, shipment ID, optional package ID, required shipment-leg ID, requested `ShipmentStatusEnum`, occurred-at `Instant`, expected shipment version, operator ID, reason, and request trace ID. Sensitive/free-text fields are excluded from `toString()`.

Rules:

- `reason` is trimmed and must contain 5-500 characters; operator, trace, leg, requested status, time, version, and idempotency key are mandatory.
- The leg is loaded by tenant and shipment. A bound leg derives the effective package ID from the database; an optional command package must match. A shipment-level leg requires command package ID null. Provider/carrier facts are derived from the leg, never accepted from HTTP.
- Resolve idempotency before stale-version validation. Store only the HMAC key digest. Canonical request hash includes tenant/shipment/effective package/leg/requested status/microsecond occurred-at/expected version/operator/reason, but excludes trace ID so a network retry with a new trace remains a replay.
- Exact replay returns the original event result without a second version increment or outbox. Same key with a different canonical request is a conflict.
- Lock the tenant shipment and require `shipment.version == expectedShipmentVersion`. Provider mapping is not called. Persist `source=MANUAL`, provider status constants `MANUAL`, requested internal status, mapping fields null/not applicable, and the structured audit columns.
- Manual tracking event identity is the operation-scoped HMAC idempotency digest; never persist the raw key.
- Status priority is assigned server-side from a fixed manual priority table and cannot be supplied over HTTP. Manual events still obey the shipment state machine, terminal protection, full `(occurredAt, priority, internalEventId)` ordering, and the Task 7 effective-outcome gate.
- Status-specific outbox events depend only on the actual Shipment transition. Outbox payloads contain internal IDs and status only; never reason, trace, tracking, location, address, phone, or raw request content.
- Timeline insert, manual audit fields, idempotency completion, package/leg/shipment watermarks and versions, order summary, and outbox commit or roll back together.

## TDD steps

1. Add Node RED tests for V019 continuity, immutable V015-V018, baseline equivalence, exact audit columns, and no secret/credential fields.
2. Add service RED tests for reason boundaries, missing operator/trace/leg/version, cross-tenant and parent mismatch, shipment-level versus bound-leg derivation, stale version, exact replay, conflicting reuse, state-machine rejection, terminal protection, same-time manual priority, and sensitive `toString()` output.
3. Add real H2 RED tests proving success, event/audit/watermark/summary/outbox atomicity, stale-version no-op, and forced outbox rollback.
4. Implement V019, DO fields, command, canonical hashing, service method, mapper support, and safe outbox audit event.
5. Run manual-event tests plus Task 5/6/6B/7 regressions and Node migration tests.
6. Run `git diff --check`, commit on an isolated branch, and submit to independent review.

## Acceptance

- No manual request is converted into a fake provider mapping.
- Every accepted correction is attributable to tenant, operator, reason, trace, and immutable timeline event.
- Stale writes and conflicting idempotency reuse fail closed.
- Terminal or illegal transitions remain timeline-only and cannot corrupt shipment/summary state.
- API access logging, permissions, request VOs, and controller validation are implemented in Task 8, not here.
