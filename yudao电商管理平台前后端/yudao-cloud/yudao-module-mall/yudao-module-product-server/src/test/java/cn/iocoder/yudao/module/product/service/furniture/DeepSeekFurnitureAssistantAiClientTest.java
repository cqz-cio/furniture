package cn.iocoder.yudao.module.product.service.furniture;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DeepSeekFurnitureAssistantAiClientTest {

    @Test
    void isEnabled_shouldReuseSpringAiDeepSeekApiKeyWhenFurnitureKeyIsMissing() {
        FurnitureAssistantProperties properties = FurnitureAssistantProperties.keywordMode();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.deepseek.api-key", "sk-spring-ai-deepseek");
        DeepSeekFurnitureAssistantAiClient client = new DeepSeekFurnitureAssistantAiClient(properties, environment);

        assertTrue(client.isEnabled());
    }

    @Test
    void defaultModel_shouldUseSupportedDeepSeekChatModel() {
        assertEquals("deepseek-chat", FurnitureAssistantProperties.DEFAULT_DEEPSEEK_MODEL);
    }

}
