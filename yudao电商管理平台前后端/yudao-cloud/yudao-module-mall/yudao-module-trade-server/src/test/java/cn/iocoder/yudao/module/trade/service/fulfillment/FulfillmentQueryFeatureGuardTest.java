package cn.iocoder.yudao.module.trade.service.fulfillment;

import cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo.ShipmentPageReqVO;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentItemMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentLegMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentPackageMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.TrackingEventMapper;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentFeatureGuard;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_FEATURE_DISABLED;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class FulfillmentQueryFeatureGuardTest {

    @InjectMocks private FulfillmentQueryServiceImpl queryService;
    @Mock private ShipmentMapper shipmentMapper;
    @Mock private ShipmentItemMapper itemMapper;
    @Mock private ShipmentPackageMapper packageMapper;
    @Mock private ShipmentLegMapper legMapper;
    @Mock private TrackingEventMapper trackingEventMapper;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(queryService, "featureGuard",
                new FulfillmentFeatureGuard(new FulfillmentProperties()));
    }

    @Test
    void globallyDisabledFeatureStopsEveryAdminQueryBeforeMapperAccess() {
        assertServiceException(() -> queryService.getShipment(1L, 2L), FULFILLMENT_FEATURE_DISABLED);
        assertServiceException(() -> queryService.getTimeline(1L, 2L), FULFILLMENT_FEATURE_DISABLED);
        assertServiceException(() -> queryService.getShipmentPage(1L, new ShipmentPageReqVO()),
                FULFILLMENT_FEATURE_DISABLED);

        verifyNoInteractions(shipmentMapper, itemMapper, packageMapper, legMapper, trackingEventMapper);
    }

}
