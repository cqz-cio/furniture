package cn.iocoder.yudao.module.trade.service.order;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentProperties;
import cn.iocoder.yudao.module.trade.service.fulfillment.FulfillmentLegacyProjectionResult;
import cn.iocoder.yudao.module.trade.service.fulfillment.FulfillmentLegacyProjectionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.ORDER_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeOrderQueryServiceImplTest extends BaseMockitoUnitTest {

    private static final Long TENANT_ID = 121L;
    private static final Long ORDER_ID = 501L;
    private static final Long USER_ID = 601L;

    @InjectMocks
    private TradeOrderQueryServiceImpl service;

    @Mock
    private TradeOrderMapper orderMapper;
    @Mock
    private FulfillmentProperties properties;
    @Mock
    private FulfillmentLegacyProjectionService projectionService;

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void appOwnershipFailureStopsBeforeProjection() {
        when(orderMapper.selectByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.getExpressTrackList(ORDER_ID, USER_ID));

        assertEquals(ORDER_NOT_FOUND.getCode(), exception.getCode());
        verify(projectionService, never()).project(TENANT_ID, ORDER_ID);
    }

    @Test
    void adminMissingOrderStopsBeforeProjection() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.getExpressTrackList(ORDER_ID));

        assertEquals(ORDER_NOT_FOUND.getCode(), exception.getCode());
        verify(projectionService, never()).project(TENANT_ID, ORDER_ID);
    }

    @Test
    void disabledReadFlagPreservesLegacyEmptyBehavior() {
        TradeOrderDO order = new TradeOrderDO().setId(ORDER_ID).setUserId(USER_ID).setLogisticsId(null);
        when(orderMapper.selectByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(order);
        when(properties.isReadFromNewModel()).thenReturn(false);

        assertEquals(List.of(), service.getExpressTrackList(ORDER_ID, USER_ID));

        verify(projectionService, never()).project(TENANT_ID, ORDER_ID);
    }

    @Test
    void authoritativeEmptyNeverFallsBackToLegacyProvider() {
        TenantContextHolder.setTenantId(TENANT_ID);
        TradeOrderDO order = new TradeOrderDO().setId(ORDER_ID).setUserId(USER_ID).setLogisticsId(99L);
        when(orderMapper.selectByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(order);
        when(properties.isReadFromNewModel()).thenReturn(true);
        when(projectionService.project(TENANT_ID, ORDER_ID))
                .thenReturn(FulfillmentLegacyProjectionResult.authoritative(List.of()));

        assertEquals(List.of(), service.getExpressTrackList(ORDER_ID, USER_ID));

        verify(projectionService).project(TENANT_ID, ORDER_ID);
    }

    @Test
    void newModelFallbackPreservesLegacyEmptyBehavior() {
        TenantContextHolder.setTenantId(TENANT_ID);
        TradeOrderDO order = new TradeOrderDO().setId(ORDER_ID).setUserId(USER_ID).setLogisticsId(null);
        when(orderMapper.selectByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(order);
        when(properties.isReadFromNewModel()).thenReturn(true);
        when(projectionService.project(TENANT_ID, ORDER_ID))
                .thenReturn(FulfillmentLegacyProjectionResult.fallback());

        assertEquals(List.of(), service.getExpressTrackList(ORDER_ID, USER_ID));

        verify(projectionService).project(TENANT_ID, ORDER_ID);
    }

}
