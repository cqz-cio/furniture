package cn.iocoder.yudao.module.trade.controller.app.order.vo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AppTradeOrderCreateRespVOTest {

    @Test
    public void testCreateResponse_exposesOrderAndPayOrderIds() {
        AppTradeOrderCreateRespVO respVO = new AppTradeOrderCreateRespVO()
                .setId(901L)
                .setPayOrderId(7001L);

        assertEquals(901L, respVO.getId());
        assertEquals(7001L, respVO.getPayOrderId());
    }

}
