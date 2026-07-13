package cn.iocoder.yudao.module.trade.service.cart.event;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.statistics.api.behavior.StatisticsBehaviorApi;
import cn.iocoder.yudao.module.statistics.api.behavior.dto.CartBehaviorRecordReqDTO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
class CartAddedEventListenerTest {
 @Test void rpcFailure_neverChangesCommittedCartResult(){
  StatisticsBehaviorApi api=mock(StatisticsBehaviorApi.class); when(api.recordCartAdded(any())).thenThrow(new RuntimeException("down"));
  CartAddedEventListener listener=new CartAddedEventListener(); ReflectionTestUtils.setField(listener,"api",api);
  assertDoesNotThrow(()->listener.onCartAdded(event()));
 }
 @Test void success_forcesServerCartTransportFields(){
  StatisticsBehaviorApi api=mock(StatisticsBehaviorApi.class); when(api.recordCartAdded(any())).thenReturn(CommonResult.success(true));
  CartAddedEventListener listener=new CartAddedEventListener(); ReflectionTestUtils.setField(listener,"api",api); listener.onCartAdded(event());
  org.mockito.ArgumentCaptor<CartBehaviorRecordReqDTO> c=org.mockito.ArgumentCaptor.forClass(CartBehaviorRecordReqDTO.class); verify(api).recordCartAdded(c.capture());
  org.junit.jupiter.api.Assertions.assertEquals(2,c.getValue().getQuantity()); org.junit.jupiter.api.Assertions.assertEquals(20L,c.getValue().getSpuId());
 }
 private CartAddedEvent event(){return new CartAddedEvent("evt",1L,9L,20L,10L,2,"visitor","session");}
}
