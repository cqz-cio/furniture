package cn.iocoder.yudao.module.product.service.furniture;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantChatReqVO;
import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantChatRespVO;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class FurnitureAssistantServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private FurnitureAssistantServiceImpl service;

    @Mock
    private FurnitureProductSearchTool productSearchTool;

    @Mock
    private FurnitureAssistantKnowledgeService knowledgeService;

    @Mock
    private FurnitureAssistantAiClient aiClient;

    @Test
    void chat_shouldSearchProductSpusAndFilterByBudget() {
        when(knowledgeService.search(anyString())).thenReturn(Collections.emptyList());
        when(productSearchTool.shouldSearchProducts("cream fabric sofa under 8000", Collections.emptyList()))
                .thenReturn(true);
        when(productSearchTool.searchForAssistant("cream fabric sofa under 8000"))
                .thenReturn(FurnitureProductSearchResult.of(products(
                        product(1001L, "Fabric Track Arm Sofa", 6999),
                        product(1003L, "Compact Apartment Sofa", 7999)
                )));

        FurnitureAssistantChatReqVO reqVO = new FurnitureAssistantChatReqVO();
        reqVO.setMessage("cream fabric sofa under 8000");

        FurnitureAssistantChatRespVO response = service.chat(reqVO);

        verify(productSearchTool).searchForAssistant("cream fabric sofa under 8000");

        assertTrue(response.getAnswer().contains("2"));
        assertEquals(2, response.getProducts().size());
        assertEquals(1001L, response.getProducts().get(0).getId());
        assertEquals("Fabric Track Arm Sofa", response.getProducts().get(0).getName());
        assertEquals(new BigDecimal("6999"), response.getProducts().get(0).getPrice());
        assertEquals(new BigDecimal("8999"), response.getProducts().get(0).getMarketPrice());
        assertEquals("/sofa-pdp?id=1001", response.getProducts().get(0).getDetailUrl());
        assertEquals(1003L, response.getProducts().get(1).getId());
        assertEquals("product-api", response.getSources().get(0).getType());
    }

    @Test
    void chat_shouldAnswerKnowledgeQuestionWithoutSearchingProducts() {
        when(knowledgeService.search(anyString())).thenReturn(Collections.singletonList(
                new FurnitureAssistantKnowledgeMatch("knowledge", "Membership Rules",
                        "Membership prices can be used with eligible coupons at checkout.")
        ));

        FurnitureAssistantChatReqVO reqVO = new FurnitureAssistantChatReqVO();
        reqVO.setMessage("Can membership price stack with coupons?");

        FurnitureAssistantChatRespVO response = service.chat(reqVO);

        verify(productSearchTool, never()).searchForAssistant(anyString());
        assertTrue(response.getAnswer().contains("Membership prices can be used"));
        assertEquals(0, response.getProducts().size());
        assertEquals(2, response.getSources().size());
        assertEquals("fallback", response.getSources().get(0).getType());
        assertEquals("knowledge", response.getSources().get(1).getType());
        assertEquals("Membership Rules", response.getSources().get(1).getName());
    }

    @Test
    void chat_shouldTreatLanguagePreferenceAsConversationInsteadOfProductSearch() {
        when(knowledgeService.search(anyString())).thenReturn(Collections.emptyList());
        when(productSearchTool.shouldSearchProducts("说中文", Collections.emptyList())).thenReturn(false);

        FurnitureAssistantChatReqVO reqVO = new FurnitureAssistantChatReqVO();
        reqVO.setMessage("说中文");

        FurnitureAssistantChatRespVO response = service.chat(reqVO);

        verify(productSearchTool, never()).searchForAssistant(anyString());
        assertTrue(response.getAnswer().contains("接下来我会使用中文"));
        assertTrue(response.getProducts().isEmpty());
        assertEquals("fallback", response.getSources().get(0).getType());
    }

    @Test
    void chat_shouldUseChineseFallbackWhenRequestIsChineseAndAiIsDisabled() {
        when(knowledgeService.search(anyString())).thenReturn(Collections.emptyList());
        when(productSearchTool.shouldSearchProducts("8000以内的米白色布艺沙发", Collections.emptyList()))
                .thenReturn(true);
        when(productSearchTool.searchForAssistant("8000以内的米白色布艺沙发"))
                .thenReturn(FurnitureProductSearchResult.of(Collections.singletonList(
                        product(1001L, "Cream Fabric Sofa", 6999)
                )));

        FurnitureAssistantChatReqVO reqVO = new FurnitureAssistantChatReqVO();
        reqVO.setMessage("8000以内的米白色布艺沙发");

        FurnitureAssistantChatRespVO response = service.chat(reqVO);

        assertTrue(response.getAnswer().contains("我从当前商品库里找到了 1 个"));
        assertEquals(1, response.getProducts().size());
        assertEquals("product-api", response.getSources().get(0).getType());
    }

    @Test
    void chat_shouldUseAiAnswerWhenClientIsEnabledWithoutChangingProductCards() {
        when(knowledgeService.search(anyString())).thenReturn(Collections.emptyList());
        when(productSearchTool.shouldSearchProducts("cream fabric sofa under 8000", Collections.emptyList()))
                .thenReturn(true);
        when(productSearchTool.searchForAssistant("cream fabric sofa under 8000"))
                .thenReturn(FurnitureProductSearchResult.of(Collections.singletonList(
                        product(1001L, "Fabric Track Arm Sofa", 6999)
                )));
        when(aiClient.isEnabled()).thenReturn(true);
        when(aiClient.getSourceName()).thenReturn("DeepSeek deepseek-v4-flash");
        when(aiClient.generateAnswer(any(FurnitureAssistantAiRequest.class)))
                .thenReturn("The Fabric Track Arm Sofa is the strongest match for a compact cream living room.");

        FurnitureAssistantChatReqVO reqVO = new FurnitureAssistantChatReqVO();
        reqVO.setMessage("cream fabric sofa under 8000");

        FurnitureAssistantChatRespVO response = service.chat(reqVO);

        assertEquals("The Fabric Track Arm Sofa is the strongest match for a compact cream living room.",
                response.getAnswer());
        assertEquals(1, response.getProducts().size());
        assertEquals(1001L, response.getProducts().get(0).getId());
        assertEquals("Fabric Track Arm Sofa", response.getProducts().get(0).getName());
        assertEquals("model", response.getSources().get(1).getType());
        assertEquals("DeepSeek deepseek-v4-flash", response.getSources().get(1).getName());
    }

    @Test
    void chat_shouldCleanModelMarkdownAndKeepAnswerShortForChatBubble() {
        when(knowledgeService.search(anyString())).thenReturn(Collections.emptyList());
        when(productSearchTool.shouldSearchProducts("8000以内的米白色布艺沙发", Collections.emptyList()))
                .thenReturn(true);
        when(productSearchTool.searchForAssistant("8000以内的米白色布艺沙发"))
                .thenReturn(FurnitureProductSearchResult.of(products(
                        product(1001L, "Small Apartment Sofa", 3299),
                        product(1002L, "Cream Fabric Sofa", 6999)
                )));
        when(aiClient.isEnabled()).thenReturn(true);
        when(aiClient.getSourceName()).thenReturn("DeepSeek deepseek-chat");
        when(aiClient.generateAnswer(any(FurnitureAssistantAiRequest.class)))
                .thenReturn("您好，根据您的需求，我在当前商品库中为您找到了以下 **2款符合预算的米白色布艺沙发**，供您参考：\n"
                        + "1. **小户型沙发 Sofa**：价格 3,299，适合预算灵活的选择。\n"
                        + "2. **Cream Fabric Sofa**：价格 6,999，更贴近米白色布艺需求。"
                        + "如果您需要进一步了解尺寸、面料细节或配送信息，请随时告诉我。");

        FurnitureAssistantChatReqVO reqVO = new FurnitureAssistantChatReqVO();
        reqVO.setMessage("8000以内的米白色布艺沙发");

        FurnitureAssistantChatRespVO response = service.chat(reqVO);

        assertFalse(response.getAnswer().contains("**"));
        assertFalse(response.getAnswer().contains("1."));
        assertFalse(response.getAnswer().contains("2."));
        assertTrue(response.getAnswer().length() <= 160);
        assertEquals(2, response.getProducts().size());
        assertEquals("model", response.getSources().get(1).getType());
    }

    @Test
    void chat_shouldExposeFallbackSourceWhenEnabledModelCallFails() {
        when(knowledgeService.search(anyString())).thenReturn(Collections.emptyList());
        when(productSearchTool.shouldSearchProducts("sofa", Collections.emptyList())).thenReturn(true);
        when(productSearchTool.searchForAssistant("sofa")).thenReturn(FurnitureProductSearchResult.empty());
        when(aiClient.isEnabled()).thenReturn(true);
        doThrow(new IllegalStateException("model unavailable")).when(aiClient)
                .generateAnswer(any(FurnitureAssistantAiRequest.class));

        FurnitureAssistantChatReqVO reqVO = new FurnitureAssistantChatReqVO();
        reqVO.setMessage("sofa");

        FurnitureAssistantChatRespVO response = service.chat(reqVO);

        assertTrue(response.getSources().stream().anyMatch(source -> "fallback".equals(source.getType())));
    }

    private static java.util.List<FurnitureAssistantChatRespVO.Product> products(FurnitureAssistantChatRespVO.Product... products) {
        return java.util.Arrays.asList(products);
    }

    private static FurnitureAssistantChatRespVO.Product product(Long id, String name, int price) {
        FurnitureAssistantChatRespVO.Product product = new FurnitureAssistantChatRespVO.Product();
        product.setId(id);
        product.setSkuId(id);
        product.setName(name);
        product.setSubtitle("");
        product.setPrice(new BigDecimal(price));
        product.setMarketPrice(new BigDecimal(price + 2000));
        product.setStock(12);
        product.setCover("/images/" + id + ".jpg");
        product.setReason("Matched against your request.");
        product.setDetailUrl("/sofa-pdp?id=" + id);
        return product;
    }

}
