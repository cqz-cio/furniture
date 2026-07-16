package cn.iocoder.yudao.module.trade.framework.fulfillment.cache;

import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("expressTrackCachePolicy")
@RequiredArgsConstructor
public class ExpressTrackCachePolicy {

    private final FulfillmentProperties properties;

    public boolean hasHmacKey() {
        return properties.getIdempotencyHmacKey() != null
                && !properties.getIdempotencyHmacKey().isBlank();
    }

}
