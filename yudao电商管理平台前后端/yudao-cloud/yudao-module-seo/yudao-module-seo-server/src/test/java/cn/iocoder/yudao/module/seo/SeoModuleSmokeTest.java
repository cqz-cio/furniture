package cn.iocoder.yudao.module.seo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class SeoModuleSmokeTest {

    @Test
    void seoApplicationShouldBeASpringBootApplication() {
        assertThat(AnnotatedElementUtils.hasAnnotation(
                SeoServerApplication.class, SpringBootApplication.class)).isTrue();
    }

    @Test
    void mainConfigurationShouldLoadPropertiesFromEveryYamlDocument() throws IOException {
        PropertySourcesPropertyResolver properties = loadYaml("application.yaml");

        assertThat(properties.getProperty("spring.application.name")).isEqualTo("seo-server");
        assertThat(properties.getProperty("spring.data.redis.repositories.enabled", Boolean.class)).isFalse();
        assertThat(properties.getProperty("yudao.seo.analysis.bm25.enabled", Boolean.class)).isFalse();
        assertThat(properties.getProperty("yudao.seo.analysis.semantic.enabled", Boolean.class)).isFalse();
        assertThat(properties.getProperty("yudao.seo.analysis.document.max-file-size")).isEqualTo("16MB");
        assertThat(properties.getProperty("spring.servlet.multipart.max-file-size")).isEqualTo("16MB");
    }

    @Test
    void unitConfigurationShouldLoadModuleIdentityAndDataSource() throws IOException {
        PropertySourcesPropertyResolver properties = loadYaml("application-unit-test.yaml");

        assertThat(properties.getProperty("spring.application.name")).isEqualTo("seo-server-test");
        assertThat(properties.getProperty("spring.datasource.name")).isEqualTo("ruoyi-vue-pro");
        assertThat(properties.getProperty("yudao.info.base-package")).isEqualTo("cn.iocoder.yudao.module.seo");
    }

    private static PropertySourcesPropertyResolver loadYaml(String location) throws IOException {
        MutablePropertySources propertySources = new MutablePropertySources();
        new YamlPropertySourceLoader().load(location, new ClassPathResource(location))
                .forEach(propertySources::addLast);
        return new PropertySourcesPropertyResolver(propertySources);
    }

}
