package cn.iocoder.yudao.module.trade.controller.app.order;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.pay.api.notify.dto.PayOrderNotifyReqDTO;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderUpdateService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.ORDER_UPDATE_PAID_FAIL_PAY_ORDER_ID_ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class AppTradeOrderControllerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private AppTradeOrderController appTradeOrderController;

    @Mock
    private TradeOrderUpdateService tradeOrderUpdateService;

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

}
