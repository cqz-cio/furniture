package cn.iocoder.yudao.module.ai.framework.ai.config;

import cn.iocoder.yudao.module.ai.framework.ai.core.model.AiModelFactory;
import cn.iocoder.yudao.module.ai.framework.ai.core.model.gemini.GeminiChatModel;
import cn.iocoder.yudao.module.ai.framework.ai.core.model.siliconflow.SiliconFlowChatModel;
import cn.iocoder.yudao.module.ai.tool.method.PersonServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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

}
