package cn.iocoder.yudao.module.product.service.furniture;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "yudao.furniture-assistant")
public class FurnitureAssistantProperties {

    public static final String KNOWLEDGE_PROVIDER_KEYWORD = "keyword";
    public static final String KNOWLEDGE_PROVIDER_AI = "ai";
    public static final String PROVIDER_DEEPSEEK = "deepseek";
    public static final String DEFAULT_DEEPSEEK_BASE_URL = "https://api.deepseek.com";
    public static final String DEFAULT_DEEPSEEK_MODEL = "deepseek-v4-flash";
    public static final String DEFAULT_DEEPSEEK_API_KEY_ENV_NAME = "DEEPSEEK_API_KEY";

    /**
     * Default MVP provider. Set to "ai" only after the real AI/RAG provider is wired.
     */
    private String knowledgeProvider = KNOWLEDGE_PROVIDER_KEYWORD;
    private String provider = PROVIDER_DEEPSEEK;
    private String baseUrl = DEFAULT_DEEPSEEK_BASE_URL;
    private String model = DEFAULT_DEEPSEEK_MODEL;
    private String apiKeyEnvName = DEFAULT_DEEPSEEK_API_KEY_ENV_NAME;
    private String apiKey;
    private String brandName = "Trendz";
    private String tone = "luxury";

    public static FurnitureAssistantProperties keywordMode() {
        FurnitureAssistantProperties properties = new FurnitureAssistantProperties();
        properties.setKnowledgeProvider(KNOWLEDGE_PROVIDER_KEYWORD);
        return properties;
    }

    public static FurnitureAssistantProperties aiMode() {
        FurnitureAssistantProperties properties = new FurnitureAssistantProperties();
        properties.setKnowledgeProvider(KNOWLEDGE_PROVIDER_AI);
        return properties;
    }

    public boolean isAiKnowledgeProvider() {
        return KNOWLEDGE_PROVIDER_AI.equalsIgnoreCase(knowledgeProvider);
    }

    public boolean hasApiKeyValue() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    public String buildSystemPrompt() {
        return "You are " + brandName + "'s storefront furniture shopping assistant. "
                + "Use a polished " + toneInstruction() + " tone while staying concise and practical. "
                + "Help customers choose furniture by budget, room, style, material, delivery and membership value. "
                + "Do not invent product names, prices, stock or IDs; product data must come from commerce tools. "
                + "Do not use admin-only tools or expose internal operations. "
                + "Return answers that fit the storefront contract: answer, products and sources.";
    }

    private String toneInstruction() {
        if ("luxury".equalsIgnoreCase(tone)) {
            return "high-end";
        }
        return tone;
    }

}
