package cn.iocoder.yudao.module.trade.framework.fulfillment.config;

import org.springframework.beans.factory.InitializingBean;
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
    InitializingBean fulfillmentConfigurationValidator(FulfillmentProperties properties, Environment environment) {
        return () -> {
            if (!properties.isWriteNewModel() || !"mock".equalsIgnoreCase(properties.getProviderCode())) {
                return;
            }
            boolean mockAllowed = Arrays.stream(environment.getActiveProfiles()).anyMatch(MOCK_ALLOWED_PROFILES::contains);
            if (!mockAllowed) {
                throw new IllegalStateException("mock fulfillment provider requires local or unit-test profile");
            }
        };
    }

}
