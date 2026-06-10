package cn.iocoder.yudao.module.product.service.furniture;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FurnitureAssistantKnowledgeServiceImplTest {

    private final FurnitureAssistantKnowledgeServiceImpl service =
            new FurnitureAssistantKnowledgeServiceImpl(FurnitureAssistantProperties.keywordMode());

    @Test
    void search_shouldReturnMembershipKnowledge() {
        List<FurnitureAssistantKnowledgeMatch> matches = service.search("Can membership price stack with coupons?");

        assertEquals(1, matches.size());
        assertEquals("knowledge", matches.get(0).getType());
        assertEquals("Membership Rules", matches.get(0).getName());
        assertTrue(matches.get(0).getContent().contains("eligible coupons"));
    }

    @Test
    void search_shouldReturnDeliveryKnowledge() {
        List<FurnitureAssistantKnowledgeMatch> matches = service.search("How does delivery work for large furniture?");

        assertEquals(1, matches.size());
        assertEquals("Delivery And Installation", matches.get(0).getName());
        assertTrue(matches.get(0).getContent().contains("address coverage"));
    }

    @Test
    void search_shouldFailClearlyWhenRealAiModeIsSelectedBeforeIntegration() {
        FurnitureAssistantKnowledgeServiceImpl aiModeService =
                new FurnitureAssistantKnowledgeServiceImpl(FurnitureAssistantProperties.aiMode());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> aiModeService.search("Can membership price stack with coupons?"));
        assertTrue(exception.getMessage().contains("real AI provider is not wired yet"));
    }

}
