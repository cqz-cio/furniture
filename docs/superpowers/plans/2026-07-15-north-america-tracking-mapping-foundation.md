# North America Tracking Mapping Foundation (Task 7A)

**Goal:** Close the gap between design section 8.4 and tracking ingestion by adding durable, versioned provider-status mappings and replayable mapping/transition audit fields before Task 7 service code is written.

**Dependency:** May be implemented after Task 4. It must be integrated and verified before Task 7. It does not add any real provider mapping data.

## Files

- Create `yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations/V016__trade_tracking_status_mapping.sql`.
- Modify `yudao电商管理平台前后端/yudao-cloud/sql/mysql/oakved-baseline.sql` through the repository baseline workflow.
- Modify database migration/safety tests under `furniture web/tests`.
- Modify H2 `create_tables.sql` and `clean.sql` fixtures.
- Create `TrackingStatusMappingDO` and `TrackingStatusMappingMapper`.
- Modify `TrackingEventDO`, `ShipmentPackageDO`, and `ShipmentLegDO`.
- Modify `ProviderTrackingEvent` and its Mock provider tests.
- Create `TrackingStatusMappingPersistenceTest`.

## Schema contract

Create `trade_tracking_status_mapping` with:

```text
id bigint primary key
tenant_id bigint not null
provider_code varchar(32) not null
carrier_code varchar(32) not null
provider_status_normalized varchar(128) not null
standard_status varchar(32) not null
mapping_version varchar(32) not null
effective_at datetime(6) not null
BaseDO audit columns and deleted
```

Constraints:

```text
UNIQUE (tenant_id, provider_code, carrier_code,
        provider_status_normalized, mapping_version, deleted)
INDEX  (tenant_id, provider_code, carrier_code, effective_at)
```

Mappings are append-only at the service boundary. Phase 1 supports exact carrier mappings only; wildcard precedence is deferred until a real provider contract requires it.

Alter `trade_tracking_event` to add:

```text
provider_status_normalized varchar(128) not null default ''
mapping_version varchar(32) null
mapping_effective_at datetime(6) null
mapping_known bit not null default 0
transition_decision varchar(20) not null default 'TIMELINE_ONLY'
previous_status varchar(32) null
result_status varchar(32) null
```

Change `occurred_at` and `received_at` to `datetime(6)`. Add nullable `last_event_occurred_at datetime(6)` to package and leg, and change the shipment watermark to `datetime(6)`.

## Mapper contract

```java
TrackingStatusMappingDO selectActive(Long tenantId, String providerCode,
                                     String carrierCode, String normalizedStatus,
                                     LocalDateTime receivedAtUtc);

TrackingStatusMappingDO selectAtVersion(Long tenantId, String providerCode,
                                        String carrierCode, String normalizedStatus,
                                        String mappingVersion);
```

Both methods are tenant-scoped and logical-delete-aware. `selectActive` selects the latest `effective_at <= receivedAtUtc`, with `id DESC` as the stable tie breaker.

## Provider event boundary

Use an immutable event carrying only provider facts:

```java
ProviderTrackingEvent(
    String externalEventId,
    String providerStatus,
    Instant occurredAt,
    String occurredTimezone,
    String location,
    String description,
    String rawPayloadRef)
```

Override/redact `toString` so external ID, raw status, location, description, and payload reference are not emitted. Provider code comes from the client; carrier/tracking context comes from the snapshot/query; received time and source come from the ingestion service. The adapter must not supply an internal shipment status.

## TDD and verification

Write failing tests first for migration ordering, generated baseline equality, mapping uniqueness, tenant isolation, active/effective selection, exact-version replay selection, microsecond timestamps, package/leg watermarks, and Provider event immutability/redaction.

Run:

```powershell
npm.cmd test -- databaseFulfillmentMigration.test.js databaseSafetyWorkflow.test.js dbMigrations.test.js
mvn.cmd -pl yudao-module-mall/yudao-module-trade-server -am "-Dtest=TrackingStatusMappingPersistenceTest,FulfillmentPersistenceTest,LogisticsProviderRegistryTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: all tests pass, no real provider rows are seeded, and V015 remains unchanged.

Commit message: `feat: add versioned tracking status mappings`.
