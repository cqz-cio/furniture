package cn.iocoder.yudao.module.trade.service.fulfillment.migration;

import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.FulfillmentIdempotencyMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.FulfillmentOutboxEventMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.OrderFulfillmentSummaryMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentItemMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentLegMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentPackageMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.TrackingEventMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentFeatureGuard;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentProperties;
import org.junit.jupiter.api.Test;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_FEATURE_DISABLED;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class FulfillmentLegacyMigrationWriterGuardTest {

    @Test
    void disabledMigrationShouldFailBeforeHmacOrAnyDatabaseInteraction() {
        TradeOrderMapper orderMapper = mock(TradeOrderMapper.class);
        LegacyMigrationEligibilityEvaluator evaluator = mock(LegacyMigrationEligibilityEvaluator.class);
        FulfillmentProperties properties = mock(FulfillmentProperties.class);
        FulfillmentFeatureGuard featureGuard = mock(FulfillmentFeatureGuard.class);
        FulfillmentIdempotencyMapper idempotencyMapper = mock(FulfillmentIdempotencyMapper.class);
        ShipmentMapper shipmentMapper = mock(ShipmentMapper.class);
        ShipmentItemMapper shipmentItemMapper = mock(ShipmentItemMapper.class);
        ShipmentPackageMapper packageMapper = mock(ShipmentPackageMapper.class);
        ShipmentLegMapper legMapper = mock(ShipmentLegMapper.class);
        TrackingEventMapper eventMapper = mock(TrackingEventMapper.class);
        OrderFulfillmentSummaryMapper summaryMapper = mock(OrderFulfillmentSummaryMapper.class);
        FulfillmentOutboxEventMapper outboxMapper = mock(FulfillmentOutboxEventMapper.class);
        FulfillmentLegacyMigrationWriterImpl writer = new FulfillmentLegacyMigrationWriterImpl(
                orderMapper, evaluator, properties, featureGuard, idempotencyMapper, shipmentMapper,
                shipmentItemMapper, packageMapper, legMapper, eventMapper, summaryMapper, outboxMapper);
        doThrow(exception(FULFILLMENT_FEATURE_DISABLED))
                .when(featureGuard).requireMigrationWriteEnabled();

        assertServiceException(() -> writer.migrateOne(121L, 91001L),
                FULFILLMENT_FEATURE_DISABLED);

        verify(featureGuard).requireMigrationWriteEnabled();
        verifyNoInteractions(properties, evaluator, orderMapper, idempotencyMapper, shipmentMapper,
                shipmentItemMapper, packageMapper, legMapper, eventMapper, summaryMapper, outboxMapper);
    }

}
