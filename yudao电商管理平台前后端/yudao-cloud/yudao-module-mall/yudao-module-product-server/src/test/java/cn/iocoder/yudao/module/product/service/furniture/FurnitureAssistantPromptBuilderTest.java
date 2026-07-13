package cn.iocoder.yudao.module.product.service.furniture;

import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantChatRespVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;
import cn.iocoder.yudao.module.product.service.furniture.search.FurnitureMatchType;

class FurnitureAssistantPromptBuilderTest {

    @Test
    void buildUserPrompt_shouldGroundModelInMatchedAndUnmetConstraints() {
        FurnitureAssistantChatRespVO.Product product = new FurnitureAssistantChatRespVO.Product();
        product.setId(1001L);
        product.setSkuId(2001L);
        product.setName("Solid-Wood Bed");
        product.setSkuProperties(Collections.singletonList("Material: Solid Wood"));
        product.setVariants(Collections.emptyList());

        FurnitureAssistantAiRequest request = new FurnitureAssistantAiRequest(
                "solid wood bed no wider than 180 cm", "Closest match only",
                Collections.singletonList(product), Collections.emptyList(),
                "No earlier conversation is available.", FurnitureMatchType.PARTIAL,
                java.util.Arrays.asList("category", "materials"), Collections.singletonList("maxWidthMm"));

        String prompt = FurnitureAssistantPromptBuilder.buildUserPrompt(request);

        assertTrue(prompt.contains("Match type: PARTIAL"));
        assertTrue(prompt.contains("Matched constraints: category, materials"));
        assertTrue(prompt.contains("Unmet constraints: maxWidthMm"));
        assertTrue(prompt.contains("Never describe an unmet constraint as satisfied"));
        assertTrue(prompt.contains("Material: Solid Wood"));
    }

    @Test
    void buildUserPrompt_shouldGroundModelInReturnedProductsAndSafetyRules() {
        FurnitureAssistantChatRespVO.Product product = new FurnitureAssistantChatRespVO.Product();
        product.setId(1001L);
        product.setSkuId(2001L);
        product.setName("Fabric Track Arm Sofa");
        product.setSubtitle("Soft cream fabric sofa for compact living rooms");
        product.setPrice(new BigDecimal("6999"));
        product.setMarketPrice(new BigDecimal("8999"));
        product.setStock(12);
        product.setReason("Matched against your request: cream fabric sofa under 8000.");

        FurnitureAssistantAiRequest request = new FurnitureAssistantAiRequest(
                "cream fabric sofa under 8000",
                "I found 1 live furniture products for \"cream fabric sofa under 8000\" from the current catalog.",
                Collections.singletonList(product),
                Collections.singletonList(new FurnitureAssistantKnowledgeMatch("knowledge", "Membership Rules",
                        "Membership prices can be used with eligible coupons at checkout."))
        );

        String prompt = FurnitureAssistantPromptBuilder.buildUserPrompt(request);

        assertTrue(prompt.contains("cream fabric sofa under 8000"));
        assertTrue(prompt.contains("Fabric Track Arm Sofa"));
        assertTrue(prompt.contains("1001"));
        assertTrue(prompt.contains("6999"));
        assertTrue(prompt.contains("Soft cream fabric sofa for compact living rooms"));
        assertTrue(prompt.contains("Membership Rules"));
        assertTrue(prompt.contains("Commerce product search has already run"));
        assertTrue(prompt.contains("Do not recommend products outside the supplied product list"));
    }

    @Test
    void buildUserPrompt_shouldTellModelHowToActLikeFurnitureSalesAdvisor() {
        FurnitureAssistantChatRespVO.Product first = new FurnitureAssistantChatRespVO.Product();
        first.setId(1001L);
        first.setSkuId(2001L);
        first.setName("Cream Fabric Sofa");
        first.setSubtitle("Soft cream fabric sofa for compact living rooms");
        first.setPrice(new BigDecimal("6999"));
        first.setMarketPrice(new BigDecimal("8999"));
        first.setStock(12);
        first.setReason("根据你的需求匹配：8000以内的米白色布艺沙发。");

        FurnitureAssistantChatRespVO.Product second = new FurnitureAssistantChatRespVO.Product();
        second.setId(1002L);
        second.setSkuId(2002L);
        second.setName("Cloud Modular Sofa");
        second.setSubtitle("Low, deep modular sofa with down-blend cushions");
        second.setPrice(new BigDecimal("3299"));
        second.setMarketPrice(new BigDecimal("4599"));
        second.setStock(18);
        second.setReason("根据你的需求匹配：8000以内的米白色布艺沙发。");

        FurnitureAssistantAiRequest request = new FurnitureAssistantAiRequest(
                "8000以内的米白色布艺沙发",
                "我从当前商品库里找到了 2 个和“8000以内的米白色布艺沙发”相关的上架家具。",
                java.util.Arrays.asList(first, second),
                Collections.emptyList()
        );

        String prompt = FurnitureAssistantPromptBuilder.buildUserPrompt(request);

        assertTrue(prompt.contains("Rank the strongest match first"));
        assertTrue(prompt.contains("Compare useful trade-offs"));
        assertTrue(prompt.contains("budget, room, style, material"));
        assertTrue(prompt.contains("Ask at most one follow-up question"));
        assertTrue(prompt.contains("Do not mention internal source labels"));
    }

    @Test
    void buildUserPrompt_shouldConstrainNoProductAnswers() {
        FurnitureAssistantAiRequest request = new FurnitureAssistantAiRequest(
                "找一张黑色岩板餐桌",
                "暂时没有找到和“找一张黑色岩板餐桌”匹配的上架商品。可以放宽房间、风格或品类再试试。",
                Collections.emptyList(),
                Collections.emptyList()
        );

        String prompt = FurnitureAssistantPromptBuilder.buildUserPrompt(request);

        assertTrue(prompt.contains("When product intent exists but no products are returned"));
        assertTrue(prompt.contains("do not name a product"));
        assertTrue(prompt.contains("suggest one useful clarification"));
    }

    @Test
    void buildUserPrompt_shouldIncludeRecentConversationForFollowUps() {
        FurnitureAssistantAiRequest request = new FurnitureAssistantAiRequest(
                "说中文", "好的，我会使用中文。", Collections.emptyList(), Collections.emptyList(),
                "user: 推荐一款沙发\nassistant: 你喜欢什么风格？");

        String prompt = FurnitureAssistantPromptBuilder.buildUserPrompt(request);

        assertTrue(prompt.contains("Recent conversation"));
        assertTrue(prompt.contains("推荐一款沙发"));
        assertTrue(prompt.contains("说中文"));
    }

}
