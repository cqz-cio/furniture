package cn.iocoder.yudao.module.product.service.furniture.conversation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FurnitureAssistantRequirementMergerTest {

    private final FurnitureAssistantRequirementMerger merger = new FurnitureAssistantRequirementMerger();

    @Test
    void shouldPreserveRequirementsAcrossTurns() {
        FurnitureAssistantConversation conversation = FurnitureAssistantConversation.newConversation("c-1");
        merger.merge(conversation, "我想要8000元以内的布艺沙发。");
        merger.merge(conversation, "奶油风，家里有猫。");

        FurnitureAssistantRequirements value = conversation.getRequirements();
        assertEquals("sofa", value.getCategory());
        assertEquals(0, new BigDecimal("8000").compareTo(value.getBudgetMax()));
        assertEquals(Collections.singletonList("fabric"), value.getMaterials());
        assertEquals(Collections.singletonList("cream-style"), value.getStyles());
        assertEquals(Boolean.TRUE, value.getHasPets());
    }

    @Test
    void shouldReplaceAnExplicitlyMentionedListField() {
        FurnitureAssistantConversation conversation = FurnitureAssistantConversation.newConversation("c-1");
        merger.merge(conversation, "我想要布艺沙发。");

        merger.merge(conversation, "改成实木的。");

        assertEquals(Collections.singletonList("solid-wood"), conversation.getRequirements().getMaterials());
        assertEquals("sofa", conversation.getRequirements().getCategory());
    }

    @Test
    void shouldCaptureMaterialExclusionAndPetCapabilities() {
        FurnitureAssistantConversation conversation = FurnitureAssistantConversation.newConversation("c-1");

        merger.merge(conversation, "家里有两只猫，推荐深色、非真皮、容易清洁的耐用单椅。");

        FurnitureAssistantRequirements value = conversation.getRequirements();
        assertEquals("single-chair", value.getCategory());
        assertEquals(Collections.singletonList("dark"), value.getColors());
        assertEquals(Collections.singletonList("leather"), value.getExcludedMaterials());
        assertEquals(Boolean.TRUE, value.getHasPets());
        assertEquals(Boolean.TRUE, value.getEasyClean());
        assertTrue(value.getHardConstraints().contains("easyClean"));
        assertTrue(value.getNonRelaxableConstraints().contains("excludedMaterials"));
    }

    @Test
    void shouldClearCategorySpecificStateWhenCategoryChanges() {
        FurnitureAssistantConversation conversation = FurnitureAssistantConversation.newConversation("c-1");
        merger.merge(conversation, "三人沙发，宽度不超过220厘米，带储物。");

        merger.merge(conversation, "改成餐桌。");

        FurnitureAssistantRequirements value = conversation.getRequirements();
        assertEquals("dining-table", value.getCategory());
        assertNull(value.getSeatCount());
        assertNull(value.getMaxWidthMm());
        assertTrue(value.getPreferredFeatures().isEmpty());
    }

    @Test
    void shouldCaptureRoundedEdgeChildUse() {
        FurnitureAssistantConversation conversation = FurnitureAssistantConversation.newConversation("c-1");

        merger.merge(conversation, "帮我找适合儿童房的圆角书桌。");

        FurnitureAssistantRequirements value = conversation.getRequirements();
        assertEquals("desk", value.getCategory());
        assertEquals(Boolean.TRUE, value.getHasChildren());
        assertEquals(Collections.singletonList("children-room"), value.getRoomTypes());
        assertTrue(value.getPreferredFeatures().contains("rounded-edges"));
        assertTrue(value.getHardConstraints().contains("preferredFeatures"));
    }

    @Test
    void shouldClearOnlyRetractedRequirement() {
        FurnitureAssistantConversation conversation = FurnitureAssistantConversation.newConversation("c-1");
        merger.merge(conversation, "推荐深棕色实木床。");

        merger.merge(conversation, "颜色不限了，但仍要实木。");

        FurnitureAssistantRequirements value = conversation.getRequirements();
        assertEquals("bed", value.getCategory());
        assertTrue(value.getColors().isEmpty());
        assertEquals(Collections.singletonList("solid-wood"), value.getMaterials());
        assertFalse(value.getHardConstraints().contains("colors"));
    }

    @Test
    void shouldNotTreatBedroomAsBedCategory() {
        FurnitureAssistantConversation conversation = FurnitureAssistantConversation.newConversation("c-1");
        merger.merge(conversation, "3 seat sofa under 220 cm wide with storage.");

        merger.merge(conversation, "For the bedroom.");

        FurnitureAssistantRequirements value = conversation.getRequirements();
        assertEquals("sofa", value.getCategory());
        assertEquals(Integer.valueOf(3), value.getSeatCount());
        assertEquals(Integer.valueOf(2200), value.getMaxWidthMm());
        assertEquals(Collections.singletonList("storage"), value.getPreferredFeatures());
        assertEquals(Collections.singletonList("bedroom"), value.getRoomTypes());
    }

    @Test
    void shouldAccumulateMaterialExclusionsAcrossTurns() {
        FurnitureAssistantConversation conversation = FurnitureAssistantConversation.newConversation("c-1");
        merger.merge(conversation, "No leather.");

        merger.merge(conversation, "Also no glass.");

        assertEquals(java.util.Arrays.asList("leather", "glass"),
                conversation.getRequirements().getExcludedMaterials());
    }

    @Test
    void shouldRemoveOnlyExplicitlyRetractedMaterialExclusion() {
        FurnitureAssistantConversation conversation = FurnitureAssistantConversation.newConversation("c-1");
        merger.merge(conversation, "No leather and no glass.");

        merger.merge(conversation, "Leather is okay now.");

        assertEquals(Collections.singletonList("glass"), conversation.getRequirements().getExcludedMaterials());
    }

    @Test
    void shouldExcludeFirstRecommendationAndLowerBudget() {
        FurnitureAssistantConversation conversation = FurnitureAssistantConversation.newConversation("c-1");
        conversation.getLastRecommendations().add(new FurnitureAssistantConversation.RecommendationRef(
                1001L, 2001L, new BigDecimal("7600")));

        merger.merge(conversation, "第一款太贵，换个便宜一点的。");

        assertTrue(conversation.getExcludedProductIds().contains(1001L));
        assertEquals(0, new BigDecimal("7599").compareTo(conversation.getRequirements().getBudgetMax()));
    }
}
