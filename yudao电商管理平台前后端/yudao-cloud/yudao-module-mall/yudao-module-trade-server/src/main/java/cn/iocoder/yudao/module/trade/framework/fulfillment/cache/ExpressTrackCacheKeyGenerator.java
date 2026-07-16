package cn.iocoder.yudao.module.trade.framework.fulfillment.cache;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentProperties;
import cn.iocoder.yudao.module.trade.service.fulfillment.support.FulfillmentHashing;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

@Component("expressTrackCacheKeyGenerator")
@RequiredArgsConstructor
public class ExpressTrackCacheKeyGenerator implements KeyGenerator {

    private static final String DOMAIN = "express-track-cache:v1";

    private final FulfillmentProperties properties;

    @Override
    public Object generate(Object target, Method method, Object... params) {
        if (params == null || params.length != 3) {
            throw new IllegalArgumentException("Express tracking cache key requires exactly three arguments");
        }
        String code = requireString(params[0]);
        String trackingNumber = requireString(params[1]);
        String phone = requireString(params[2]);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String canonical = DOMAIN + encode(String.valueOf(tenantId)) + encode(code)
                + encode(trackingNumber) + encode(phone);
        String digest = FulfillmentHashing.hmacSha256Hex(properties.getIdempotencyHmacKey(), canonical);
        return "express-track:" + digest;
    }

    private static String requireString(Object value) {
        if (value == null) {
            return "";
        }
        if (!(value instanceof String string)) {
            throw new IllegalArgumentException("Express tracking cache key arguments must be strings");
        }
        return string;
    }

    private static String encode(String value) {
        return "|" + value.getBytes(StandardCharsets.UTF_8).length + ":" + value;
    }

}
