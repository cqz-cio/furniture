package cn.iocoder.yudao.module.trade.service.cart.event;
import cn.iocoder.yudao.module.statistics.api.behavior.StatisticsBehaviorApi;
import cn.iocoder.yudao.module.statistics.api.behavior.dto.CartBehaviorRecordReqDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
import javax.annotation.Resource;
@Component @Slf4j
public class CartAddedEventListener {
 @Resource private StatisticsBehaviorApi api;
 @Async @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
 public void onCartAdded(CartAddedEvent e){try{api.recordCartAdded(new CartBehaviorRecordReqDTO().setEventId(e.getEventId()).setUserId(e.getUserId()).setSpuId(e.getSpuId()).setSkuId(e.getSkuId()).setQuantity(e.getQuantity()).setVisitorId(e.getVisitorId()).setSessionId(e.getSessionId()).setConsentEvidence(e.getConsentEvidence())).checkError();}catch(Exception ex){log.warn("[recordCartAdded][cartId({}) statistics unavailable]",e.getCartId());}}
}
