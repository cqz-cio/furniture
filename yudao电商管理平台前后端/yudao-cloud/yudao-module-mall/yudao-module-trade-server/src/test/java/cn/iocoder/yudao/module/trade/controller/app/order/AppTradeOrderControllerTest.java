package cn.iocoder.yudao.module.trade.controller.app.order;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.pay.api.notify.dto.PayOrderNotifyReqDTO;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderCommentCreateReqVO;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderCommentCreateRespVO;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.AppOrderExpressTrackRespDTO;
import cn.iocoder.yudao.module.trade.framework.delivery.core.client.dto.ExpressTrackRespDTO;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderQueryService;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderUpdateService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.ORDER_UPDATE_PAID_FAIL_PAY_ORDER_ID_ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AppTradeOrderControllerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private AppTradeOrderController appTradeOrderController;

    @Mock
    private TradeOrderUpdateService tradeOrderUpdateService;
    @Mock
    private TradeOrderQueryService tradeOrderQueryService;

    @Test
    public void testGetOrderExpressTrackList_preservesLegacyResponseContract() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 16, 10, 30);
        ExpressTrackRespDTO event = new ExpressTrackRespDTO()
                .setTime(occurredAt).setContent("IN_TRANSIT");
        when(tradeOrderQueryService.getExpressTrackList(501L, null)).thenReturn(List.of(event));

        CommonResult<List<AppOrderExpressTrackRespDTO>> result =
                appTradeOrderController.getOrderExpressTrackList(501L);

        assertEquals(1, result.getData().size());
        assertEquals(occurredAt, result.getData().get(0).getTime());
        assertEquals("IN_TRANSIT", result.getData().get(0).getContent());
        verify(tradeOrderQueryService).getExpressTrackList(501L, null);
    }

    @Test
    public void testUpdateOrderPaid_rejectsInvalidMerchantOrderId() {
        PayOrderNotifyReqDTO notifyReqDTO = PayOrderNotifyReqDTO.builder()
                .merchantOrderId("ORDER-100")
                .payOrderId(900L)
                .build();

        ServiceException serviceException = assertThrows(ServiceException.class,
                () -> appTradeOrderController.updateOrderPaid(notifyReqDTO));

        assertEquals(ORDER_UPDATE_PAID_FAIL_PAY_ORDER_ID_ERROR.getCode(), serviceException.getCode());
        verify(tradeOrderUpdateService, never()).updateOrderPaid(any(), any());
    }

    @Test
    public void testCreateOrderComments_delegatesToBatchCommentService() {
        AppTradeOrderCommentCreateReqVO reqVO = buildCommentReqVO();
        AppTradeOrderCommentCreateRespVO respVO = new AppTradeOrderCommentCreateRespVO()
                .setOrderId(100L)
                .setCommentedItemCount(2)
                .setCommentIds(Arrays.asList(9001L, 9002L));
        when(tradeOrderUpdateService.createOrderCommentsByMember(isNull(), eq(reqVO))).thenReturn(respVO);

        CommonResult<AppTradeOrderCommentCreateRespVO> result = appTradeOrderController.createOrderComments(reqVO);

        assertSame(respVO, result.getData());
        verify(tradeOrderUpdateService).createOrderCommentsByMember(isNull(), eq(reqVO));
    }

    private static AppTradeOrderCommentCreateReqVO buildCommentReqVO() {
        AppTradeOrderCommentCreateReqVO.Item item = new AppTradeOrderCommentCreateReqVO.Item()
                .setOrderItemId(201L)
                .setDescriptionScores(5)
                .setBenefitScores(5)
                .setContent("整体满意")
                .setPicUrls(Collections.emptyList());
        return new AppTradeOrderCommentCreateReqVO()
                .setOrderId(100L)
                .setAnonymous(Boolean.FALSE)
                .setItems(Collections.singletonList(item));
    }

}
