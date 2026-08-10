# North America Fulfillment Phase 1 Rollout Runbook

## 1. Scope and non-goals

This rollout supports domestic fulfillment inside the United States (`US -> US`) and Canada (`CA -> CA`). It introduces the fulfillment aggregate, admin APIs, safe legacy tracking projection, and an approval-driven legacy-order migration path.

The following are not supported in this phase:

- China-to-North-America transport;
- United States/Canada cross-border transport in either direction;
- routes that start or end outside the United States or Canada;
- a production carrier/provider adapter, webhook, or polling integration;
- automatic inference of country, timezone, warehouse, or migration provider.

The existing admin and App legacy tracking endpoints remain the fallback contract. Enabling the new model does not authorize exposing provider payloads or operational details to customers.

## 2. Preconditions and ownership

This runbook is executable only from one fully integrated release revision that contains the V023 migration-fact
schema, the legacy projection, the bounded migration service/job, the transactional writer, and both migration
feature-guard checks. A configuration/runbook-only revision or a revision assembled from only some task branches is
not deployable. Build, test, record, and deploy the same immutable revision; do not rehearse one revision and execute
another.

Before any rollout window:

1. Assign a rollout owner, database owner, security approver, operations approver, and rollback decision-maker.
2. Back up the target database and prove that the backup can be restored in a disposable environment.
3. Confirm the migration catalog is contiguous from V001 through V023. Published V015 through V022 files are immutable.
4. Store the production HMAC secret in the deployment secret manager. Do not put it in Git, tickets, terminal transcripts, screenshots, or this runbook.
5. Register and approve the tenant's carrier, provider, warehouse, and V023 per-order migration facts. A row is approval evidence, not a place to guess missing facts.
6. Decide which roles receive the five V022 permissions. V022 creates permissions only; it does not assign them to roles.
7. Confirm there is no real provider adapter in this phase. `mock` is allowed only in the `local` and `unit-test` profiles and must never be selected in production.

Stop the rollout if any precondition is missing.

## 3. Feature flags and dependencies

All production flags default to `false`.

| Full property | Production default | Local profile | Purpose and dependencies |
|---|---:|---:|---|
| `yudao.trade.fulfillment.enabled` | `false` | `true` | Master switch for every new admin fulfillment read and write API |
| `yudao.trade.fulfillment.write-new-model` | `false` | `true` | Allows command and tracking mutations; requires `yudao.trade.fulfillment.enabled=true`, a nonblank HMAC secret, and a provider code that resolves to a registered client at startup |
| `yudao.trade.fulfillment.read-from-new-model` | `false` | `false` | Controls only whether legacy order-tracking endpoints try the safe new-model projection before their old-provider fallback; new admin fulfillment reads depend on `enabled`, not this flag |
| `yudao.trade.fulfillment.customer-ui-enabled` | `false` | `false` | Reserved for a future customer UI; requires both `yudao.trade.fulfillment.enabled=true` and `yudao.trade.fulfillment.read-from-new-model=true` |
| `yudao.trade.fulfillment.legacy-migration-write-enabled` | `false` | `false` | Allows non-dry-run legacy migration; requires both `yudao.trade.fulfillment.enabled=true` and `yudao.trade.fulfillment.write-new-model=true` |
| `yudao.trade.fulfillment.provider-code` | `${FULFILLMENT_PROVIDER_CODE:}` | `mock` | Provider selection; production must use an approved real provider code |
| `yudao.trade.fulfillment.idempotency-hmac-key` | `${FULFILLMENT_IDEMPOTENCY_HMAC_KEY:}` | local-only value | HMAC secret for cache/idempotency domains; production value must come from the secret manager |

The HMAC secret may be blank only while all new-model writes are disabled. In that state, old express queries remain available and deliberately bypass the cache. Do not replace the missing secret with a weak, random-process, MD5, SHA-256, or plaintext-derived cache key.

When writes are enabled, startup resolves `provider-code` through the provider registry and fails closed if the registry or client is absent. The mock client is registered only when the active-profile set is nonempty and every active profile is either `local` or `unit-test`; mixed sets such as `prod,local` and `prod,unit-test` never register it, even when another provider is selected.

The environment variables used by shared configuration are:

```text
FULFILLMENT_PROVIDER_CODE
FULFILLMENT_IDEMPOTENCY_HMAC_KEY
```

Record only whether each variable was supplied and rotated. Never record its value.

## 4. Approving or reapproving a V023 migration fact

One absolute fact row exists for each `(tenant_id, order_id)`. Reapproval updates that row; logical deletion does not permit a second row. The approving operator must verify the order evidence, domestic route, IANA timezones, enabled tenant warehouse, and enabled tenant provider before executing the template.

Use session variables or an approved database tool. Values below are placeholders and contain no credentials or customer data:

```sql
SET @tenant_id = <tenant-id>;
SET @order_id = <order-id>;
SET @origin_country = '<US-or-CA>';
SET @destination_country = '<same-as-origin-country>';
SET @origin_timezone = '<approved-IANA-timezone>';
SET @destination_timezone = '<approved-IANA-timezone>';
SET @warehouse_id = <enabled-tenant-warehouse-id>;
SET @migration_provider_id = <enabled-tenant-provider-id>;
SET @approved_by = <approving-operator-id>;
SET @source_reference = 'change-ticket:<approved-ticket-id>';

INSERT INTO trade_fulfillment_legacy_migration_fact (
    tenant_id, order_id, origin_country, destination_country,
    origin_timezone, destination_timezone, warehouse_id,
    migration_provider_id, approved_by, approved_at, source_reference,
    creator, updater, deleted
) VALUES (
    @tenant_id, @order_id, @origin_country, @destination_country,
    @origin_timezone, @destination_timezone, @warehouse_id,
    @migration_provider_id, @approved_by, CURRENT_TIMESTAMP(6), @source_reference,
    CAST(@approved_by AS CHAR), CAST(@approved_by AS CHAR), b'0'
)
ON DUPLICATE KEY UPDATE
    origin_country = VALUES(origin_country),
    destination_country = VALUES(destination_country),
    origin_timezone = VALUES(origin_timezone),
    destination_timezone = VALUES(destination_timezone),
    warehouse_id = VALUES(warehouse_id),
    migration_provider_id = VALUES(migration_provider_id),
    approved_by = VALUES(approved_by),
    approved_at = VALUES(approved_at),
    source_reference = VALUES(source_reference),
    updater = VALUES(updater),
    deleted = b'0';
```

Do not place a tracking number, recipient phone, address, provider payload, secret, signature, or HMAC input/digest in `source_reference`.

## 5. Static migration verification

From the repository root:

```powershell
cd 'D:\code\furniture web'
npm.cmd run verify:db-migrations
```

The command must report the repository's complete contiguous catalog and deterministic latest baseline. Resolve any gap or checksum mismatch before touching a database. The former `invoke-local-migrations.ps1` writer is retired; the candidate `yudao-server.jar` and its packaged Flyway resources are the only migration executor.

## 6. Disposable MySQL rehearsal

Use a named temporary database, never a shared development or production database. The example assumes the approved local Docker Compose stack is already running and reads its password from the operator's environment.

```powershell
$rehearsalDatabase = 'oakved_fulfillment_phase1_rehearsal'
$compose = 'D:\code\yudao电商管理平台前后端\yudao-cloud\script\docker\docker-compose-local-infra.yml'
docker compose -f $compose exec -T mysql mysql -uroot "-p$env:MYSQL_ROOT_PASSWORD" -e "CREATE DATABASE $rehearsalDatabase CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
$jdbc = "jdbc:mysql://127.0.0.1:3306/$rehearsalDatabase?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
java.exe -jar 'D:\code\yudao电商管理平台前后端\yudao-cloud\yudao-server\target\yudao-server.jar' `
  --spring.profiles.active=local --server.port=48081 `
  "--spring.datasource.dynamic.datasource.master.url=$jdbc" `
  --spring.datasource.dynamic.datasource.master.username=root `
  "--spring.datasource.dynamic.datasource.master.password=$env:MYSQL_ROOT_PASSWORD" `
  "--spring.datasource.dynamic.datasource.slave.url=$jdbc" `
  --spring.datasource.dynamic.datasource.slave.username=root `
  "--spring.datasource.dynamic.datasource.slave.password=$env:MYSQL_ROOT_PASSWORD"
```

Wait for the Flyway success and application-ready messages, then stop this rehearsal process before querying the database.

Verify the ledger and critical objects without selecting customer-facing columns:

```sql
SELECT installed_rank, version, script, success FROM flyway_schema_history ORDER BY installed_rank;
SELECT COUNT(*) AS fact_table_present
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'trade_fulfillment_legacy_migration_fact';
WITH expected(permission) AS (
    SELECT 'trade:fulfillment:shipment:query' UNION ALL
    SELECT 'trade:fulfillment:shipment:create' UNION ALL
    SELECT 'trade:fulfillment:shipment:update' UNION ALL
    SELECT 'trade:fulfillment:shipment:dispatch' UNION ALL
    SELECT 'trade:fulfillment:tracking:manual'
), actual AS (
    SELECT DISTINCT permission
    FROM system_menu
    WHERE permission LIKE 'trade:fulfillment:%' AND deleted = b'0'
)
SELECT expected.permission,
       CASE WHEN actual.permission IS NULL THEN 'MISSING' ELSE 'PRESENT' END AS verification
FROM expected
LEFT JOIN actual ON actual.permission = expected.permission
UNION ALL
SELECT actual.permission, 'UNEXPECTED'
FROM actual
LEFT JOIN expected ON expected.permission = actual.permission
WHERE expected.permission IS NULL
ORDER BY permission;
```

The permission query must return exactly five rows and every row must be `PRESENT`. Any `MISSING` or `UNEXPECTED` row fails the rehearsal; do not continue with a broad `LIKE` count as evidence.

Run the migration catalog tests and the fulfillment regression suite against the code revision being deployed. Clean up the temporary database only after the owner has recorded the successful commands, exit codes, migration count, and test totals:

```powershell
docker compose -f $compose exec -T mysql mysql -uroot "-p$env:MYSQL_ROOT_PASSWORD" -e "DROP DATABASE $rehearsalDatabase;"
```

Never run the cleanup command against a computed or unverified database name.

## 7. Legacy migration rehearsal and execution

The XXL-Job handler is `fulfillmentLegacyMigrationJob`. Each invocation processes one bounded page and never loops internally. Tenant identity comes from the server's tenant job context; do not include a tenant-like field in the parameter.

Start with `yudao.trade.fulfillment.write-new-model=false` and
`yudao.trade.fulfillment.legacy-migration-write-enabled=false`, then submit one dry-run page:

```json
{"afterOrderId":0,"limit":25,"dryRun":true}
```

The allowed range is `1..100`; defaults are cursor `0`, limit `100`, and `dryRun=true`. Record only aggregate counts, next cursor, `hasMore`, and reason counts. Dry-run must not allocate numbers, lock rows for writing, write any table, or call a provider.

Review every reason category. In particular, no row may be written for missing/stale/deleted facts, cross-border facts, missing warehouse/provider references, invalid carrier mapping, conflicting tracking identity, or an existing fulfillment aggregate. Correct source data or reapprove V023 facts; do not bypass the evaluator.

Persist the returned `nextAfterOrderId` in the approved rollout record. Do not reuse a cursor from a different tenant or code revision.

After the dry-run has been approved:

1. Set `yudao.trade.fulfillment.enabled=true` and `yudao.trade.fulfillment.write-new-model=true` with the real provider code and managed HMAC secret.
2. Set `yudao.trade.fulfillment.legacy-migration-write-enabled=true` for the bounded write window.
3. Invoke one small write page, for example:

```json
{"afterOrderId":0,"limit":10,"dryRun":false}
```

4. Verify aggregate counts and replay the exact page. Exact replay must not create duplicates.
5. Continue one cursor page at a time only while error and reason counts remain within the approved threshold.
6. Set `yudao.trade.fulfillment.legacy-migration-write-enabled=false` immediately after the window.

Every non-dry-run invocation is checked twice: once before the tenant job calls the migration service and again at
the first line of the transactional writer, before HMAC calculation, row locks, or writes. Either disabled boundary
returns the stable `FULFILLMENT_FEATURE_DISABLED` service error. Do not catch, translate, or bypass that error in an
operator wrapper.

A failure in one order must roll back that order only; it must not roll back an earlier committed order or leave a partial aggregate.

## 8. Smoke checklist

Perform these checks with synthetic, approved test records only:

- Actuator health is `UP` and database connectivity is healthy.
- A role without V022 permissions cannot read or mutate fulfillment; an explicitly assigned staff role can perform only its granted operations.
- One `US -> US` and one `CA -> CA` domestic shipment can be created.
- `US -> CA`, `CA -> US`, China-to-North-America, and third-country routes are rejected.
- Repeating an identical write with the same idempotency key returns the original outcome without duplicate rows; changing the payload conflicts.
- With no shipment or no subject event, the legacy endpoint falls back to the old provider path.
- With an existing subject event that is unsafe or unmapped, the legacy endpoint returns authoritative empty and performs no provider call.
- A manual event records the approved audit fields and does not expose the reason or trace through the legacy projection.
- A migrated order replay creates no duplicate shipment, item, package, leg, event, idempotency, or outbox row.
- Setting `yudao.trade.fulfillment.read-from-new-model=false` restores the old legacy read path without deleting new data; while `enabled=true`, the three new admin read APIs remain available.

## 9. Observation and safe diagnostics

This phase does not add fulfillment Micrometer counters. Do not claim that a dedicated fulfillment dashboard or counter exists. Use Actuator health, fixed-field safe logs, and count-only database queries.

```sql
-- Outbox backlog by state; no payload is selected.
SELECT status, COUNT(*) AS event_count, MIN(next_attempt_at) AS oldest_due_at
FROM trade_fulfillment_outbox_event
WHERE deleted = b'0'
GROUP BY status;

-- Stale in-progress idempotency operations; no request hash or result is selected.
SELECT operation, COUNT(*) AS stale_count
FROM trade_fulfillment_idempotency
WHERE deleted = b'0'
  AND status = 'PROCESSING'
  AND update_time < CURRENT_TIMESTAMP - INTERVAL 15 MINUTE
GROUP BY operation;

-- Unknown provider mappings; no raw provider status is selected.
SELECT tenant_id, COUNT(*) AS unknown_mapping_count
FROM trade_tracking_event
WHERE deleted = b'0' AND mapping_known = b'0'
GROUP BY tenant_id;

-- Shipment exceptions by tenant and status only.
SELECT tenant_id, status, COUNT(*) AS shipment_count
FROM trade_shipment
WHERE deleted = b'0' AND status = 'DELIVERY_EXCEPTION'
GROUP BY tenant_id, status;

-- Invalid shipment enum values; a healthy result is zero rows.
SELECT status, COUNT(*) AS invalid_status_count
FROM trade_shipment
WHERE deleted = b'0'
  AND status NOT IN (
    'DRAFT', 'READY_TO_SHIP', 'HANDED_TO_CARRIER', 'IN_TRANSIT',
    'AT_LOCAL_TERMINAL', 'APPOINTMENT_REQUIRED', 'APPOINTMENT_CONFIRMED',
    'OUT_FOR_DELIVERY', 'DELIVERED', 'DELIVERY_EXCEPTION',
    'RETURNING', 'RETURNED', 'CANCELED'
  )
GROUP BY status;
```

Allowed log fields are fixed provider label, HTTP status, elapsed milliseconds, operation, aggregate ID where already approved, reason code, and count/cursor metadata. Do not log provider request/response bodies, raw payloads, tracking number, phone, address, signature, secret, exception body, HMAC input, or digest.

## 10. Enable sequence

Use this order and stop after each step for verification:

1. Deploy V015-V023 and the application code; configure the managed HMAC secret and real provider code.
2. Set `yudao.trade.fulfillment.enabled=true` while keeping `yudao.trade.fulfillment.read-from-new-model=false`, `yudao.trade.fulfillment.customer-ui-enabled=false`, and `yudao.trade.fulfillment.legacy-migration-write-enabled=false`.
3. Set `yudao.trade.fulfillment.write-new-model=true` for a small synthetic/admin write cohort and verify idempotency/outbox health.
4. Run migration dry-run pages. Set `yudao.trade.fulfillment.legacy-migration-write-enabled=true` only for approved bounded writes, then set `yudao.trade.fulfillment.legacy-migration-write-enabled=false`.
5. Set `yudao.trade.fulfillment.read-from-new-model=true` for staff-facing legacy projection and verify fallback/authoritative-empty behavior.
6. Keep `yudao.trade.fulfillment.customer-ui-enabled=false` in Phase 1. A later approved customer UI release may set `yudao.trade.fulfillment.customer-ui-enabled=true` only after `yudao.trade.fulfillment.read-from-new-model=true`.

## 11. Disable sequence

Disable in the reverse risk order:

1. Set `yudao.trade.fulfillment.customer-ui-enabled=false`.
2. Set `yudao.trade.fulfillment.read-from-new-model=false` to restore legacy reads.
3. Set `yudao.trade.fulfillment.legacy-migration-write-enabled=false`.
4. Stop provider ingestion and set `yudao.trade.fulfillment.write-new-model=false`.
5. Set `yudao.trade.fulfillment.enabled=false` after confirming no in-flight approved write remains.

Retain the HMAC secret while replay, cache validation, rollback investigation, or audit evidence may still depend on it. Rotate through the secret manager; never blank or publish it as an emergency action.

## 12. Rollback rules

- Never edit or drop V015-V023 after publication.
- Never delete new fulfillment, idempotency, event, outbox, mapping, or V023 fact rows to simulate rollback.
- Restore old reads with flags, not schema reversal.
- Stop new writes and migration writes through their flags before disabling the master switch.
- Preserve idempotency and audit evidence so an exact replay remains explainable.
- If code rollback is required, deploy a revision that understands the already-applied schema. Do not restore an old database backup over newer production writes without the database owner and incident commander.

## 13. Evidence record

Attach this sanitized record to the approved change ticket:

```text
Code revision:
Migration catalog result and count:
Disposable rehearsal database name:
Backup/restore evidence reference:
Test commands, exit codes, and totals:
Tenant identifier:
Flag state before/after (booleans only):
Provider code approval reference (no credentials):
HMAC configured/rotated (yes/no only):
V023 approval ticket references:
Dry-run cursor, counts, hasMore, and reason counts:
Write cursor, counts, hasMore, and reason counts:
Smoke checklist result:
Actuator health result:
Outbox/stale/unknown/exception count summary:
Rollback decision and approver:
```

The evidence record must not contain a secret, signature, provider request/response, raw payload, tracking number, recipient phone, address, HMAC input/digest, or customer-identifying order content.
