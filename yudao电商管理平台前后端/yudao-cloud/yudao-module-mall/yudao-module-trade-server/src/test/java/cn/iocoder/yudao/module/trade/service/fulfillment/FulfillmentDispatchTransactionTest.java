package cn.iocoder.yudao.module.trade.service.fulfillment;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.FulfillmentOutboxEventDO;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.FulfillmentOutboxEventMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentMapper;
import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentFeatureGuard;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentProperties;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.LogisticsProviderRegistry;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.LogisticsProviderClient;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.ProviderCapability;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.DispatchShipmentCommand;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.AddShipmentLegCommand;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.UpsertPackageCommand;
import cn.iocoder.yudao.module.trade.service.fulfillment.support.FulfillmentNoGenerator;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.sql.DataSource;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Import({FulfillmentCommandServiceImpl.class, FulfillmentFeatureGuard.class, FulfillmentProperties.class,
        FulfillmentNoGenerator.class,
        FulfillmentCommandTransactionTest.TenantDbTestConfiguration.class})
class FulfillmentDispatchTransactionTest extends BaseDbUnitTest {

    private static final Long TENANT_ID = 121L;
    private static final Long ORDER_ID = 100L;
    private static final Long SHIPMENT_ID = 70001L;

    @Resource private FulfillmentCommandService service;
    @Resource private FulfillmentProperties properties;
    @Resource private DataSource dataSource;
    @MockBean private FulfillmentOutboxEventMapper outboxMapper;
    @MockBean private LogisticsProviderRegistry providerRegistry;
    @MockBean private FulfillmentTrackingRegistrationFailureService registrationFailureService;
    @SpyBean private ShipmentMapper shipmentMapper;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        properties.setEnabled(true);
        properties.setWriteNewModel(true);
        properties.setProviderCode("mock");
        properties.setIdempotencyHmacKey("dispatch-transaction-test-secret");
        LoginUser loginUser = new LoginUser();
        loginUser.setId(110L);
        loginUser.setTenantId(TENANT_ID);
        loginUser.setUserType(1);
        SecurityFrameworkUtils.setLoginUser(loginUser, new MockHttpServletRequest());
        LogisticsProviderClient queryOnlyClient = mock(LogisticsProviderClient.class);
        when(queryOnlyClient.getCapabilities()).thenReturn(Set.of(ProviderCapability.TRACKING_QUERY));
        when(providerRegistry.getClient("mock")).thenReturn(queryOnlyClient);
        insertReadyAggregate();
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void outboxFailureAfterLegacyProjectionRollsBackWholeDispatchTransaction() {
        doThrow(new IllegalStateException("outbox failed"))
                .when(outboxMapper).insert(any(FulfillmentOutboxEventDO.class));

        assertThrows(IllegalStateException.class, () -> service.dispatch("private-dispatch-key",
                new DispatchShipmentCommand().setTenantId(TENANT_ID).setShipmentId(SHIPMENT_ID)
                        .setExpectedVersion(1)));

        assertEquals(ShipmentStatusEnum.READY_TO_SHIP.name(), value(
                "SELECT status FROM trade_shipment WHERE id = " + SHIPMENT_ID, String.class));
        assertEquals(1, value("SELECT version FROM trade_shipment WHERE id = " + SHIPMENT_ID, Integer.class));
        assertEquals(ShipmentStatusEnum.DRAFT.name(), value(
                "SELECT status FROM trade_shipment_package WHERE shipment_id = " + SHIPMENT_ID, String.class));
        assertEquals(ShipmentStatusEnum.DRAFT.name(), value(
                "SELECT status FROM trade_shipment_leg WHERE shipment_id = " + SHIPMENT_ID, String.class));
        assertEquals("NOT_SHIPPED", value(
                "SELECT status FROM trade_order_fulfillment_summary WHERE order_id = " + ORDER_ID, String.class));
        assertEquals(TradeOrderStatusEnum.UNDELIVERED.getStatus(), value(
                "SELECT status FROM trade_order WHERE id = " + ORDER_ID, Integer.class));
        assertNull(value("SELECT logistics_id FROM trade_order WHERE id = " + ORDER_ID, Long.class));
        assertNull(value("SELECT logistics_no FROM trade_order WHERE id = " + ORDER_ID, String.class));
        assertNull(value("SELECT delivery_time FROM trade_order WHERE id = " + ORDER_ID, java.sql.Timestamp.class));
        assertEquals(0, count("trade_fulfillment_idempotency"));
    }

    @Test
    void zeroRowShipmentCasRollsBackNewPackageAndIdempotency() {
        jdbcTemplate.update("UPDATE trade_shipment SET status = 'DRAFT' WHERE id = ?", SHIPMENT_ID);
        doReturn(0).when(shipmentMapper).incrementVersionByIdAndVersion(TENANT_ID, SHIPMENT_ID, 1);

        assertThrows(Exception.class, () -> service.addPackage("private-package-cas-key",
                new UpsertPackageCommand().setTenantId(TENANT_ID).setShipmentId(SHIPMENT_ID).setExpectedVersion(1)
                        .setPackageNo("PKG-CAS").setPackageType("PARCEL").setCarrierId(73L)
                        .setTrackingNumber("private-package-cas-tracking")));

        assertEquals(0, value("SELECT COUNT(*) FROM trade_shipment_package WHERE package_no = 'PKG-CAS'",
                Integer.class));
        assertEquals(1, value("SELECT version FROM trade_shipment WHERE id = " + SHIPMENT_ID, Integer.class));
        assertEquals(0, count("trade_fulfillment_idempotency"));
    }

    @Test
    void addPackageAdvancesShipmentVersionOnceAndExactReplayDoesNotBumpAgain() {
        jdbcTemplate.update("UPDATE trade_shipment SET status = 'DRAFT' WHERE id = ?", SHIPMENT_ID);
        UpsertPackageCommand command = new UpsertPackageCommand().setTenantId(TENANT_ID)
                .setShipmentId(SHIPMENT_ID).setExpectedVersion(1).setPackageNo("PKG-VERSION")
                .setPackageType("PARCEL").setCarrierId(73L).setTrackingNumber("private-version-tracking");

        Long packageId = service.addPackage("private-package-version-key", command);
        Long replayedPackageId = service.addPackage("private-package-version-key", command);

        assertEquals(packageId, replayedPackageId);
        assertEquals(1, value("SELECT COUNT(*) FROM trade_shipment_package WHERE package_no = 'PKG-VERSION'",
                Integer.class));
        assertEquals(2, value("SELECT version FROM trade_shipment WHERE id = " + SHIPMENT_ID, Integer.class));
        assertEquals(1, count("trade_fulfillment_idempotency"));
    }

    @Test
    void zeroRowShipmentCasRollsBackNewLegAndIdempotency() {
        jdbcTemplate.update("UPDATE trade_shipment SET status = 'DRAFT' WHERE id = ?", SHIPMENT_ID);
        doReturn(0).when(shipmentMapper).incrementVersionByIdAndVersion(TENANT_ID, SHIPMENT_ID, 1);

        assertThrows(Exception.class, () -> service.addLeg("private-leg-cas-key",
                new AddShipmentLegCommand().setTenantId(TENANT_ID).setShipmentId(SHIPMENT_ID).setExpectedVersion(1)
                        .setPackageId(71001L).setSequenceNo(2).setLegType("LAST_MILE")
                        .setCarrierId(73L).setProviderId(83L).setTrackingNumber("private-leg-cas-tracking")));

        assertEquals(0, value("SELECT COUNT(*) FROM trade_shipment_leg WHERE sequence_no = 2", Integer.class));
        assertEquals(1, value("SELECT version FROM trade_shipment WHERE id = " + SHIPMENT_ID, Integer.class));
        assertEquals(0, count("trade_fulfillment_idempotency"));
    }

    @Test
    void addLegAdvancesShipmentVersionExactlyOnce() {
        jdbcTemplate.update("UPDATE trade_shipment SET status = 'DRAFT' WHERE id = ?", SHIPMENT_ID);

        Long legId = service.addLeg("private-leg-version-key",
                new AddShipmentLegCommand().setTenantId(TENANT_ID).setShipmentId(SHIPMENT_ID).setExpectedVersion(1)
                        .setPackageId(71001L).setSequenceNo(2).setLegType("LAST_MILE")
                        .setCarrierId(73L).setProviderId(83L).setTrackingNumber("private-leg-version-tracking"));

        assertEquals(1, value("SELECT COUNT(*) FROM trade_shipment_leg WHERE id = " + legId, Integer.class));
        assertEquals(2, value("SELECT version FROM trade_shipment WHERE id = " + SHIPMENT_ID, Integer.class));
        assertEquals(1, count("trade_fulfillment_idempotency"));
    }

    private void insertReadyAggregate() {
        jdbcTemplate.update("INSERT INTO trade_order (id, no, type, terminal, user_id, user_ip, status, "
                        + "product_count, pay_status, discount_price, delivery_price, adjust_price, pay_price, "
                        + "delivery_type, receiver_name, receiver_mobile, receiver_area_id, receiver_detail_address, "
                        + "coupon_id, coupon_price, point_price, tenant_id) "
                        + "VALUES (?, 'ORDER-DISPATCH', 0, 10, 200, '127.0.0.1', 10, 1, TRUE, 0, 0, 0, 3000, 1, "
                        + "'Receiver', '5550000000', 1, 'controlled-test-address', 0, 0, 0, ?)",
                ORDER_ID, TENANT_ID);
        jdbcTemplate.update("INSERT INTO trade_carrier "
                        + "(id, tenant_id, code, name, country_codes, legacy_express_id, status) "
                        + "VALUES (73, ?, 'UPS', 'Carrier', 'US,CA', 93, 0)", TENANT_ID);
        jdbcTemplate.update("INSERT INTO trade_logistics_provider "
                        + "(id, tenant_id, code, name, capabilities, status) "
                        + "VALUES (83, ?, 'mock', 'Mock', 'TRACKING_QUERY', 0)", TENANT_ID);
        jdbcTemplate.update("INSERT INTO trade_shipment (id, tenant_id, order_id, shipment_no, shipment_type, "
                        + "status, origin_country, destination_country, origin_timezone, destination_timezone, "
                        + "warehouse_id, provider_id, version) "
                        + "VALUES (?, ?, ?, 'SHP-DISPATCH-1', 'PARCEL', 'READY_TO_SHIP', 'US', 'US', "
                        + "'America/New_York', 'America/Chicago', 31, 83, 1)", SHIPMENT_ID, TENANT_ID, ORDER_ID);
        jdbcTemplate.update("INSERT INTO trade_shipment_item "
                        + "(id, tenant_id, shipment_id, order_item_id, sku_id, quantity) "
                        + "VALUES (70101, ?, ?, 501, 901, 1)", TENANT_ID, SHIPMENT_ID);
        jdbcTemplate.update("INSERT INTO trade_shipment_package "
                        + "(id, tenant_id, shipment_id, package_no, package_type, carrier_id, tracking_number, status, version) "
                        + "VALUES (71001, ?, ?, 'PKG-1', 'PARCEL', 73, 'private-tracking-123', 'DRAFT', 0)",
                TENANT_ID, SHIPMENT_ID);
        jdbcTemplate.update("INSERT INTO trade_shipment_leg "
                        + "(id, tenant_id, shipment_id, package_id, sequence_no, leg_type, carrier_id, provider_id, "
                        + "tracking_number, status, version) "
                        + "VALUES (72001, ?, ?, 71001, 1, 'LAST_MILE', 73, 83, 'private-tracking-123', 'DRAFT', 0)",
                TENANT_ID, SHIPMENT_ID);
        jdbcTemplate.update("INSERT INTO trade_order_fulfillment_summary "
                        + "(id, tenant_id, order_id, status, shipment_count, delivered_shipment_count, version) "
                        + "VALUES (9001, ?, ?, 'NOT_SHIPPED', 1, 0, 2)", TENANT_ID, ORDER_ID);
    }

    private int count(String table) {
        return value("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private <T> T value(String sql, Class<T> type) {
        return jdbcTemplate.queryForObject(sql, type);
    }

}
