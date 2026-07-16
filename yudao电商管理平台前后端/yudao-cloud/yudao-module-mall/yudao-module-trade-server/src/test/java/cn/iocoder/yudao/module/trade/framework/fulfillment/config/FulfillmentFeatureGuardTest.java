package cn.iocoder.yudao.module.trade.framework.fulfillment.config;

import org.junit.jupiter.api.Test;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_FEATURE_DISABLED;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class FulfillmentFeatureGuardTest {

    @Test
    void rejectsDisabledReadBeforeDownstreamWork() {
        FulfillmentFeatureGuard guard = new FulfillmentFeatureGuard(new FulfillmentProperties());
        assertServiceException(guard::requireReadEnabled, FULFILLMENT_FEATURE_DISABLED);
    }

    @Test
    void rejectsDisabledWriteBeforeDownstreamWork() {
        FulfillmentFeatureGuard guard = new FulfillmentFeatureGuard(new FulfillmentProperties());
        assertServiceException(guard::requireWriteEnabled, FULFILLMENT_FEATURE_DISABLED);
    }

    @Test
    void migrationWriteRequiresAllThreeFlags() {
        FulfillmentProperties properties = enabledProperties();
        FulfillmentFeatureGuard guard = new FulfillmentFeatureGuard(properties);
        assertServiceException(guard::requireMigrationWriteEnabled, FULFILLMENT_FEATURE_DISABLED);

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
