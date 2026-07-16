package cn.iocoder.yudao.module.trade.service.fulfillment.migration;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentTypeEnum;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentFeatureGuard;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentProperties;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.LogisticsProviderRegistry;
import cn.iocoder.yudao.module.trade.service.fulfillment.FulfillmentCommandService;
import cn.iocoder.yudao.module.trade.service.fulfillment.FulfillmentCommandServiceImpl;
import cn.iocoder.yudao.module.trade.service.fulfillment.FulfillmentTrackingRegistrationFailureService;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.CreateShipmentCommand;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.CreateShipmentItemCommand;
import cn.iocoder.yudao.module.trade.service.fulfillment.support.FulfillmentHashing;
import cn.iocoder.yudao.module.trade.service.fulfillment.support.FulfillmentNoGenerator;
import com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.github.yulichang.autoconfigure.MybatisPlusJoinAutoConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.opentest4j.AssertionFailedError;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "FULFILLMENT_MYSQL_TEST_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = FulfillmentLegacyMigrationMySqlProductionIntegrationTest.MySqlTestApplication.class,
        properties = {
                "spring.main.banner-mode=off",
                "spring.sql.init.mode=never",
                "yudao.info.base-package=cn.iocoder.yudao.module.trade",
                "yudao.trade.fulfillment.enabled=true",
                "yudao.trade.fulfillment.write-new-model=true",
                "yudao.trade.fulfillment.read-from-new-model=false",
                "yudao.trade.fulfillment.customer-ui-enabled=false",
                "yudao.trade.fulfillment.legacy-migration-write-enabled=true",
                "yudao.trade.fulfillment.provider-code=mysql-integration",
                "yudao.trade.fulfillment.idempotency-hmac-key=mysql-integration-only-hmac-secret"
        })
class FulfillmentLegacyMigrationMySqlProductionIntegrationTest {

    private static final long TENANT_ID = 7101L;
    private static final long CARRIER_ID = 7201L;
    private static final long PROVIDER_ID = 7301L;
    private static final long WAREHOUSE_ID = 7401L;
    private static final long LEGACY_EXPRESS_ID = 7501L;

    @Resource private DataSource dataSource;
    @Resource private FulfillmentLegacyMigrationWriter migrationWriter;
    @Resource private FulfillmentCommandService commandService;
    @Resource private FulfillmentProperties fulfillmentProperties;
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("FULFILLMENT_MYSQL_TEST_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault(
                "FULFILLMENT_MYSQL_TEST_USER", "root"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault(
                "FULFILLMENT_MYSQL_TEST_PASSWORD", "test-secret"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        cleanBusinessTables();
        jdbc.update("INSERT INTO trade_carrier (id, tenant_id, code, name, country_codes, legacy_express_id, status) "
                        + "VALUES (?, ?, 'MYSQL-CARRIER', 'MySQL Carrier', 'US', ?, 0)",
                CARRIER_ID, TENANT_ID, LEGACY_EXPRESS_ID);
        jdbc.update("INSERT INTO trade_logistics_provider (id, tenant_id, code, name, capabilities, status) "
                        + "VALUES (?, ?, 'mysql-integration', 'MySQL integration provider', 'TRACKING_QUERY', 0)",
                PROVIDER_ID, TENANT_ID);
        jdbc.update("INSERT INTO erp_warehouse (id, name, status, deleted, tenant_id) "
                        + "VALUES (?, 'MySQL migration warehouse', 0, b'0', ?)",
                WAREHOUSE_ID, TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        jdbc.execute("DROP TRIGGER IF EXISTS migration_test_pause_live_create");
        jdbc.execute("DROP TABLE IF EXISTS migration_test_probe");
    }

    @Test
    void concurrentSameOrderUsesProductionWriterAndReturnsExactReplay() throws Exception {
        seedMigratableOrder(8101L, 9101L, "EXACT-REPLAY-TRACKING");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<MigrationOrderResult> first = pool.submit(() -> migrateAfter(start, 8101L));
            Future<MigrationOrderResult> second = pool.submit(() -> migrateAfter(start, 8101L));
            start.countDown();

            Set<MigrationOutcome> outcomes = Set.of(first.get(20, TimeUnit.SECONDS).outcome(),
                    second.get(20, TimeUnit.SECONDS).outcome());
            assertEquals(Set.of(MigrationOutcome.MIGRATED, MigrationOutcome.ALREADY_MIGRATED), outcomes);
            assertCompleteMigrationAggregate(8101L, 1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void migrationWaitsForProductionLiveCreateAndDoesNotBuildASecondAggregate() throws Exception {
        seedMigratableOrder(8201L, 9201L, "LIVE-CREATE-TRACKING");
        jdbc.execute("CREATE TABLE migration_test_probe (order_id BIGINT PRIMARY KEY) ENGINE=MyISAM");
        jdbc.execute("CREATE TRIGGER migration_test_pause_live_create BEFORE INSERT ON trade_shipment "
                + "FOR EACH ROW BEGIN IF NEW.order_id = 8201 AND NEW.status = 'DRAFT' THEN "
                + "INSERT IGNORE INTO migration_test_probe VALUES (NEW.order_id); DO SLEEP(2); END IF; END");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Long> liveCreate = pool.submit(() -> createLiveShipment(8201L, 9201L));
            awaitProbe(8201L, liveCreate);
            Future<MigrationOrderResult> waitingMigration = pool.submit(() -> migrate(8201L));

            Long liveShipmentId = liveCreate.get(20, TimeUnit.SECONDS);
            MigrationOrderResult result = waitingMigration.get(20, TimeUnit.SECONDS);
            assertEquals(MigrationOutcome.CONCURRENT_CHANGE, result.outcome());
            assertEquals(1, count("trade_shipment", "order_id = 8201"));
            assertEquals("DRAFT", jdbc.queryForObject(
                    "SELECT status FROM trade_shipment WHERE id = ?", String.class, liveShipmentId));
            assertEquals(0, count("trade_fulfillment_idempotency",
                    "operation = 'LEGACY_ORDER_MIGRATION'"));
            assertEquals(1, count("trade_order_fulfillment_summary", "order_id = 8201"));
            assertEquals(1, count("trade_fulfillment_outbox_event", "aggregate_id = " + liveShipmentId));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void crossOrderSameTrackingKeepsOneCompleteAggregateAndRollsBackTheLoser() throws Exception {
        seedMigratableOrder(8301L, 9301L, "SHARED-TRACKING");
        seedMigratableOrder(8302L, 9302L, "SHARED-TRACKING");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<MigrationOrderResult> first = pool.submit(() -> migrateAfter(start, 8301L));
            Future<MigrationOrderResult> second = pool.submit(() -> migrateAfter(start, 8302L));
            start.countDown();
            MigrationOrderResult firstResult = first.get(20, TimeUnit.SECONDS);
            MigrationOrderResult secondResult = second.get(20, TimeUnit.SECONDS);
            assertEquals(Set.of(MigrationOutcome.MIGRATED, MigrationOutcome.TRACKING_CONFLICT),
                    Set.of(firstResult.outcome(), secondResult.outcome()));

            long winner = firstResult.outcome() == MigrationOutcome.MIGRATED ? 8301L : 8302L;
            long loser = winner == 8301L ? 8302L : 8301L;
            assertCompleteMigrationAggregate(winner, 1);
            assertCompleteMigrationAggregate(loser, 0);
            assertEquals(1, count("trade_shipment_package",
                    "carrier_id = " + CARRIER_ID + " AND tracking_number = 'SHARED-TRACKING'"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void zeroAggregateAssertionRejectsAnOrphanedLoserIdempotencyRow() {
        long loserOrderId = 8401L;
        String loserKeyHash = FulfillmentHashing.hmacSha256Hex(fulfillmentProperties.getIdempotencyHmacKey(),
                "legacy-migration:key:v1|" + TENANT_ID + "|" + loserOrderId);
        jdbc.update("INSERT INTO trade_fulfillment_idempotency (tenant_id, operation, idempotency_key_hash, "
                        + "request_hash, resource_type, resource_id, status, expires_at) "
                        + "VALUES (?, 'LEGACY_ORDER_MIGRATION', ?, ?, 'SHIPMENT', NULL, 'PROCESSING', ?)",
                TENANT_ID, loserKeyHash, "0".repeat(64), LocalDateTime.of(9999, 12, 31, 23, 59, 59));

        assertThrows(AssertionFailedError.class, () -> assertCompleteMigrationAggregate(loserOrderId, 0));
    }

    private MigrationOrderResult migrateAfter(CountDownLatch start, long orderId) throws Exception {
        assertTrue(start.await(10, TimeUnit.SECONDS));
        return migrate(orderId);
    }

    private MigrationOrderResult migrate(long orderId) {
        TenantContextHolder.setTenantId(TENANT_ID);
        try {
            return migrationWriter.migrateOne(TENANT_ID, orderId);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private Long createLiveShipment(long orderId, long orderItemId) {
        TenantContextHolder.setTenantId(TENANT_ID);
        LoginUser operator = new LoginUser();
        operator.setId(7001L);
        operator.setTenantId(TENANT_ID);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(operator, null, List.of()));
        try {
            return commandService.createShipment("mysql-live-create-" + orderId,
                    new CreateShipmentCommand()
                            .setTenantId(TENANT_ID)
                            .setOrderId(orderId)
                            .setShipmentType(ShipmentTypeEnum.PARCEL)
                            .setOriginCountry("US")
                            .setDestinationCountry("US")
                            .setOriginTimezone("America/New_York")
                            .setDestinationTimezone("America/Los_Angeles")
                            .setWarehouseId(WAREHOUSE_ID)
                            .setProviderId(PROVIDER_ID)
                            .setItems(List.of(new CreateShipmentItemCommand()
                                    .setOrderItemId(orderItemId)
                                    .setSkuId(orderItemId + 10000)
                                    .setQuantity(BigDecimal.ONE))));
        } finally {
            SecurityContextHolder.clearContext();
            TenantContextHolder.clear();
        }
    }

    private void awaitProbe(long orderId, Future<?> liveCreate) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (liveCreate.isDone()) {
                liveCreate.get(1, TimeUnit.SECONDS);
                throw new AssertionError("Production live-create completed before the lock probe was observed");
            }
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM migration_test_probe WHERE order_id = ?", Integer.class, orderId);
            if (count != null && count == 1) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for production live-create to hold the order lock");
    }

    private void seedMigratableOrder(long orderId, long itemId, String tracking) {
        jdbc.update("INSERT INTO trade_order (id, no, status, logistics_id, logistics_no, delivery_time, "
                        + "tenant_id, deleted) VALUES (?, ?, 20, ?, ?, ?, ?, b'0')",
                orderId, "ORDER-" + orderId, LEGACY_EXPRESS_ID, tracking,
                LocalDateTime.of(2026, 7, 15, 12, 0), TENANT_ID);
        jdbc.update("INSERT INTO trade_order_item (id, user_id, order_id, spu_id, spu_name, sku_id, count, "
                        + "price, discount_price, pay_price, after_sale_status, tenant_id, deleted) "
                        + "VALUES (?, 7001, ?, ?, 'MySQL migration item', ?, 1, 1000, 0, 1000, 0, ?, b'0')",
                itemId, orderId, itemId + 20000, itemId + 10000, TENANT_ID);
        jdbc.update("INSERT INTO trade_fulfillment_legacy_migration_fact "
                        + "(tenant_id, order_id, origin_country, destination_country, origin_timezone, "
                        + "destination_timezone, warehouse_id, migration_provider_id, approved_by, approved_at, "
                        + "source_reference) VALUES (?, ?, 'US', 'US', 'America/New_York', "
                        + "'America/Los_Angeles', ?, ?, 7001, ?, ?)",
                TENANT_ID, orderId, WAREHOUSE_ID, PROVIDER_ID,
                LocalDateTime.of(2026, 7, 16, 9, 30), "mysql-approved-" + orderId);
    }

    private void assertCompleteMigrationAggregate(long orderId, int expected) {
        assertEquals(expected, count("trade_shipment", "order_id = " + orderId));
        assertEquals(expected, jdbc.queryForObject("SELECT COUNT(*) FROM trade_shipment_item i "
                + "JOIN trade_shipment s ON s.id = i.shipment_id WHERE s.order_id = ?", Integer.class, orderId));
        assertEquals(expected, jdbc.queryForObject("SELECT COUNT(*) FROM trade_shipment_package p "
                + "JOIN trade_shipment s ON s.id = p.shipment_id WHERE s.order_id = ?", Integer.class, orderId));
        assertEquals(expected, jdbc.queryForObject("SELECT COUNT(*) FROM trade_shipment_leg l "
                + "JOIN trade_shipment s ON s.id = l.shipment_id WHERE s.order_id = ?", Integer.class, orderId));
        assertEquals(expected, jdbc.queryForObject("SELECT COUNT(*) FROM trade_tracking_event e "
                + "JOIN trade_shipment s ON s.id = e.shipment_id WHERE s.order_id = ?", Integer.class, orderId));
        assertEquals(expected, count("trade_order_fulfillment_summary", "order_id = " + orderId));
        String migrationKeyHash = FulfillmentHashing.hmacSha256Hex(fulfillmentProperties.getIdempotencyHmacKey(),
                "legacy-migration:key:v1|" + TENANT_ID + "|" + orderId);
        assertEquals(expected, jdbc.queryForObject("SELECT COUNT(*) FROM trade_fulfillment_idempotency "
                        + "WHERE tenant_id = ? AND operation = 'LEGACY_ORDER_MIGRATION' "
                        + "AND idempotency_key_hash = ? AND status IN ('PROCESSING', 'COMPLETED') "
                        + "AND deleted = b'0'",
                Integer.class, TENANT_ID, migrationKeyHash));
        assertEquals(expected, jdbc.queryForObject("SELECT COUNT(*) FROM trade_fulfillment_outbox_event o "
                + "JOIN trade_shipment s ON s.id = o.aggregate_id "
                + "WHERE o.event_type = 'LEGACY_ORDER_MIGRATED' AND s.order_id = ?", Integer.class, orderId));
    }

    private int count(String table, String predicate) {
        Integer value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + predicate, Integer.class);
        return value == null ? 0 : value;
    }

    private void cleanBusinessTables() {
        jdbc.execute("DROP TRIGGER IF EXISTS migration_test_pause_live_create");
        jdbc.execute("DROP TABLE IF EXISTS migration_test_probe");
        for (String table : List.of("trade_fulfillment_outbox_event", "trade_fulfillment_idempotency",
                "trade_tracking_event", "trade_shipment_leg", "trade_shipment_package", "trade_shipment_item",
                "trade_order_fulfillment_summary", "trade_shipment", "trade_fulfillment_legacy_migration_fact",
                "trade_order_item", "trade_order", "trade_carrier", "trade_logistics_provider", "erp_warehouse")) {
            jdbc.execute("DELETE FROM " + table);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(FulfillmentProperties.class)
    @MapperScan(basePackages = {
            "cn.iocoder.yudao.module.trade.dal.mysql.order",
            "cn.iocoder.yudao.module.trade.dal.mysql.fulfillment"
    })
    @Import({
            YudaoDataSourceAutoConfiguration.class,
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class,
            MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class,
            SpringUtil.class,
            FulfillmentFeatureGuard.class,
            LegacyMigrationEligibilityEvaluator.class,
            LegacyMigrationFactSourceImpl.class,
            FulfillmentLegacyMigrationWriterImpl.class,
            FulfillmentCommandServiceImpl.class,
            FulfillmentTrackingRegistrationFailureService.class,
            FulfillmentNoGenerator.class,
            LogisticsProviderRegistry.class
    })
    static class MySqlTestApplication {
    }
}
