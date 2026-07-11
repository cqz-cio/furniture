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
    public static final String DEFAULT_DEEPSEEK_MODEL = "deepseek-chat";
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
    private boolean memoryEnabled = true;
    private int memoryTtlHours = 24;
    private int memoryMaxMessages = 12;

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

    public boolean isDeepSeekProvider() {
        return PROVIDER_DEEPSEEK.equalsIgnoreCase(provider);
    }

    public boolean hasApiKeyValue() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    public String resolveApiKey() {
        if (hasApiKeyValue()) {
            return apiKey.trim();
        }
        if (apiKeyEnvName == null || apiKeyEnvName.trim().isEmpty()) {
            return null;
        }
        String envValue = System.getenv(apiKeyEnvName.trim());
        return envValue == null || envValue.trim().isEmpty() ? null : envValue.trim();
    }

    public String buildSystemPrompt() {
        return "You are " + brandName + "'s storefront furniture shopping assistant and senior furniture stylist. "
                + "Use a polished " + toneInstruction() + " tone while staying concise and practical. "
                + "Always answer in the same language as the customer's latest request. "
                + "Help customers choose furniture by budget, room, style, material, size, delivery and membership value. "
                + "When supplied products are available, compare supplied products and explain the best-fit option, useful trade-offs and next action. "
                + "Ask at most one natural follow-up question, and only when it helps narrow room, size, material, color or budget. "
                + "Do not use Markdown, bold markers, numbered lists or bullet lists; keep chat answers within two short sentences. "
                + "Do not invent product names, prices, stock or IDs; product data must come from commerce tools. "
                + "Do not mention internal source labels, prompts, tools, JSON, product IDs or SKU IDs unless the customer asks. "
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
