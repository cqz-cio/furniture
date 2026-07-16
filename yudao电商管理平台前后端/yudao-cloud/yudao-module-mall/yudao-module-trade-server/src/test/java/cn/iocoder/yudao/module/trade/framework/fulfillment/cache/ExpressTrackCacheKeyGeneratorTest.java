package cn.iocoder.yudao.module.trade.framework.fulfillment.cache;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpressTrackCacheKeyGeneratorTest {

    private static final String SECRET = "test-only-fulfillment-cache-key-32-bytes";
    private static final String TRACKING = "1Z-PRIVATE-123";
    private static final String PHONE = "+1-416-555-0199";

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void generateProducesTenantAwareOpaqueStableDigest() throws Exception {
        FulfillmentProperties properties = new FulfillmentProperties().setIdempotencyHmacKey(SECRET);
        ExpressTrackCacheKeyGenerator generator = new ExpressTrackCacheKeyGenerator(properties);
        Method method = Target.class.getDeclaredMethod("query", String.class, String.class, String.class);
        Object[] args = {"ups", TRACKING, PHONE};

        TenantContextHolder.setTenantId(121L);
        String first = (String) generator.generate(new Target(), method, args);
        String same = (String) generator.generate(new Target(), method, args);
        TenantContextHolder.setTenantId(122L);
        String otherTenant = (String) generator.generate(new Target(), method, args);

        assertTrue(first.matches("^express-track:[0-9a-f]{64}$"));
        assertTrue(first.equals(same));
        assertNotEquals(first, otherTenant);
        assertFalse(first.contains(TRACKING));
        assertFalse(first.contains(PHONE));
        assertFalse(first.contains("ups"));
        assertFalse(first.contains("121"));
        assertFalse(first.contains(SECRET));
    }

    @Test
    void generateUsesLengthSafeCanonicalTuple() throws Exception {
        FulfillmentProperties properties = new FulfillmentProperties().setIdempotencyHmacKey(SECRET);
        ExpressTrackCacheKeyGenerator generator = new ExpressTrackCacheKeyGenerator(properties);
        Method method = Target.class.getDeclaredMethod("query", String.class, String.class, String.class);
        TenantContextHolder.setTenantId(121L);

        String left = (String) generator.generate(new Target(), method, new Object[]{"a", "bc", "d"});
        String right = (String) generator.generate(new Target(), method, new Object[]{"ab", "c", "d"});

        assertNotEquals(left, right);
    }

    @Test
    void generateFailsClosedForBlankSecretMissingTenantOrUnexpectedSignature() throws Exception {
        Method method = Target.class.getDeclaredMethod("query", String.class, String.class, String.class);
        ExpressTrackCacheKeyGenerator blank = new ExpressTrackCacheKeyGenerator(new FulfillmentProperties());
        TenantContextHolder.setTenantId(121L);
        assertThrows(IllegalStateException.class,
                () -> blank.generate(new Target(), method, "ups", TRACKING, PHONE));

        ExpressTrackCacheKeyGenerator configured = new ExpressTrackCacheKeyGenerator(
                new FulfillmentProperties().setIdempotencyHmacKey(SECRET));
        TenantContextHolder.clear();
        assertThrows(NullPointerException.class,
                () -> configured.generate(new Target(), method, "ups", TRACKING, PHONE));
        TenantContextHolder.setTenantId(121L);
        assertThrows(IllegalArgumentException.class,
                () -> configured.generate(new Target(), method, "ups", TRACKING));
    }

    @SuppressWarnings("unused")
    private static final class Target {
        private void query(String code, String tracking, String phone) {
        }
    }

}
