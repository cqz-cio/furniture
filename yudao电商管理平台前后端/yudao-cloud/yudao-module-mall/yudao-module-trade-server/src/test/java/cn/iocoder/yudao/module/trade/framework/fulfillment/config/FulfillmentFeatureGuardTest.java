package cn.iocoder.yudao.module.trade.framework.fulfillment.config;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FulfillmentFeatureGuardTest {

    @Test
    void rejectsDisabledReadBeforeDownstreamWork() {
        FulfillmentFeatureGuard guard = new FulfillmentFeatureGuard(new FulfillmentProperties());
        AtomicInteger downstreamCalls = new AtomicInteger();

        assertThrows(RuntimeException.class, guard::requireReadEnabled);
        assertEquals(0, downstreamCalls.get());
    }

    @Test
    void rejectsDisabledWriteBeforeDownstreamWork() {
        FulfillmentFeatureGuard guard = new FulfillmentFeatureGuard(new FulfillmentProperties());
        AtomicInteger downstreamCalls = new AtomicInteger();

        assertThrows(RuntimeException.class, guard::requireWriteEnabled);
        assertEquals(0, downstreamCalls.get());
    }

    @Test
    void migrationWriteRequiresAllThreeFlags() {
        FulfillmentProperties properties = enabledProperties();
        FulfillmentFeatureGuard guard = new FulfillmentFeatureGuard(properties);
        assertThrows(RuntimeException.class, guard::requireMigrationWriteEnabled);

        properties.setLegacyMigrationWriteEnabled(true);
        assertDoesNotThrow(guard::requireMigrationWriteEnabled);
    }

    @Test
    void enabledBoundariesPass() {
        FulfillmentProperties properties = enabledProperties();
        FulfillmentFeatureGuard guard = new FulfillmentFeatureGuard(properties);

        assertDoesNotThrow(guard::requireReadEnabled);
        assertDoesNotThrow(guard::requireWriteEnabled);
    }

    private static FulfillmentProperties enabledProperties() {
        FulfillmentProperties properties = new FulfillmentProperties();
        properties.setEnabled(true);
        properties.setWriteNewModel(true);
        properties.setReadFromNewModel(true);
        properties.setProviderCode("real");
        properties.setIdempotencyHmacKey("unit-test-fulfillment-hmac-key-32-characters");
        return properties;
    }

}
