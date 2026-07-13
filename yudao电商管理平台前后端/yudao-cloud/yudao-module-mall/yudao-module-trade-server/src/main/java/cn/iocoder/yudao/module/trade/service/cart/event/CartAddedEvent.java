package cn.iocoder.yudao.module.trade.service.cart.event;
import lombok.Value;
@Value public class CartAddedEvent { String eventId; Long cartId; Long userId; Long spuId; Long skuId; Integer quantity; String visitorId; String sessionId; String consentEvidence; }
