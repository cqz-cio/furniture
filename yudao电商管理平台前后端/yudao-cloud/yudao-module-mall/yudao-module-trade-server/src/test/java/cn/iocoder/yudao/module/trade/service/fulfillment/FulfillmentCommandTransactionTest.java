package cn.iocoder.yudao.module.trade.service.fulfillment;

import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.FulfillmentOutboxEventDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.FulfillmentOutboxEventMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentTypeEnum;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentProperties;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.CreateShipmentCommand;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.CreateShipmentItemCommand;
import cn.iocoder.yudao.module.trade.service.fulfillment.support.FulfillmentNoGenerator;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import jakarta.annotation.Resource;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@Import({FulfillmentCommandServiceImpl.class, FulfillmentProperties.class, FulfillmentNoGenerator.class,
        FulfillmentCommandTransactionTest.TenantDbTestConfiguration.class})
class FulfillmentCommandTransactionTest extends BaseDbUnitTest {

    private static final Long TENANT_ID = 121L;
    private static final Long OTHER_TENANT_ID = 122L;
    private static final Long ORDER_ID = 100L;
    private static final Long ORDER_ITEM_ID = 501L;
    private static final Long SKU_ID = 901L;

    @Resource
    private FulfillmentCommandService service;
    @Resource
    private FulfillmentProperties properties;
    @Resource
    private TradeOrderMapper tradeOrderMapper;
    @Resource
    private TenantLineInnerInterceptor tenantLineInnerInterceptor;
    @Resource
    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private FulfillmentOutboxEventMapper outboxMapper;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        properties.setIdempotencyHmacKey("transaction-test-hmac-secret");
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
    void rollsBackIdempotencyShipmentItemsSummaryAndOutboxWhenOutboxInsertFails() {
        insertOrderAndItem(TENANT_ID, ORDER_ID, ORDER_ITEM_ID);
        doThrow(new IllegalStateException("outbox insert failed"))
                .when(outboxMapper).insert(any(FulfillmentOutboxEventDO.class));

        assertThrows(IllegalStateException.class,
                () -> service.createShipment("private-idempotency-key", command()));

        assertEquals(0, count("trade_fulfillment_idempotency"));
        assertEquals(0, count("trade_shipment"));
        assertEquals(0, count("trade_shipment_item"));
        assertEquals(0, count("trade_order_fulfillment_summary"));
        assertEquals(0, count("trade_fulfillment_outbox_event"));
        assertEquals(1, count("trade_order"));
        assertEquals(1, count("trade_order_item"));
    }

    @Test
    void forUpdateLookupUsesActualTenantInterceptor() {
        insertOrderAndItem(OTHER_TENANT_ID, ORDER_ID, ORDER_ITEM_ID);

        TradeOrderDO hidden = TenantUtils.execute(TENANT_ID,
                () -> tradeOrderMapper.selectByIdForUpdate(ORDER_ID));
        TradeOrderDO visible = TenantUtils.execute(OTHER_TENANT_ID,
                () -> tradeOrderMapper.selectByIdForUpdate(ORDER_ID));

        assertNull(hidden);
        assertNotNull(visible);
        assertEquals(ORDER_ID, visible.getId());
    }

    private void insertOrderAndItem(Long tenantId, Long orderId, Long orderItemId) {
        jdbcTemplate.update("INSERT INTO trade_order (id, no, type, terminal, user_id, user_ip, status, "
                        + "product_count, pay_status, discount_price, delivery_price, adjust_price, pay_price, "
                        + "delivery_type, receiver_name, receiver_mobile, receiver_area_id, receiver_detail_address, "
                        + "coupon_id, coupon_price, point_price, tenant_id) "
                        + "VALUES (?, ?, 0, 10, 200, '127.0.0.1', 20, 3, TRUE, 0, 0, 0, 3000, 1, "
                        + "'Receiver', '5550000000', 1, 'controlled-test-address', 0, 0, 0, ?)",
                orderId, "ORDER-" + tenantId, tenantId);
        jdbcTemplate.update("INSERT INTO trade_order_item (id, user_id, order_id, spu_id, spu_name, sku_id, "
                        + "count, price, discount_price, pay_price, after_sale_status, tenant_id) "
                        + "VALUES (?, 200, ?, 701, 'Test Product', ?, 3, 1000, 0, 3000, 0, ?)",
                orderItemId, orderId, SKU_ID, tenantId);
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static CreateShipmentCommand command() {
        return new CreateShipmentCommand()
                .setTenantId(TENANT_ID)
                .setOrderId(ORDER_ID)
                .setShipmentType(ShipmentTypeEnum.PARCEL)
                .setOriginCountry("US")
                .setDestinationCountry("US")
                .setOriginTimezone("America/New_York")
                .setDestinationTimezone("America/Chicago")
                .setWarehouseId(31L)
                .setProviderId(41L)
                .setItems(List.of(new CreateShipmentItemCommand()
                        .setOrderItemId(ORDER_ITEM_ID)
                        .setSkuId(SKU_ID)
                        .setQuantity(BigDecimal.ONE)));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TenantProperties.class)
    static class TenantDbTestConfiguration {

        @Bean
        @Lazy(false)
        TenantLineInnerInterceptor tenantLineInnerInterceptor(TenantProperties properties,
                                                               MybatisPlusInterceptor interceptor) {
            TenantLineInnerInterceptor inner =
                    new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(properties));
            MyBatisUtils.addInterceptor(interceptor, inner, 0);
            return inner;
        }

    }

}
