package cn.iocoder.yudao.module.product.service.furniture;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class DeepSeekFurnitureAssistantAiClient implements FurnitureAssistantAiClient {

    private static final String SPRING_AI_DEEPSEEK_API_KEY_PROPERTY = "spring.ai.deepseek.api-key";
    private static final String SPRING_AI_DEEPSEEK_MODEL_PROPERTY = "spring.ai.deepseek.chat.options.model";
    private static final String SPRING_AI_DEEPSEEK_BASE_URL_PROPERTY = "spring.ai.deepseek.base-url";
    private static final double DEFAULT_TEMPERATURE = 0.4D;
    private static final int DEFAULT_MAX_TOKENS = 800;
    private static final int DEFAULT_TIMEOUT_MILLIS = 30_000;

    private final FurnitureAssistantProperties properties;
    private final Environment environment;

    public DeepSeekFurnitureAssistantAiClient(FurnitureAssistantProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public boolean isEnabled() {
        return properties.isDeepSeekProvider() && StrUtil.isNotBlank(resolveApiKey());
    }

    @Override
    public String generateAnswer(FurnitureAssistantAiRequest request) {
        String apiKey = resolveApiKey();
        if (StrUtil.isBlank(apiKey)) {
            return request.getFallbackAnswer();
        }
        ChatCompletionResponse response = postChatCompletion(apiKey, buildChatCompletionRequest(request));
        String content = extractContent(response);
        return StrUtil.blankToDefault(content, request.getFallbackAnswer());
    }

    @Override
    public String getSourceName() {
        return "DeepSeek " + resolveModel();
    }

    private ChatCompletionRequest buildChatCompletionRequest(FurnitureAssistantAiRequest request) {
        ChatCompletionRequest completionRequest = new ChatCompletionRequest();
        completionRequest.setModel(resolveModel());
        completionRequest.setTemperature(DEFAULT_TEMPERATURE);
        completionRequest.setMaxTokens(DEFAULT_MAX_TOKENS);
        completionRequest.setMessages(Arrays.asList(
                new ChatMessage("system", properties.buildSystemPrompt()),
                new ChatMessage("user", FurnitureAssistantPromptBuilder.buildUserPrompt(request))));
        return completionRequest;
    }

    private String resolveApiKey() {
        String assistantApiKey = properties.resolveApiKey();
        if (StrUtil.isNotBlank(assistantApiKey)) {
            return assistantApiKey;
        }
        return trimToNull(environment.getProperty(SPRING_AI_DEEPSEEK_API_KEY_PROPERTY));
    }

    private String resolveModel() {
        String model = properties.getModel();
        if (StrUtil.isBlank(model) || FurnitureAssistantProperties.DEFAULT_DEEPSEEK_MODEL.equals(model)) {
            return StrUtil.blankToDefault(trimToNull(environment.getProperty(SPRING_AI_DEEPSEEK_MODEL_PROPERTY)),
                    FurnitureAssistantProperties.DEFAULT_DEEPSEEK_MODEL);
        }
        return model;
    }

    private ChatCompletionResponse postChatCompletion(String apiKey, ChatCompletionRequest request) {
        try (HttpResponse response = HttpRequest.post(buildChatCompletionsUrl())
                .header("Authorization", "Bearer " + apiKey)
                .contentType(ContentType.JSON.getValue())
                .body(JsonUtils.toJsonString(request))
                .timeout(DEFAULT_TIMEOUT_MILLIS)
                .execute()) {
            String body = response.body();
            if (!response.isOk()) {
                throw new IllegalStateException("DeepSeek chat completion failed: HTTP " + response.getStatus()
                        + " " + body);
            }
            return JsonUtils.parseObject(body, ChatCompletionResponse.class);
        }
    }

    private String buildChatCompletionsUrl() {
        String baseUrl = resolveBaseUrl();
        return StrUtil.removeSuffix(baseUrl, "/") + "/chat/completions";
    }

    private String resolveBaseUrl() {
        String baseUrl = properties.getBaseUrl();
        if (StrUtil.isBlank(baseUrl) || FurnitureAssistantProperties.DEFAULT_DEEPSEEK_BASE_URL.equals(baseUrl)) {
            return StrUtil.blankToDefault(trimToNull(environment.getProperty(SPRING_AI_DEEPSEEK_BASE_URL_PROPERTY)),
                    FurnitureAssistantProperties.DEFAULT_DEEPSEEK_BASE_URL);
        }
        return baseUrl;
    }

    private static String trimToNull(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private String extractContent(ChatCompletionResponse response) {
        if (response == null || CollUtil.isEmpty(response.getChoices())
                || response.getChoices().get(0) == null || response.getChoices().get(0).getMessage() == null) {
            return null;
        }
        return response.getChoices().get(0).getMessage().getContent();
    }

    @Data
    private static class ChatCompletionRequest {

        private String model;
        private List<ChatMessage> messages;
        private Double temperature;
        @JsonProperty("max_tokens")
        private Integer maxTokens;

    }

    @Data
    private static class ChatMessage {

        private String role;
        private String content;

        ChatMessage() {
        }

        ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

    }

    @Data
    private static class ChatCompletionResponse {

        private List<Choice> choices;

    }

    @Data
    private static class Choice {

        private ChatMessage message;

    }

}
