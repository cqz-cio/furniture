package cn.iocoder.yudao.module.product.service.furniture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FurnitureAssistantPropertiesTest {

    @Test
    void defaults_shouldPrepareTrendzDeepSeekConfigurationWithoutApiKeyValue() {
        FurnitureAssistantProperties properties = FurnitureAssistantProperties.keywordMode();

        assertEquals("Trendz", properties.getBrandName());
        assertEquals("luxury", properties.getTone());
        assertEquals("deepseek", properties.getProvider());
        assertTrue(properties.isDeepSeekProvider());
        assertEquals("https://api.deepseek.com", properties.getBaseUrl());
        assertEquals("deepseek-v4-flash", properties.getModel());
        assertEquals("DEEPSEEK_API_KEY", properties.getApiKeyEnvName());
        assertFalse(properties.hasApiKeyValue());
    }

    @Test
    void resolveApiKey_shouldPreferExplicitRuntimeSecret() {
        FurnitureAssistantProperties properties = FurnitureAssistantProperties.keywordMode();
        properties.setApiKey("  sk-runtime  ");

        assertEquals("sk-runtime", properties.resolveApiKey());
    }

    @Test
    void buildSystemPrompt_shouldKeepStorefrontAssistantSeparateFromAdminAi() {
        FurnitureAssistantProperties properties = FurnitureAssistantProperties.keywordMode();

        String prompt = properties.buildSystemPrompt();

        assertTrue(prompt.contains("Trendz"));
        assertTrue(prompt.contains("high-end"));
        assertTrue(prompt.contains("storefront furniture shopping assistant"));
        assertTrue(prompt.contains("Always answer in the same language"));
        assertTrue(prompt.contains("senior furniture stylist"));
        assertTrue(prompt.contains("compare supplied products"));
        assertTrue(prompt.contains("Ask at most one"));
        assertTrue(prompt.contains("Do not mention internal source labels"));
        assertTrue(prompt.contains("Do not invent product names, prices, stock or IDs"));
        assertTrue(prompt.contains("Do not use admin-only tools"));
    }

}
