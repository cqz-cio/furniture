package cn.iocoder.yudao.module.trade.framework.fulfillment.config;

import cn.iocoder.yudao.module.trade.framework.fulfillment.core.LogisticsProviderRegistry;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.impl.MockLogisticsProviderClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FulfillmentYamlBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(FulfillmentConfiguration.class, MockLogisticsProviderClient.class,
                    LogisticsProviderRegistry.class);

    @Test
    void sharedYamlKeepsEverythingDisabledAndSecretsOptional() {
        withYaml("yudao-server/src/main/resources/application.yaml").run(context -> {
            assertTrue(context.isRunning());
            assertDisabled(context.getBean(FulfillmentProperties.class));
            assertEquals("", context.getBean(FulfillmentProperties.class).getProviderCode());
            assertEquals("", context.getBean(FulfillmentProperties.class).getIdempotencyHmacKey());
        });
    }

    @Test
    void localYamlEnablesOnlyMasterAndWritesWithLocalCredentials() {
        withYaml("yudao-server/src/main/resources/application-local.yaml")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("local"))
                .run(context -> {
                    assertTrue(context.isRunning());
                    FulfillmentProperties properties = context.getBean(FulfillmentProperties.class);
                    assertTrue(properties.isEnabled());
                    assertTrue(properties.isWriteNewModel());
                    assertFalse(properties.isReadFromNewModel());
                    assertFalse(properties.isCustomerUiEnabled());
                    assertFalse(properties.isLegacyMigrationWriteEnabled());
                    assertEquals("mock", properties.getProviderCode());
                    assertTrue(properties.getIdempotencyHmacKey().startsWith("local-only-"));
                    assertTrue(properties.getIdempotencyHmacKey().length() >= 32);
                });
    }

    @Test
    void unitTestYamlKeepsFlagsOffWithDeterministicTestCredentials() {
        withYaml("yudao-module-mall/yudao-module-trade-server/src/test/resources/application-unit-test.yaml")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("unit-test"))
                .run(context -> {
                    assertTrue(context.isRunning());
                    FulfillmentProperties properties = context.getBean(FulfillmentProperties.class);
                    assertDisabled(properties);
                    assertEquals("mock", properties.getProviderCode());
                    assertTrue(properties.getIdempotencyHmacKey().startsWith("unit-test-only-"));
                    assertTrue(properties.getIdempotencyHmacKey().length() >= 32);
                });
    }

    private ApplicationContextRunner withYaml(String relativePath) {
        return contextRunner.withInitializer(context -> {
            Path resourcePath = cloudRoot().resolve(relativePath);
            try {
                List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(
                        relativePath, new FileSystemResource(resourcePath));
                for (int index = sources.size() - 1; index >= 0; index--) {
                    context.getEnvironment().getPropertySources().addFirst(sources.get(index));
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to load YAML resource", exception);
            }
        });
    }

    private static Path cloudRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null && !Files.isDirectory(current.resolve("yudao-server"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Unable to locate yudao-cloud root");
        }
        return current;
    }

    private static void assertDisabled(FulfillmentProperties properties) {
        assertFalse(properties.isEnabled());
        assertFalse(properties.isWriteNewModel());
        assertFalse(properties.isReadFromNewModel());
        assertFalse(properties.isCustomerUiEnabled());
        assertFalse(properties.isLegacyMigrationWriteEnabled());
    }

}
