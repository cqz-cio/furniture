package cn.iocoder.yudao.module.trade.service.fulfillment.migration;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentFeatureGuard;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import({FulfillmentLegacyMigrationWriterImpl.class, LegacyMigrationEligibilityEvaluator.class,
        LegacyMigrationFactSourceImpl.class, FulfillmentFeatureGuard.class, FulfillmentProperties.class})
class FulfillmentLegacyMigrationWriterTest extends BaseDbUnitTest {

    private static final long TENANT_ID = 121L;
    private static final long ORDER_ID = 91001L;

    @Resource private FulfillmentLegacyMigrationWriter writer;
    @Resource private FulfillmentProperties properties;
    @Resource private DataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        properties.setEnabled(true);
        properties.setWriteNewModel(true);
        properties.setLegacyMigrationWriteEnabled(true);
        properties.setIdempotencyHmacKey("migration-writer-test-hmac-key-32-chars-minimum");
        jdbc = new JdbcTemplate(dataSource);
        LegacyMigrationTestData.seed(jdbc, TENANT_ID, ORDER_ID, 20, "  Case-Sensitive.91001  ");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createsCompleteHistoricalAggregateAndExactReplayCreatesNoDuplicates() {
        Map<String, Object> legacyBefore = legacySnapshot();

        MigrationOrderResult first = writer.migrateOne(TENANT_ID, ORDER_ID);
        MigrationOrderResult replay = writer.migrateOne(TENANT_ID, ORDER_ID);

        assertEquals(MigrationOutcome.MIGRATED, first.outcome());
        assertEquals(MigrationOutcome.ALREADY_MIGRATED, replay.outcome());
        assertEquals(1, count("trade_shipment"));
        assertEquals(2, count("trade_shipment_item"));
        assertEquals(1, count("trade_shipment_package"));
        assertEquals(1, count("trade_shipment_leg"));
        assertEquals(1, count("trade_tracking_event"));
        assertEquals(1, count("trade_order_fulfillment_summary"));
        assertEquals(1, count("trade_fulfillment_outbox_event"));
        assertEquals(1, count("trade_fulfillment_idempotency"));
        assertEquals(legacyBefore, legacySnapshot());

        assertEquals("PARCEL", scalar("SELECT shipment_type FROM trade_shipment"));
        assertEquals("HANDED_TO_CARRIER", scalar("SELECT status FROM trade_shipment"));
        assertEquals("Case-Sensitive.91001", scalar("SELECT tracking_number FROM trade_shipment_package"));
        assertEquals("LAST_MILE", scalar("SELECT leg_type FROM trade_shipment_leg"));
        assertEquals("MIGRATION", scalar("SELECT source FROM trade_tracking_event"));
        assertEquals("TIMELINE_ONLY", scalar("SELECT transition_decision FROM trade_tracking_event"));
        Long eventId = jdbc.queryForObject("SELECT id FROM trade_tracking_event", Long.class);
        assertEquals(eventId, jdbc.queryForObject("SELECT last_event_id FROM trade_shipment", Long.class));
        assertEquals(eventId, jdbc.queryForObject("SELECT last_event_id FROM trade_shipment_package", Long.class));
        assertEquals(eventId, jdbc.queryForObject("SELECT last_event_id FROM trade_shipment_leg", Long.class));
        assertEquals(1, jdbc.queryForObject("SELECT version FROM trade_shipment", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT version FROM trade_shipment_package", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT version FROM trade_shipment_leg", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM trade_shipment_item si "
                + "JOIN trade_order_item oi ON oi.id = si.order_item_id "
                + "WHERE si.sku_id = oi.sku_id AND si.quantity = oi.count", Integer.class));
        assertEquals("SHIPPED", scalar("SELECT status FROM trade_order_fulfillment_summary"));
        assertEquals("COMPLETED", scalar("SELECT status FROM trade_fulfillment_idempotency"));
        assertEquals(9999, jdbc.queryForObject(
                "SELECT YEAR(expires_at) FROM trade_fulfillment_idempotency", Integer.class));
        assertNull(scalar("SELECT description FROM trade_tracking_event"));
        assertNull(scalar("SELECT location FROM trade_tracking_event"));
        assertNull(scalar("SELECT raw_payload_ref FROM trade_tracking_event"));
        JsonNode payload = JsonUtils.parseTree(
                String.valueOf(scalar("SELECT payload FROM trade_fulfillment_outbox_event")));
        Set<String> payloadKeys = new HashSet<>();
        payload.fieldNames().forEachRemaining(payloadKeys::add);
        assertEquals(Set.of("tenantId", "orderId", "shipmentId", "shipmentStatus", "fulfillmentStatus"),
                payloadKeys);
        assertTrue(payload.get("tenantId").isIntegralNumber());
        assertTrue(payload.get("orderId").isIntegralNumber());
        assertTrue(payload.get("shipmentId").isIntegralNumber());
        assertTrue(payload.get("shipmentStatus").isTextual());
        assertTrue(payload.get("fulfillmentStatus").isTextual());
        assertEquals(TENANT_ID, payload.get("tenantId").longValue());
        assertEquals(ORDER_ID, payload.get("orderId").longValue());
        assertEquals("HANDED_TO_CARRIER", payload.get("shipmentStatus").textValue());
        assertEquals("SHIPPED", payload.get("fulfillmentStatus").textValue());
        for (String forbidden : Set.of("providerId", "providerCode", "warehouseId", "sourceReference",
                "trackingDigest", "requestDigest")) {
            assertFalse(payload.has(forbidden), forbidden);
        }
        String renderedPayload = payload.toString();
        assertFalse(renderedPayload.contains("Case-Sensitive.91001"));
        assertFalse(renderedPayload.contains("5550000000"));
        assertFalse(renderedPayload.contains("controlled-test-address"));
    }

    @Test
    void changedSourceAfterCompletionConflictsAndNeverOverwritesAggregate() {
        assertEquals(MigrationOutcome.MIGRATED, writer.migrateOne(TENANT_ID, ORDER_ID).outcome());
        jdbc.update("UPDATE trade_order SET logistics_no = 'CHANGED-TRACKING' WHERE id = ?", ORDER_ID);

        MigrationOrderResult result = writer.migrateOne(TENANT_ID, ORDER_ID);

        assertEquals(MigrationOutcome.IDEMPOTENCY_CONFLICT, result.outcome());
        assertEquals("Case-Sensitive.91001", scalar("SELECT tracking_number FROM trade_shipment_package"));
        assertEquals(1, count("trade_shipment"));
        assertEquals(1, count("trade_fulfillment_idempotency"));
    }

    @Test
    void changedApprovedFactsAfterCompletionConflict() {
        assertEquals(MigrationOutcome.MIGRATED, writer.migrateOne(TENANT_ID, ORDER_ID).outcome());
        jdbc.update("UPDATE trade_fulfillment_legacy_migration_fact SET destination_timezone = 'America/Toronto' "
                + "WHERE tenant_id = ? AND order_id = ?", TENANT_ID, ORDER_ID);

        assertEquals(MigrationOutcome.IDEMPOTENCY_CONFLICT,
                writer.migrateOne(TENANT_ID, ORDER_ID).outcome());
        assertEquals(1, count("trade_shipment"));
    }

    @Test
    void processingReplayIsNeverTreatedAsCompleted() {
        assertEquals(MigrationOutcome.MIGRATED, writer.migrateOne(TENANT_ID, ORDER_ID).outcome());
        jdbc.update("UPDATE trade_fulfillment_idempotency SET status = 'PROCESSING', resource_id = NULL");

        assertEquals(MigrationOutcome.IDEMPOTENCY_CONFLICT,
                writer.migrateOne(TENANT_ID, ORDER_ID).outcome());
        assertEquals(1, count("trade_shipment"));
    }

    @Test
    void missingOrMismatchedReplayResourceConflicts() {
        assertEquals(MigrationOutcome.MIGRATED, writer.migrateOne(TENANT_ID, ORDER_ID).outcome());
        jdbc.update("UPDATE trade_fulfillment_idempotency SET resource_id = 999999");
        assertEquals(MigrationOutcome.IDEMPOTENCY_CONFLICT,
                writer.migrateOne(TENANT_ID, ORDER_ID).outcome());

        Long shipmentId = jdbc.queryForObject("SELECT id FROM trade_shipment", Long.class);
        jdbc.update("UPDATE trade_fulfillment_idempotency SET resource_id = ?", shipmentId);
        jdbc.update("UPDATE trade_shipment SET order_id = ? WHERE id = ?", ORDER_ID + 1, shipmentId);
        assertEquals(MigrationOutcome.IDEMPOTENCY_CONFLICT,
                writer.migrateOne(TENANT_ID, ORDER_ID).outcome());
    }

    @Test
    void liveShipmentCreatedBeforeWriterLockIsReportedAsConcurrentChange() {
        jdbc.update("INSERT INTO trade_shipment (tenant_id, order_id, shipment_no, shipment_type, status, "
                        + "origin_country, destination_country, origin_timezone, destination_timezone, warehouse_id) "
                        + "VALUES (?, ?, 'LIVE-91001', 'PARCEL', 'DRAFT', 'US', 'US', "
                        + "'America/New_York', 'America/New_York', ?)",
                TENANT_ID, ORDER_ID, ORDER_ID + 20);

        assertEquals(MigrationOutcome.CONCURRENT_CHANGE, writer.migrateOne(TENANT_ID, ORDER_ID).outcome());
        assertEquals(1, count("trade_shipment"));
        assertEquals(0, count("trade_fulfillment_idempotency"));
    }

    @Test
    void anotherOrderOwningSameCarrierTrackingIsReportedWithoutLeakingTracking() {
        long secondOrderId = ORDER_ID + 1000;
        LegacyMigrationTestData.seed(jdbc, TENANT_ID, secondOrderId, 20, "Case-Sensitive.91001");
        jdbc.update("UPDATE trade_order SET logistics_id = ? WHERE id = ?", ORDER_ID + 40, secondOrderId);

        assertEquals(MigrationOutcome.MIGRATED, writer.migrateOne(TENANT_ID, ORDER_ID).outcome());
        MigrationOrderResult collision = writer.migrateOne(TENANT_ID, secondOrderId);

        assertEquals(MigrationOutcome.TRACKING_CONFLICT, collision.outcome());
        assertEquals("TRACKING_CONFLICT", collision.reasonCode());
        assertEquals(1, count("trade_shipment"));
        assertEquals(1, count("trade_fulfillment_idempotency"));
    }

    @Test
    void concurrentCallsSerializeOnTenantOrderAndKeepOneAggregate() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<MigrationOutcome> call = () -> {
                TenantContextHolder.setTenantId(TENANT_ID);
                try {
                    return writer.migrateOne(TENANT_ID, ORDER_ID).outcome();
                } finally {
                    TenantContextHolder.clear();
                }
            };
            Future<MigrationOutcome> first = pool.submit(call);
            Future<MigrationOutcome> second = pool.submit(call);

            assertEquals(Set.of(MigrationOutcome.MIGRATED, MigrationOutcome.ALREADY_MIGRATED),
                    Set.of(first.get(), second.get()));
            assertEquals(1, count("trade_shipment"));
            assertEquals(1, count("trade_fulfillment_idempotency"));
            assertEquals(1, count("trade_fulfillment_outbox_event"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void statusTenIsReportedNotShippedAndWritesNothing() {
        jdbc.update("UPDATE trade_order SET status = 10 WHERE id = ?", ORDER_ID);

        MigrationOrderResult result = writer.migrateOne(TENANT_ID, ORDER_ID);

        assertEquals(MigrationOutcome.NOT_SHIPPED, result.outcome());
        assertEquals(0, count("trade_shipment"));
        assertEquals(0, count("trade_fulfillment_idempotency"));
        assertEquals(0, count("trade_fulfillment_outbox_event"));
    }

    private Map<String, Object> legacySnapshot() {
        return jdbc.queryForMap("SELECT status, logistics_id, logistics_no, delivery_time, pay_status "
                + "FROM trade_order WHERE id = " + ORDER_ID);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private Object scalar(String sql) {
        return jdbc.queryForObject(sql, Object.class);
    }
}
