# Fulfillment Aggregate Version Guards Implementation Plan

**Goal:** Make package and leg mutations honor the shipment aggregate version required by the future admin API, without weakening existing idempotency or transaction behavior.

**Scope:** `addPackage` and `addLeg` only. Manual tracking events are a separate Task 7B. No HTTP controller is added here.

## Contract

- Add mandatory `expectedVersion` to `UpsertPackageCommand` and `AddShipmentLegCommand`.
- Include `expectedVersion` in the canonical request hash. Reusing an idempotency key with a different version is a conflict.
- After the idempotency record is resolved, lock the tenant-scoped shipment, require `DRAFT`, and compare `expectedVersion` with the persisted shipment version.
- Insert the package or leg, then increment shipment version through a tenant-scoped `WHERE id=? AND version=?` update. A zero-row update raises the existing fulfillment version-conflict error and rolls back the child insert and idempotency completion.
- A successful mutation advances shipment version by exactly one. An exact idempotency replay returns the original resource ID without incrementing version again, even if the request version is now stale.
- A stale request with a new idempotency key fails without inserting a child row.
- Preserve tenant isolation, package ownership checks, vocabulary validation, sensitive-field handling, and duplicate-tracking translation.

## TDD Steps

1. Extend dispatch service and real H2 transaction tests with successful version increments, stale-version rejection, exact replay, hash conflict, and child-insert rollback when the version CAS fails.
2. Run the focused tests and record RED.
3. Implement command, hashing, mapper CAS, and service validation/update changes.
4. Run Task 5/6 command, transaction, atomicity, persistence, state-machine, provider, and legacy-order regressions.
5. Run `git diff --check`, commit on the isolated task branch, and submit for independent review.
