package cn.iocoder.yudao.module.trade.framework.fulfillment.config;

import cn.iocoder.yudao.module.trade.framework.fulfillment.core.LogisticsProviderRegistry;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FulfillmentProperties.class)
public class FulfillmentConfiguration {

    private static final Set<String> MOCK_ALLOWED_PROFILES = Set.of("local", "unit-test");

    @Bean
    InitializingBean fulfillmentConfigurationValidator(FulfillmentProperties properties, Environment environment,
                                                        ObjectProvider<LogisticsProviderRegistry> registryProvider) {
        return () -> {
            if (!properties.isWriteNewModel()) {
                return;
            }
            if ("mock".equalsIgnoreCase(properties.getProviderCode()) && !isMockAllowed(environment)) {
                throw new IllegalStateException("mock fulfillment provider requires local or unit-test profile");
            }
            LogisticsProviderRegistry registry = registryProvider.getIfAvailable();
            if (registry == null) {
                throw new IllegalStateException("fulfillment provider registry is unavailable");
            }
            registry.getClient(properties.getProviderCode());
        };
    }

    static boolean isMockAllowed(Environment environment) {
        String[] activeProfiles = environment.getActiveProfiles();
        return activeProfiles.length > 0
                && Arrays.stream(activeProfiles).allMatch(MOCK_ALLOWED_PROFILES::contains);
    }

}
