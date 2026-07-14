package cn.iocoder.yudao.module.product.service.furniture;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantChatReqVO;
import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantChatRespVO;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import cn.iocoder.yudao.module.product.service.furniture.search.FurnitureMatchType;

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
    void chat_shouldExposeExactMatchThroughTypedSearch() {
        when(knowledgeService.search(anyString())).thenReturn(Collections.emptyList());
        when(productSearchTool.shouldSearchProducts(anyString(), any(), anyList())).thenReturn(true);
        when(productSearchTool.searchProducts(any(FurnitureProductSearchRequest.class))).thenReturn(
                FurnitureProductSearchResult.of(FurnitureMatchType.EXACT,
                        Arrays.asList("category", "materials"), Collections.emptyList(),
                        Collections.singletonList(product(1001L, 2001L, "Solid-Wood Bed", 6999,
                                Collections.singletonList("Material: Solid Wood")))));

        FurnitureAssistantChatReqVO request = new FurnitureAssistantChatReqVO();
        request.setMessage("solid wood bed");

        FurnitureAssistantChatRespVO result = service.chat(request);

        assertEquals(FurnitureMatchType.EXACT, result.getMatchType());
        assertEquals(Arrays.asList("category", "materials"), result.getMatchedConstraints());
        assertTrue(result.getUnmetConstraints().isEmpty());
        ArgumentCaptor<FurnitureProductSearchRequest> captor = ArgumentCaptor.forClass(FurnitureProductSearchRequest.class);
        verify(productSearchTool).searchProducts(captor.capture());
        assertEquals("solid wood bed", captor.getValue().getMessage());
        assertTrue(captor.getValue().isIncludeAllVariants());
    }

    @Test
    void chat_shouldExposePartialMatchWithoutClaimingMissingConstraint() {
        when(knowledgeService.search(anyString())).thenReturn(Collections.emptyList());
        when(productSearchTool.shouldSearchProducts(anyString(), any(), anyList())).thenReturn(true);
        FurnitureProductSearchResult search = FurnitureProductSearchResult.of(
                FurnitureMatchType.PARTIAL,
                Arrays.asList("category", "materials"),
                Collections.singletonList("maxWidthMm"),
                Collections.singletonList(product(1001L, 2001L, "Solid-Wood Bed", 6999,
                        Collections.singletonList("Material: Solid Wood"))));
        when(productSearchTool.searchProducts(any(FurnitureProductSearchRequest.class))).thenReturn(search);

        FurnitureAssistantChatReqVO request = new FurnitureAssistantChatReqVO();
        request.setMessage("找宽度不超过180厘米的实木床");
        FurnitureAssistantChatRespVO result = service.chat(request);

        assertEquals(FurnitureMatchType.PARTIAL, result.getMatchType());
        assertEquals(Collections.singletonList("maxWidthMm"), result.getUnmetConstraints());
        assertTrue(result.getAnswer().contains("maxWidthMm"));
        assertFalse(result.getAnswer().contains("宽度符合"));
        assertEquals(Long.valueOf(2001L), result.getProducts().get(0).getSkuId());
    }

    @Test
    void chat_shouldReturnNoCardsAndRelaxationGuidanceForNoMatch() {
        when(knowledgeService.search(anyString())).thenReturn(Collections.emptyList());
        when(productSearchTool.shouldSearchProducts(anyString(), any(), anyList())).thenReturn(true);
        when(productSearchTool.searchProducts(any(FurnitureProductSearchRequest.class))).thenReturn(
                FurnitureProductSearchResult.of(FurnitureMatchType.NONE, Collections.emptyList(),
                        Arrays.asList("materials", "maxWidthMm"),
                        Collections.singletonList(product(1001L, "Should not leak", 6999))));

        FurnitureAssistantChatReqVO request = new FurnitureAssistantChatReqVO();
        request.setMessage("solid wood bed no wider than 180 cm");
        FurnitureAssistantChatRespVO result = service.chat(request);

        assertEquals(FurnitureMatchType.NONE, result.getMatchType());
        assertTrue(result.getProducts().isEmpty());
        assertTrue(result.getAnswer().contains("materials"));
        assertTrue(result.getAnswer().contains("maxWidthMm"));
        assertTrue(result.getAnswer().toLowerCase().contains("relax"));
    }

    @Test
    void chat_shouldGroupOnlySameSpuRowsAsSkuVariants() {
        when(knowledgeService.search(anyString())).thenReturn(Collections.emptyList());
        when(productSearchTool.shouldSearchProducts(anyString(), any(), anyList())).thenReturn(true);
        FurnitureAssistantChatRespVO.Product oak = product(1001L, 2001L, "Storage Bed", 6999,
                Arrays.asList("Material: Oak", "Size: 1.8m"));
        FurnitureAssistantChatRespVO.Product walnut = product(1001L, 2002L, "Storage Bed", 7299,
                Arrays.asList("Material: Walnut", "Size: 1.8m"));
        FurnitureAssistantChatRespVO.Product otherSpu = product(1002L, 3001L, "Other Bed", 5999,
                Collections.singletonList("Material: Pine"));
        when(productSearchTool.searchProducts(any(FurnitureProductSearchRequest.class))).thenReturn(
                FurnitureProductSearchResult.of(FurnitureMatchType.EXACT,
                        Collections.singletonList("category"), Collections.emptyList(),
                        Arrays.asList(oak, walnut, otherSpu)));

        FurnitureAssistantChatReqVO request = new FurnitureAssistantChatReqVO();
        request.setMessage("storage bed");
        FurnitureAssistantChatRespVO result = service.chat(request);

        assertEquals(2, result.getProducts().size());
        FurnitureAssistantChatRespVO.Product groupedBed = result.getProducts().get(0);
        assertEquals(Long.valueOf(1001L), groupedBed.getId());
        assertEquals(2, groupedBed.getVariants().size());
        assertEquals(Long.valueOf(2001L), groupedBed.getVariants().get(0).getSkuId());
        assertEquals(Arrays.asList("Material: Oak", "Size: 1.8m"),
                groupedBed.getVariants().get(0).getSkuProperties());
        assertEquals(new BigDecimal("6999"), groupedBed.getVariants().get(0).getPrice());
        assertEquals(Integer.valueOf(12), groupedBed.getVariants().get(0).getStock());
        assertTrue(groupedBed.getVariants().stream().noneMatch(variant -> Long.valueOf(3001L).equals(variant.getSkuId())));
        assertEquals(1, result.getProducts().get(1).getVariants().size());
    }

    @Test
    void chat_shouldSearchProductSpusAndFilterByBudget() {
        when(knowledgeService.search(anyString())).thenReturn(Collections.emptyList());
        when(productSearchTool.shouldSearchProducts(anyString(), any(), anyList()))
                .thenReturn(true);
        when(productSearchTool.searchProducts(any(FurnitureProductSearchRequest.class)))
                .thenReturn(FurnitureProductSearchResult.of(products(
                        product(1001L, "Fabric Track Arm Sofa", 6999),
                        product(1003L, "Compact Apartment Sofa", 7999)
                )));

        FurnitureAssistantChatReqVO reqVO = new FurnitureAssistantChatReqVO();
        reqVO.setMessage("cream fabric sofa under 8000");

        FurnitureAssistantChatRespVO response = service.chat(reqVO);

        verify(productSearchTool).searchProducts(any(FurnitureProductSearchRequest.class));

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

        verify(productSearchTool, never()).searchProducts(any(FurnitureProductSearchRequest.class));
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
        when(productSearchTool.shouldSearchProducts(anyString(), any(), anyList())).thenReturn(false);

        FurnitureAssistantChatReqVO reqVO = new FurnitureAssistantChatReqVO();
        reqVO.setMessage("说中文");

        FurnitureAssistantChatRespVO response = service.chat(reqVO);

        verify(productSearchTool, never()).searchProducts(any(FurnitureProductSearchRequest.class));
        assertTrue(response.getAnswer().contains("接下来我会使用中文"));
        assertTrue(response.getProducts().isEmpty());
        assertEquals("fallback", response.getSources().get(0).getType());
    }

    @Test
    void chat_shouldUseChineseFallbackWhenRequestIsChineseAndAiIsDisabled() {
        when(knowledgeService.search(anyString())).thenReturn(Collections.emptyList());
        when(productSearchTool.shouldSearchProducts(anyString(), any(), anyList()))
                .thenReturn(true);
        when(productSearchTool.searchProducts(any(FurnitureProductSearchRequest.class)))
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
        when(productSearchTool.shouldSearchProducts(anyString(), any(), anyList()))
                .thenReturn(true);
        when(productSearchTool.searchProducts(any(FurnitureProductSearchRequest.class)))
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
        when(productSearchTool.shouldSearchProducts(anyString(), any(), anyList()))
                .thenReturn(true);
        when(productSearchTool.searchProducts(any(FurnitureProductSearchRequest.class)))
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
        when(productSearchTool.shouldSearchProducts(anyString(), any(), anyList())).thenReturn(true);
        when(productSearchTool.searchProducts(any(FurnitureProductSearchRequest.class)))
                .thenReturn(FurnitureProductSearchResult.empty());
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
        return product(id, id, name, price, Collections.emptyList());
    }

    private static FurnitureAssistantChatRespVO.Product product(Long id, Long skuId, String name, int price,
                                                                 java.util.List<String> skuProperties) {
        FurnitureAssistantChatRespVO.Product product = new FurnitureAssistantChatRespVO.Product();
        product.setId(id);
        product.setSkuId(skuId);
        product.setSkuProperties(skuProperties);
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
