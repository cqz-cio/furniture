package cn.iocoder.yudao.module.trade.service.fulfillment.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.*;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.*;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentProperties;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

@Import({FulfillmentLegacyMigrationServiceImpl.class, FulfillmentLegacyMigrationWriterImpl.class,
        LegacyMigrationEligibilityEvaluator.class,
        LegacyMigrationFactSourceImpl.class, FulfillmentProperties.class})
class FulfillmentLegacyMigrationTransactionTest extends BaseDbUnitTest {

    private static final long TENANT_ID = 121L;
    private static final long FIRST_ORDER_ID = 92001L;
    private static final long FAILED_ORDER_ID = 93001L;

    @Resource private FulfillmentLegacyMigrationWriter writer;
    @Resource private FulfillmentLegacyMigrationService migrationService;
    @Resource private FulfillmentProperties properties;
    @Resource private DataSource dataSource;
    @SpyBean private FulfillmentIdempotencyMapper idempotencyMapper;
    @SpyBean private ShipmentMapper shipmentMapper;
    @SpyBean private ShipmentItemMapper itemMapper;
    @SpyBean private ShipmentPackageMapper packageMapper;
    @SpyBean private ShipmentLegMapper legMapper;
    @SpyBean private TrackingEventMapper eventMapper;
    @SpyBean private OrderFulfillmentSummaryMapper summaryMapper;
    @SpyBean private FulfillmentOutboxEventMapper outboxMapper;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        properties.setIdempotencyHmacKey("migration-transaction-test-hmac-key-32-chars");
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void lateBoundaryFailureRollsBackFailedOrderWhileEarlierRequiresNewCommitSurvives() {
        LegacyMigrationTestData.seed(jdbc, TENANT_ID, FIRST_ORDER_ID, 20, "TRACK-92001");
        assertEquals(MigrationOutcome.MIGRATED, writer.migrateOne(TENANT_ID, FIRST_ORDER_ID).outcome());
        LegacyMigrationTestData.seed(jdbc, TENANT_ID, FAILED_ORDER_ID, 20, "TRACK-93001");
        doThrow(new IllegalStateException("injected outbox failure"))
                .when(outboxMapper).insert(any(FulfillmentOutboxEventDO.class));

        assertThrows(IllegalStateException.class, () -> writer.migrateOne(TENANT_ID, FAILED_ORDER_ID));

        assertEquals(1, countForOrder("trade_shipment", FIRST_ORDER_ID));
        assertEquals(0, countForOrder("trade_shipment", FAILED_ORDER_ID));
        assertEquals(0, countForOrder("trade_order_fulfillment_summary", FAILED_ORDER_ID));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM trade_fulfillment_idempotency "
                + "WHERE resource_id IS NULL", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM trade_fulfillment_outbox_event", Integer.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"IDEMPOTENCY", "SHIPMENT", "ITEM", "PACKAGE", "LEG", "EVENT",
            "SHIPMENT_WATERMARK", "PACKAGE_WATERMARK", "LEG_WATERMARK", "SUMMARY", "OUTBOX", "COMPLETE"})
    void everyWriteBoundaryFailureRollsBackTheEntireOrder(String boundary) {
        LegacyMigrationTestData.seed(jdbc, TENANT_ID, FAILED_ORDER_ID, 20, "TRACK-93001");
        injectFailure(boundary);

        assertThrows(RuntimeException.class, () -> writer.migrateOne(TENANT_ID, FAILED_ORDER_ID));

        assertEquals(0, count("trade_fulfillment_idempotency"), boundary);
        assertEquals(0, count("trade_shipment"), boundary);
        assertEquals(0, count("trade_shipment_item"), boundary);
        assertEquals(0, count("trade_shipment_package"), boundary);
        assertEquals(0, count("trade_shipment_leg"), boundary);
        assertEquals(0, count("trade_tracking_event"), boundary);
        assertEquals(0, count("trade_order_fulfillment_summary"), boundary);
        assertEquals(0, count("trade_fulfillment_outbox_event"), boundary);
    }

    @ParameterizedTest
    @ValueSource(strings = {"IDEMPOTENCY", "SHIPMENT", "ITEM", "PACKAGE", "LEG", "EVENT", "SUMMARY", "OUTBOX"})
    void everyZeroRowInsertRollsBackTheEntireOrder(String boundary) {
        LegacyMigrationTestData.seed(jdbc, TENANT_ID, FAILED_ORDER_ID, 20, "TRACK-93001");
        injectZeroRowInsert(boundary);

        assertThrows(IllegalStateException.class, () -> writer.migrateOne(TENANT_ID, FAILED_ORDER_ID));

        assertEquals(0, count("trade_fulfillment_idempotency"), boundary);
        assertEquals(0, count("trade_shipment"), boundary);
        assertEquals(0, count("trade_shipment_item"), boundary);
        assertEquals(0, count("trade_shipment_package"), boundary);
        assertEquals(0, count("trade_shipment_leg"), boundary);
        assertEquals(0, count("trade_tracking_event"), boundary);
        assertEquals(0, count("trade_order_fulfillment_summary"), boundary);
        assertEquals(0, count("trade_fulfillment_outbox_event"), boundary);
    }

    @Test
    void onlyNamedPackageTrackingDuplicateBecomesSafeConflictAndRollsBack() {
        LegacyMigrationTestData.seed(jdbc, TENANT_ID, FAILED_ORDER_ID, 20, "do-not-leak-this-tracking");
        doThrow(new DuplicateKeyException("Duplicate entry for key 'uk_package_tracking'"))
                .when(packageMapper).insert(any(ShipmentPackageDO.class));

        LegacyMigrationWriteConflictException failure = assertThrows(LegacyMigrationWriteConflictException.class,
                () -> writer.migrateOne(TENANT_ID, FAILED_ORDER_ID));

        assertEquals(MigrationOutcome.TRACKING_CONFLICT, failure.toResult().outcome());
        assertEquals("Legacy migration write conflict", failure.getMessage());
        assertEquals(0, count("trade_fulfillment_idempotency"));
        assertEquals(0, count("trade_shipment"));
    }

    @Test
    void batchMapsNamedTrackingDuplicateOnlyAfterRequiresNewRollback() {
        LegacyMigrationTestData.seed(jdbc, TENANT_ID, FAILED_ORDER_ID, 20, "do-not-leak-this-tracking");
        doThrow(new DuplicateKeyException("Duplicate entry for key 'uk_package_tracking'"))
                .when(packageMapper).insert(any(ShipmentPackageDO.class));

        MigrationBatchResult batch = migrationService.migrateActiveOrders(TENANT_ID, 0L, 10, false);

        assertEquals(MigrationOutcome.TRACKING_CONFLICT, batch.orders().get(0).outcome());
        assertEquals("TRACKING_CONFLICT", batch.orders().get(0).reasonCode());
        assertEquals(0, count("trade_fulfillment_idempotency"));
        assertEquals(0, count("trade_shipment"));
        assertEquals(0, count("trade_shipment_item"));
    }

    private void injectZeroRowInsert(String boundary) {
        switch (boundary) {
            case "IDEMPOTENCY" -> doReturn(0).when(idempotencyMapper).insert(any(FulfillmentIdempotencyDO.class));
            case "SHIPMENT" -> doReturn(0).when(shipmentMapper).insert(any(ShipmentDO.class));
            case "ITEM" -> doReturn(0).when(itemMapper).insert(any(ShipmentItemDO.class));
            case "PACKAGE" -> doReturn(0).when(packageMapper).insert(any(ShipmentPackageDO.class));
            case "LEG" -> doReturn(0).when(legMapper).insert(any(ShipmentLegDO.class));
            case "EVENT" -> doReturn(0).when(eventMapper).insert(any(TrackingEventDO.class));
            case "SUMMARY" -> doReturn(0).when(summaryMapper).insert(any(OrderFulfillmentSummaryDO.class));
            case "OUTBOX" -> doReturn(0).when(outboxMapper).insert(any(FulfillmentOutboxEventDO.class));
            default -> throw new IllegalArgumentException("unknown boundary");
        }
    }

    private void injectFailure(String boundary) {
        IllegalStateException failure = new IllegalStateException("injected " + boundary + " failure");
        switch (boundary) {
            case "IDEMPOTENCY" -> doThrow(failure).when(idempotencyMapper).insert(any(FulfillmentIdempotencyDO.class));
            case "SHIPMENT" -> doThrow(failure).when(shipmentMapper).insert(any(ShipmentDO.class));
            case "ITEM" -> doThrow(failure).when(itemMapper).insert(any(ShipmentItemDO.class));
            case "PACKAGE" -> doThrow(failure).when(packageMapper).insert(any(ShipmentPackageDO.class));
            case "LEG" -> doThrow(failure).when(legMapper).insert(any(ShipmentLegDO.class));
            case "EVENT" -> doThrow(failure).when(eventMapper).insert(any(TrackingEventDO.class));
            case "SHIPMENT_WATERMARK" -> doReturn(0).when(shipmentMapper).updateTrackingStateByIdAndVersion(
                    anyLong(), anyLong(), any(), anyString(), any(), any(), anyLong(), any());
            case "PACKAGE_WATERMARK" -> doReturn(0).when(packageMapper).updateTrackingStateByIdAndVersion(
                    anyLong(), anyLong(), any(), anyString(), any(), any(), anyLong());
            case "LEG_WATERMARK" -> doReturn(0).when(legMapper).updateTrackingStateByIdAndVersion(
                    anyLong(), anyLong(), any(), anyString(), any(), any(), anyLong());
            case "SUMMARY" -> doThrow(failure).when(summaryMapper).insert(any(OrderFulfillmentSummaryDO.class));
            case "OUTBOX" -> doThrow(failure).when(outboxMapper).insert(any(FulfillmentOutboxEventDO.class));
            case "COMPLETE" -> doReturn(0).when(idempotencyMapper).completeProcessingById(
                    anyLong(), anyLong(), anyString(), anyLong(), any());
            default -> throw new IllegalArgumentException("unknown boundary");
        }
    }

    private int countForOrder(String table, long orderId) {
        if ("trade_shipment".equals(table) || "trade_order_fulfillment_summary".equals(table)) {
            return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE order_id = ?", Integer.class,
                    orderId);
        }
        throw new IllegalArgumentException("unsupported table");
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
