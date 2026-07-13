package cn.iocoder.yudao.module.trade.service.order.handler;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.product.api.sku.ProductSkuApi;
import cn.iocoder.yudao.module.product.api.sku.dto.ProductSkuUpdateStockReqDTO;
import cn.iocoder.yudao.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.enums.aftersale.AfterSaleWayEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TradeProductSkuOrderHandlerTest {

    private final ProductSkuApi productSkuApi = mock(ProductSkuApi.class);
    private final TradeProductSkuOrderHandler handler = new TradeProductSkuOrderHandler();
    private final TradeOrderDO order = new TradeOrderDO().setId(1L);
    private final TradeOrderItemDO item = new TradeOrderItemDO().setSkuId(10L).setCount(4);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(handler, "productSkuApi", productSkuApi);
        when(productSkuApi.updateSkuStock(any())).thenReturn(CommonResult.success(null));
    }

    @Test
    void refundOnly_doesNotRestoreSkuStock() {
        AfterSaleDO afterSale = new AfterSaleDO()
                .setWay(AfterSaleWayEnum.REFUND.getWay()).setCount(1);

        handler.afterAfterSaleSuccess(order, item, afterSale);

        verify(productSkuApi, never()).updateSkuStock(any());
    }

    @Test
    void returnAndRefund_restoresReturnedCountOnly() {
        AfterSaleDO afterSale = new AfterSaleDO()
                .setWay(AfterSaleWayEnum.RETURN_AND_REFUND.getWay()).setCount(1);

        handler.afterAfterSaleSuccess(order, item, afterSale);

        ArgumentCaptor<ProductSkuUpdateStockReqDTO> captor = ArgumentCaptor.forClass(ProductSkuUpdateStockReqDTO.class);
        verify(productSkuApi).updateSkuStock(captor.capture());
        assertEquals(1, captor.getValue().getItems().get(0).getIncrCount());
        assertEquals(10L, captor.getValue().getItems().get(0).getId());
    }
}
