package cn.iocoder.yudao.module.product.service.furniture.conversation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FurnitureAssistantRequirementMergerTest {

    private final FurnitureAssistantRequirementMerger merger = new FurnitureAssistantRequirementMerger();

    @Test
    void shouldPreserveRequirementsAcrossTurns() {
        FurnitureAssistantConversation conversation = FurnitureAssistantConversation.newConversation("c-1");
        merger.merge(conversation, "想要8000以内的布艺沙发");
        merger.merge(conversation, "奶油风，家里有猫");

        FurnitureAssistantRequirements value = conversation.getRequirements();
        assertEquals("sofa", value.getCategory());
        assertEquals(0, new BigDecimal("8000").compareTo(value.getBudgetMax()));
        assertTrue(value.getMaterials().contains("布艺"));
        assertTrue(value.getStyles().contains("奶油风"));
        assertEquals(Boolean.TRUE, value.getHasPets());
    }

    @Test
    void shouldExcludeFirstRecommendationAndLowerBudget() {
        FurnitureAssistantConversation conversation = FurnitureAssistantConversation.newConversation("c-1");
        conversation.getLastRecommendations().add(new FurnitureAssistantConversation.RecommendationRef(
                1001L, 2001L, new BigDecimal("7600")));

        merger.merge(conversation, "第一款太贵，换个便宜一点的");

        assertTrue(conversation.getExcludedProductIds().contains(1001L));
        assertEquals(0, new BigDecimal("7599").compareTo(conversation.getRequirements().getBudgetMax()));
    }

}
