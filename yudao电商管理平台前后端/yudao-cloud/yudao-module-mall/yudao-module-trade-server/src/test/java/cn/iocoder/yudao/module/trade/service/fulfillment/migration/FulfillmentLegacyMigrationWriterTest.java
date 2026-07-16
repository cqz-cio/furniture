package cn.iocoder.yudao.module.trade.service.fulfillment.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentProperties;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import({FulfillmentLegacyMigrationWriterImpl.class, LegacyMigrationEligibilityEvaluator.class,
        LegacyMigrationFactSourceImpl.class, FulfillmentProperties.class})
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
        assertEquals("SHIPPED", scalar("SELECT status FROM trade_order_fulfillment_summary"));
        assertEquals("COMPLETED", scalar("SELECT status FROM trade_fulfillment_idempotency"));
        assertEquals(9999, jdbc.queryForObject(
                "SELECT YEAR(expires_at) FROM trade_fulfillment_idempotency", Integer.class));
        assertNull(scalar("SELECT description FROM trade_tracking_event"));
        assertNull(scalar("SELECT location FROM trade_tracking_event"));
        assertNull(scalar("SELECT raw_payload_ref FROM trade_tracking_event"));
        String payload = String.valueOf(scalar("SELECT payload FROM trade_fulfillment_outbox_event"));
        assertTrue(payload.contains("tenantId"));
        assertTrue(payload.contains("orderId"));
        assertTrue(payload.contains("shipmentId"));
        assertTrue(!payload.contains("Case-Sensitive.91001") && !payload.contains("5550000000")
                && !payload.contains("controlled-test-address"));
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
