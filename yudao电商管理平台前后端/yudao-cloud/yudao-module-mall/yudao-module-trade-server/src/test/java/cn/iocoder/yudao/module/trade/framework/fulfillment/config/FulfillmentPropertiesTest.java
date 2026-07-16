package cn.iocoder.yudao.module.trade.framework.fulfillment.config;

import cn.iocoder.yudao.module.trade.framework.fulfillment.core.impl.MockLogisticsProviderClient;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.LogisticsProviderClient;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.LogisticsProviderRegistry;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.ProviderCapability;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.dto.TrackingQuery;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.dto.TrackingRegistrationCommand;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.dto.TrackingRegistrationResult;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.dto.TrackingSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

class FulfillmentPropertiesTest {

    private static final String PREFIX = "yudao.trade.fulfillment.";
    private static final String TEST_KEY = "unit-test-fulfillment-hmac-key-32-characters";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(FulfillmentConfiguration.class, MockLogisticsProviderClient.class,
                    LogisticsProviderRegistry.class);

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
        writeRunner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod", "local"))
                .run(context -> assertThat(context).hasFailed());
        writeRunner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod", "unit-test"))
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
        contextRunner.withInitializer(context -> context.getEnvironment().setActiveProfiles("local", "unit-test"))
                .withPropertyValues(PREFIX + "enabled=true", PREFIX + "write-new-model=true",
                        PREFIX + "provider-code=mock", PREFIX + "idempotency-hmac-key=" + TEST_KEY)
                .run(context -> assertTrue(context.isRunning()));
        contextRunner.withPropertyValues(PREFIX + "provider-code=mock")
                .run(context -> {
                    assertTrue(context.isRunning());
                    assertTrue(context.getBeansOfType(MockLogisticsProviderClient.class).isEmpty());
                });
    }

    @Test
    void mixedProfilesNeverRegisterMockEvenWhenARealProviderIsSelected() {
        for (String allowedProfile : new String[]{"local", "unit-test"}) {
            contextRunner.withUserConfiguration(RealProviderConfiguration.class)
                    .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod", allowedProfile))
                    .withPropertyValues(PREFIX + "enabled=true", PREFIX + "write-new-model=true",
                            PREFIX + "provider-code=real", PREFIX + "idempotency-hmac-key=" + TEST_KEY)
                    .run(context -> {
                        assertTrue(context.isRunning());
                        assertTrue(context.getBeansOfType(MockLogisticsProviderClient.class).isEmpty());
                        LogisticsProviderRegistry registry = context.getBean(LogisticsProviderRegistry.class);
                        assertThatThrownBy(() -> registry.getClient("mock"));
                    });
        }
    }

    @Test
    void rejectsWriteProviderThatCannotBeResolvedFromRegistry() {
        contextRunner.withPropertyValues(PREFIX + "enabled=true", PREFIX + "write-new-model=true",
                        PREFIX + "provider-code=real", PREFIX + "idempotency-hmac-key=" + TEST_KEY)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsWritesWhenProviderRegistryIsMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                .withUserConfiguration(FulfillmentConfiguration.class)
                .withPropertyValues(PREFIX + "enabled=true", PREFIX + "write-new-model=true",
                        PREFIX + "provider-code=real", PREFIX + "idempotency-hmac-key=" + TEST_KEY)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void acceptsWriteProviderThatResolvesToRegisteredClient() {
        contextRunner.withUserConfiguration(RealProviderConfiguration.class)
                .withPropertyValues(PREFIX + "enabled=true", PREFIX + "write-new-model=true",
                        PREFIX + "provider-code=real", PREFIX + "idempotency-hmac-key=" + TEST_KEY)
                .run(context -> {
                    assertTrue(context.isRunning());
                    assertTrue(context.getBean(LogisticsProviderRegistry.class).getClient("real")
                            instanceof RealProviderClient);
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

    @TestConfiguration(proxyBeanMethods = false)
    static class RealProviderConfiguration {

        @Bean
        LogisticsProviderClient realProviderClient() {
            return new RealProviderClient();
        }

    }

    static class RealProviderClient implements LogisticsProviderClient {

        @Override
        public String getProviderCode() {
            return "real";
        }

        @Override
        public Set<ProviderCapability> getCapabilities() {
            return Set.of();
        }

        @Override
        public TrackingRegistrationResult registerTracking(TrackingRegistrationCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TrackingSnapshot queryTracking(TrackingQuery query) {
            throw new UnsupportedOperationException();
        }

    }

}
