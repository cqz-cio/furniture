package cn.iocoder.yudao.module.trade.service.fulfillment.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.LogisticsProviderRegistry;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.lang.reflect.Array;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;

@Import({FulfillmentLegacyMigrationServiceImpl.class, LegacyMigrationEligibilityEvaluator.class,
        LegacyMigrationFactSourceImpl.class})
class FulfillmentLegacyMigrationDryRunIntegrationTest extends BaseDbUnitTest {

    private static final Long TENANT_ID = 121L;
    private static final Long ORDER_ID = 88001L;
    private static final Long CARRIER_ID = 88002L;
    private static final Long WAREHOUSE_ID = 88003L;
    private static final Long PROVIDER_ID = 88004L;

    private static final List<String> FULFILLMENT_TABLES = List.of(
            "trade_carrier",
            "trade_logistics_provider",
            "trade_fulfillment_legacy_migration_fact",
            "trade_shipment",
            "trade_shipment_item",
            "trade_shipment_package",
            "trade_shipment_leg",
            "trade_tracking_status_mapping",
            "trade_tracking_event",
            "trade_order_fulfillment_summary",
            "trade_fulfillment_idempotency",
            "trade_fulfillment_outbox_event");

    @Resource private FulfillmentLegacyMigrationService migrationService;
    @Resource private DataSource dataSource;
    @MockBean private LogisticsProviderRegistry providerRegistry;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        jdbc = new JdbcTemplate(dataSource);
        seedRealCandidateAndApprovedFacts();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void dryRunReadsRealCandidateAndFactsWithoutAnyFulfillmentWriteOrProviderDispatch() {
        Map<String, List<String>> before = snapshotEveryFulfillmentTable();

        MigrationBatchResult result = migrationService.migrateActiveOrders(TENANT_ID, 0L, 10, true);

        assertEquals(1, result.scanned());
        assertEquals(1, result.wouldMigrate());
        assertEquals(MigrationOutcome.WOULD_MIGRATE, result.orders().get(0).outcome());
        assertEquals(before, snapshotEveryFulfillmentTable());
        // The dry-run graph has no provider-client dependency; this registry mock proves no client dispatch occurs.
        verifyNoInteractions(providerRegistry);
    }

    private void seedRealCandidateAndApprovedFacts() {
        jdbc.update("INSERT INTO trade_order (id, no, type, terminal, user_id, user_ip, status, "
                        + "product_count, pay_status, discount_price, delivery_price, adjust_price, pay_price, "
                        + "delivery_type, logistics_id, logistics_no, delivery_time, receiver_name, receiver_mobile, "
                        + "receiver_area_id, receiver_detail_address, coupon_id, coupon_price, point_price, tenant_id) "
                        + "VALUES (?, 'ORDER-MIGRATION-DRY-RUN', 0, 10, 200, '127.0.0.1', 20, 1, TRUE, 0, 0, 0, "
                        + "3000, 1, ?, 'REAL-TRACKING-88001', ?, 'Receiver', '5550000000', 1, "
                        + "'controlled-test-address', 0, 0, 0, ?)",
                ORDER_ID, 77L, LocalDateTime.of(2026, 7, 15, 12, 0), TENANT_ID);
        jdbc.update("INSERT INTO trade_order_item (id, user_id, order_id, spu_id, spu_name, sku_id, count, price, "
                        + "discount_price, pay_price, after_sale_status, tenant_id) "
                        + "VALUES (?, 200, ?, 99001, 'Migration item', 99002, 1, 3000, 0, 3000, 0, ?)",
                88005L, ORDER_ID, TENANT_ID);
        jdbc.update("INSERT INTO trade_carrier (id, tenant_id, code, name, country_codes, legacy_express_id, status) "
                        + "VALUES (?, ?, 'MIGRATION-CARRIER', 'Migration Carrier', 'US', 77, 0)",
                CARRIER_ID, TENANT_ID);
        jdbc.update("INSERT INTO erp_warehouse (id, name, status, deleted, tenant_id) VALUES (?, ?, 0, FALSE, ?)",
                WAREHOUSE_ID, "Migration warehouse", TENANT_ID);
        jdbc.update("INSERT INTO trade_logistics_provider (id, tenant_id, code, name, capabilities, status) "
                        + "VALUES (?, ?, 'migration-provider', 'Migration Provider', 'TRACKING_QUERY', 0)",
                PROVIDER_ID, TENANT_ID);
        jdbc.update("INSERT INTO trade_fulfillment_legacy_migration_fact "
                        + "(tenant_id, order_id, origin_country, destination_country, origin_timezone, "
                        + "destination_timezone, warehouse_id, migration_provider_id, approved_by, approved_at, "
                        + "source_reference) VALUES (?, ?, 'US', 'US', 'America/New_York', 'America/Los_Angeles', "
                        + "?, ?, 701, ?, 'controlled-approval-reference')",
                TENANT_ID, ORDER_ID, WAREHOUSE_ID, PROVIDER_ID, LocalDateTime.of(2026, 7, 16, 9, 30));
    }

    private Map<String, List<String>> snapshotEveryFulfillmentTable() {
        Map<String, List<String>> snapshot = new LinkedHashMap<>();
        for (String table : FULFILLMENT_TABLES) {
            List<String> rows = new ArrayList<>();
            for (Map<String, Object> row : jdbc.queryForList("SELECT * FROM " + table)) {
                TreeMap<String, String> canonicalRow = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                row.forEach((column, value) -> canonicalRow.put(column, canonicalValue(value)));
                rows.add(canonicalRow.toString());
            }
            rows.sort(String::compareTo);
            snapshot.put(table, List.copyOf(rows));
        }
        return snapshot;
    }

    private static String canonicalValue(Object value) {
        if (value == null || !value.getClass().isArray()) {
            return String.valueOf(value);
        }
        int length = Array.getLength(value);
        List<String> elements = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            elements.add(String.valueOf(Array.get(value, i)));
        }
        return elements.toString();
    }
}
