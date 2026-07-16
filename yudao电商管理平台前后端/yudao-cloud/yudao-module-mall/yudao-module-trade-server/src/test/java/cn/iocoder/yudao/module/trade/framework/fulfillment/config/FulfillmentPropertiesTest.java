package cn.iocoder.yudao.module.trade.framework.fulfillment.config;

import cn.iocoder.yudao.module.trade.framework.fulfillment.core.impl.MockLogisticsProviderClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

class FulfillmentPropertiesTest {

    private static final String PREFIX = "yudao.trade.fulfillment.";
    private static final String TEST_KEY = "unit-test-fulfillment-hmac-key-32-characters";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(FulfillmentConfiguration.class, MockLogisticsProviderClient.class);

    @Test
    void registersPropertiesExactlyOnceWithSafeDefaults() {
        contextRunner.run(context -> {
            assertTrue(context.isRunning());
            assertTrue(context.getBeansOfType(FulfillmentProperties.class).size() == 1);
            FulfillmentProperties properties = context.getBean(FulfillmentProperties.class);
            assertFalse(properties.isEnabled());
            assertFalse(properties.isWriteNewModel());
            assertFalse(properties.isReadFromNewModel());
            assertFalse(properties.isCustomerUiEnabled());
            assertFalse(properties.isLegacyMigrationWriteEnabled());
            assertTrue(properties.getProviderCode().isEmpty());
            assertTrue(properties.getIdempotencyHmacKey().isEmpty());
        });
    }

    @Test
    void blankWriteSecretsRemainValidWhenAllWritesAreOff() {
        contextRunner.withPropertyValues(PREFIX + "enabled=true", PREFIX + "read-from-new-model=true")
                .run(context -> assertTrue(context.isRunning()));
    }

    @Test
    void rejectsEveryInvalidFlagDependency() {
        assertStartupFails(PREFIX + "write-new-model=true", PREFIX + "provider-code=real",
                PREFIX + "idempotency-hmac-key=" + TEST_KEY);
        assertStartupFails(PREFIX + "enabled=true", PREFIX + "write-new-model=true",
                PREFIX + "provider-code=real");
        assertStartupFails(PREFIX + "enabled=true", PREFIX + "write-new-model=true",
                PREFIX + "idempotency-hmac-key=" + TEST_KEY);
        assertStartupFails(PREFIX + "read-from-new-model=true");
        assertStartupFails(PREFIX + "enabled=true", PREFIX + "customer-ui-enabled=true");
        assertStartupFails(PREFIX + "enabled=true", PREFIX + "legacy-migration-write-enabled=true");
    }

    @Test
    void rejectsMockWritesOutsideLocalAndUnitTestProfiles() {
        ApplicationContextRunner writeRunner = contextRunner.withPropertyValues(
                PREFIX + "enabled=true", PREFIX + "write-new-model=true",
                PREFIX + "provider-code=mock", PREFIX + "idempotency-hmac-key=" + TEST_KEY);
        writeRunner.run(context -> assertThat(context).hasFailed());
        writeRunner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void permitsAndRegistersMockOnlyForLocalAndUnitTestProfiles() {
        for (String profile : new String[]{"local", "unit-test"}) {
            contextRunner.withInitializer(context -> context.getEnvironment().setActiveProfiles(profile))
                    .withPropertyValues(PREFIX + "enabled=true", PREFIX + "write-new-model=true",
                            PREFIX + "provider-code=mock", PREFIX + "idempotency-hmac-key=" + TEST_KEY)
                    .run(context -> {
                        assertTrue(context.isRunning());
                        assertTrue(context.getBeansOfType(MockLogisticsProviderClient.class).size() == 1);
                    });
        }
        contextRunner.withPropertyValues(PREFIX + "provider-code=mock")
                .run(context -> {
                    assertTrue(context.isRunning());
                    assertTrue(context.getBeansOfType(MockLogisticsProviderClient.class).isEmpty());
                });
    }

    @Test
    void neverRendersHmacSecretInPropertiesString() {
        contextRunner.withPropertyValues(PREFIX + "idempotency-hmac-key=" + TEST_KEY)
                .run(context -> assertFalse(context.getBean(FulfillmentProperties.class)
                        .toString().contains(TEST_KEY)));
    }

    private void assertStartupFails(String... propertyValues) {
        contextRunner.withPropertyValues(propertyValues).run(context -> assertThat(context).hasFailed());
    }

}
