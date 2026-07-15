package cn.iocoder.yudao.module.ai.framework.ai.config;

import cn.iocoder.yudao.module.ai.framework.ai.core.model.AiModelFactory;
import cn.iocoder.yudao.module.ai.framework.ai.core.model.gemini.GeminiChatModel;
import cn.iocoder.yudao.module.ai.framework.ai.core.model.siliconflow.SiliconFlowChatModel;
import cn.iocoder.yudao.module.ai.tool.method.PersonServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiKeylessConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AiAutoConfiguration.class)
            .withBean(PersonServiceImpl.class, PersonServiceImpl::new);

    @Test
    void shouldStartWithoutProviderCredentials() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AiModelFactory.class);
            assertThat(context).doesNotHaveBean(GeminiChatModel.class);
            assertThat(context).doesNotHaveBean(SiliconFlowChatModel.class);
        });
    }

    @Test
    void shouldDisableDashScopeAgentByDefault() throws IOException {
        List<PropertySource<?>> propertySources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yaml"));
        MutablePropertySources sources = new MutablePropertySources();
        propertySources.forEach(sources::addLast);
        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(sources);

        assertThat(resolver.getProperty("spring.ai.dashscope.agent.enabled", Boolean.class)).isFalse();
        assertThat(resolver.getProperty("spring.ai.alibaba.tool.async.enabled", Boolean.class)).isFalse();
        assertThat(resolver.getProperty("spring.ai.model.chat")).isEqualTo("none");
        assertThat(resolver.getProperty("spring.ai.model.embedding")).isEqualTo("none");
        assertThat(resolver.getProperty("spring.ai.model.image")).isEqualTo("none");
        assertThat(resolver.getProperty("spring.ai.model.moderation")).isEqualTo("none");
        assertThat(resolver.getProperty("spring.ai.model.audio.speech")).isEqualTo("none");
        assertThat(resolver.getProperty("spring.ai.model.audio.transcription")).isEqualTo("none");
        assertThat(resolver.getProperty("spring.ai.model.video")).isEqualTo("none");
    }

}
