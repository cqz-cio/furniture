package cn.iocoder.yudao.module.system.framework.navigation.config;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 家具后台导航配置。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FurnitureNavigationProperties.class)
public class FurnitureNavigationConfiguration {

    private static final String CATALOG_RESOURCE = "navigation/furniture-lite-menu-paths.json";

    @Bean
    public FurnitureNavigationCatalog furnitureNavigationCatalog(ObjectMapper objectMapper) throws IOException {
        ClassPathResource resource = new ClassPathResource(CATALOG_RESOURCE);
        List<String> configuredPaths;
        try (InputStream inputStream = resource.getInputStream()) {
            configuredPaths = objectMapper.readValue(inputStream, new TypeReference<>() {});
        }
        Set<String> normalizedPaths = new LinkedHashSet<>();
        configuredPaths.stream()
                .map(FurnitureNavigationConfiguration::normalizePath)
                .forEach(normalizedPaths::add);
        if (normalizedPaths.isEmpty()) {
            throw new IllegalStateException("家具导航目录不能为空: " + CATALOG_RESOURCE);
        }
        return new FurnitureNavigationCatalog(normalizedPaths);
    }

    static String normalizePath(String path) {
        if (StrUtil.isBlank(path)) {
            throw new IllegalStateException("家具导航目录不能包含空路径");
        }
        String normalized = "/" + StrUtil.strip(path.trim(), "/");
        return normalized.length() > 1 ? StrUtil.removeSuffix(normalized, "/") : normalized;
    }

}
