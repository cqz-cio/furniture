package cn.iocoder.yudao.module.trade.service.fulfillment;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentTypeEnum;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentFeatureGuard;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentProperties;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.LogisticsProviderRegistry;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.CreateShipmentCommand;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.CreateShipmentItemCommand;
import cn.iocoder.yudao.module.trade.service.fulfillment.support.FulfillmentNoGenerator;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Import({FulfillmentCommandServiceImpl.class, FulfillmentFeatureGuard.class, FulfillmentProperties.class,
        FulfillmentNoGenerator.class,
        FulfillmentCommandTransactionTest.TenantDbTestConfiguration.class})
class FulfillmentCommandAtomicityTest extends BaseDbUnitTest {

    private static final Long TENANT_ID = 121L;
    private static final Long ORDER_ID = 100L;
    private static final Long ORDER_ITEM_ID = 501L;
    private static final Long SKU_ID = 901L;

    @Resource
    private FulfillmentCommandService service;
    @Resource
    private FulfillmentProperties properties;
    @Resource
    private DataSource dataSource;
    @Resource
    private PlatformTransactionManager transactionManager;
    @MockBean
    private LogisticsProviderRegistry providerRegistry;
    @MockBean
    private FulfillmentTrackingRegistrationFailureService registrationFailureService;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        properties.setEnabled(true);
        properties.setWriteNewModel(true);
        properties.setProviderCode("mock");
        properties.setIdempotencyHmacKey("atomicity-test-hmac-secret");
        LoginUser loginUser = new LoginUser();
        loginUser.setId(110L);
        loginUser.setTenantId(TENANT_ID);
        loginUser.setUserType(1);
        SecurityFrameworkUtils.setLoginUser(loginUser, new MockHttpServletRequest());
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void domainAndRealOutboxRowsRollbackTogetherAtTransactionBoundary() {
        insertOrderAndItem();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThrows(RollbackSignal.class, () -> transaction.executeWithoutResult(status -> {
            Long shipmentId = service.createShipment("atomicity-private-key", command());
            assertNotNull(shipmentId);
            assertEquals(1, count("trade_fulfillment_idempotency"));
            assertEquals(1, count("trade_shipment"));
            assertEquals(1, count("trade_shipment_item"));
            assertEquals(1, count("trade_order_fulfillment_summary"));
            assertEquals(1, count("trade_fulfillment_outbox_event"));
            throw new RollbackSignal();
        }));

        assertEquals(0, count("trade_fulfillment_idempotency"));
        assertEquals(0, count("trade_shipment"));
        assertEquals(0, count("trade_shipment_item"));
        assertEquals(0, count("trade_order_fulfillment_summary"));
        assertEquals(0, count("trade_fulfillment_outbox_event"));
        assertEquals(1, count("trade_order"));
        assertEquals(1, count("trade_order_item"));
    }

    @Test
    void identicalDatabaseReplayReturnsSameShipmentWithoutDuplicateRows() {
        insertOrderAndItem();
        CreateShipmentCommand command = command();

        Long first = service.createShipment("stable-replay-key", command);
        Long replay = service.createShipment("stable-replay-key", command);

        assertEquals(first, replay);
        assertEquals(1, count("trade_fulfillment_idempotency"));
        assertEquals(1, count("trade_shipment"));
        assertEquals(1, count("trade_shipment_item"));
        assertEquals(1, count("trade_order_fulfillment_summary"));
        assertEquals(1, count("trade_fulfillment_outbox_event"));
    }

    private void insertOrderAndItem() {
        jdbcTemplate.update("INSERT INTO trade_order (id, no, type, terminal, user_id, user_ip, status, "
                        + "product_count, pay_status, discount_price, delivery_price, adjust_price, pay_price, "
                        + "delivery_type, receiver_name, receiver_mobile, receiver_area_id, receiver_detail_address, "
                        + "coupon_id, coupon_price, point_price, tenant_id) "
                        + "VALUES (?, 'ORDER-ATOMICITY', 0, 10, 200, '127.0.0.1', 20, 3, TRUE, 0, 0, 0, 3000, 1, "
                        + "'Receiver', '5550000000', 1, 'controlled-test-address', 0, 0, 0, ?)",
                ORDER_ID, TENANT_ID);
        jdbcTemplate.update("INSERT INTO trade_order_item (id, user_id, order_id, spu_id, spu_name, sku_id, "
                        + "count, price, discount_price, pay_price, after_sale_status, tenant_id) "
                        + "VALUES (?, 200, ?, 701, 'Test Product', ?, 3, 1000, 0, 3000, 0, ?)",
                ORDER_ITEM_ID, ORDER_ID, SKU_ID, TENANT_ID);
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static CreateShipmentCommand command() {
        return new CreateShipmentCommand()
                .setTenantId(TENANT_ID)
                .setOrderId(ORDER_ID)
                .setShipmentType(ShipmentTypeEnum.PARCEL)
                .setOriginCountry("CA")
                .setDestinationCountry("CA")
                .setOriginTimezone("America/Toronto")
                .setDestinationTimezone("America/Vancouver")
                .setWarehouseId(31L)
                .setProviderId(41L)
                .setItems(List.of(new CreateShipmentItemCommand()
                        .setOrderItemId(ORDER_ITEM_ID)
                        .setSkuId(SKU_ID)
                        .setQuantity(BigDecimal.ONE)));
    }

    private static final class RollbackSignal extends RuntimeException {
    }

}
