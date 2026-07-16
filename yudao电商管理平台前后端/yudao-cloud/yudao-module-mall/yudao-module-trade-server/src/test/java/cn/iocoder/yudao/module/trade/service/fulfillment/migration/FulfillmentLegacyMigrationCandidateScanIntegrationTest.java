package cn.iocoder.yudao.module.trade.service.fulfillment.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@Import(FulfillmentLegacyMigrationServiceImpl.class)
class FulfillmentLegacyMigrationCandidateScanIntegrationTest extends BaseDbUnitTest {

    private static final Long TENANT_ID = 901L;

    @Resource private TradeOrderMapper orderMapper;
    @Resource private FulfillmentLegacyMigrationService migrationService;
    @Resource private DataSource dataSource;
    @MockBean private LegacyMigrationEligibilityEvaluator evaluator;
    @MockBean private FulfillmentLegacyMigrationWriter writer;

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void candidateMapperEnforcesTenantDeletionActiveStatusesLogisticsCursorOrderingAndLimitPlusOne() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        insertOrder(jdbc, 109L, TENANT_ID, 20, 77L, null, false);
        insertOrder(jdbc, 101L, TENANT_ID, 10, 77L, null, false);
        insertOrder(jdbc, 105L, TENANT_ID, 20, null, "TRACK-105", false);
        insertOrder(jdbc, 102L, TENANT_ID + 1, 20, 77L, "TRACK-102", false);
        insertOrder(jdbc, 103L, TENANT_ID, 20, 77L, "TRACK-103", true);
        insertOrder(jdbc, 104L, TENANT_ID, 0, 77L, "TRACK-104", false);
        insertOrder(jdbc, 106L, TENANT_ID, 30, 77L, "TRACK-106", false);
        insertOrder(jdbc, 107L, TENANT_ID, 20, null, "   ", false);
        insertOrder(jdbc, 108L, TENANT_ID, 20, 0L, null, false);

        List<TradeOrderDO> firstTwoPlusOne = orderMapper.selectLegacyMigrationCandidates(
                TENANT_ID, 100L, 3);
        assertEquals(List.of(101L, 105L, 109L), firstTwoPlusOne.stream().map(TradeOrderDO::getId).toList());

        List<TradeOrderDO> exclusiveCursor = orderMapper.selectLegacyMigrationCandidates(
                TENANT_ID, 101L, 10);
        assertEquals(List.of(105L, 109L), exclusiveCursor.stream().map(TradeOrderDO::getId).toList());
    }

    @Test
    void rejectedRowsStillAdvanceCursorAndPreserveHasMore() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        insertOrder(jdbc, 201L, TENANT_ID, 20, 77L, "TRACK-201", false);
        insertOrder(jdbc, 202L, TENANT_ID, 20, 77L, "TRACK-202", false);
        insertOrder(jdbc, 203L, TENANT_ID, 20, 77L, "TRACK-203", false);
        when(evaluator.evaluate(eq(TENANT_ID), any(TradeOrderDO.class))).thenAnswer(invocation -> {
            TradeOrderDO order = invocation.getArgument(1);
            return MigrationOrderResult.of(order.getId(), MigrationOutcome.INVALID_CARRIER);
        });
        TenantContextHolder.setTenantId(TENANT_ID);

        MigrationBatchResult first = migrationService.migrateActiveOrders(TENANT_ID, 0L, 2, true);
        assertEquals(List.of(201L, 202L), first.orders().stream().map(MigrationOrderResult::orderId).toList());
        assertEquals(202L, first.nextAfterOrderId());
        assertEquals(2, first.rejected());
        assertTrue(first.hasMore());

        MigrationBatchResult second = migrationService.migrateActiveOrders(
                TENANT_ID, first.nextAfterOrderId(), 2, true);
        assertEquals(List.of(203L), second.orders().stream().map(MigrationOrderResult::orderId).toList());
        assertEquals(203L, second.nextAfterOrderId());
        assertEquals(1, second.rejected());
        assertFalse(second.hasMore());
    }

    private static void insertOrder(JdbcTemplate jdbc, Long id, Long tenantId, int status,
                                    Long logisticsId, String logisticsNo, boolean deleted) {
        jdbc.update("INSERT INTO trade_order (id, no, type, terminal, user_id, user_ip, status, "
                        + "product_count, pay_status, discount_price, delivery_price, adjust_price, pay_price, "
                        + "delivery_type, logistics_id, logistics_no, receiver_name, receiver_mobile, "
                        + "receiver_area_id, receiver_detail_address, coupon_id, coupon_price, point_price, "
                        + "deleted, tenant_id) VALUES (?, ?, 0, 10, 1, '127.0.0.1', ?, 1, TRUE, 0, 0, 0, "
                        + "100, 1, ?, ?, 'Receiver', '5550000000', 1, 'Address', 0, 0, 0, ?, ?)",
                id, "ORDER-" + id, status, logisticsId, logisticsNo, deleted, tenantId);
    }
}
