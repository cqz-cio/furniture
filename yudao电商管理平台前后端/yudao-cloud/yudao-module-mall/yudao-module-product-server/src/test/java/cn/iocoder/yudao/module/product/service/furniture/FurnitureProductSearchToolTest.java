package cn.iocoder.yudao.module.product.service.furniture;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantChatRespVO;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductSpuPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FurnitureProductSearchToolTest extends BaseMockitoUnitTest {

    @InjectMocks
    private FurnitureProductSearchTool tool;

    @Mock
    private ProductSpuService productSpuService;

    @Test
    void searchForAssistant_shouldExtractKeywordFilterBudgetAndReturnProductCards() {
        when(productSpuService.getSpuPage(any(AppProductSpuPageReqVO.class)))
                .thenReturn(new PageResult<>(Arrays.asList(
                        spu(1001L, "Fabric Track Arm Sofa", "A soft cream sofa", 699900, 899900, 12),
                        spu(1002L, "Oversized Leather Sofa", "Premium leather", 1280000, 1580000, 4),
                        spu(1003L, "Compact Apartment Sofa", "Fits small rooms", 799900, 999900, 8)
                ), 3L));

        FurnitureProductSearchResult result = tool.searchForAssistant("cream fabric sofa under 8000");

        ArgumentCaptor<AppProductSpuPageReqVO> captor = ArgumentCaptor.forClass(AppProductSpuPageReqVO.class);
        verify(productSpuService).getSpuPage(captor.capture());
        assertEquals("sofa", captor.getValue().getKeyword());
        assertEquals(1, captor.getValue().getPageNo());
        assertEquals(10, captor.getValue().getPageSize());
        assertEquals(2, result.getProducts().size());
        assertEquals(1001L, result.getProducts().get(0).getId());
        assertEquals(new BigDecimal("6999"), result.getProducts().get(0).getPrice());
        assertEquals(1003L, result.getProducts().get(1).getId());
    }

    @Test
    void searchForAssistant_shouldUnderstandChineseSofaAndBudgetIntent() {
        when(productSpuService.getSpuPage(any(AppProductSpuPageReqVO.class)))
                .thenReturn(new PageResult<>(Arrays.asList(
                        spu(1001L, "Cream Fabric Sofa", "Soft cream fabric sofa", 699900, 899900, 12),
                        spu(1002L, "Leather Lounge Sofa", "Premium leather", 1280000, 1580000, 4),
                        spu(1003L, "Cloud Modular Sofa", "Fits small rooms", 329900, 459900, 18)
                ), 3L));

        FurnitureProductSearchResult result = tool.searchForAssistant("8000以内的米白色布艺沙发");

        ArgumentCaptor<AppProductSpuPageReqVO> captor = ArgumentCaptor.forClass(AppProductSpuPageReqVO.class);
        verify(productSpuService).getSpuPage(captor.capture());
        assertEquals("sofa", captor.getValue().getKeyword());
        assertEquals(2, result.getProducts().size());
        assertEquals(1001L, result.getProducts().get(0).getId());
        assertEquals("根据你的需求匹配：8000以内的米白色布艺沙发。", result.getProducts().get(0).getReason());
        assertEquals(1003L, result.getProducts().get(1).getId());
    }

    @Test
    void searchForAssistant_shouldUnderstandChineseLeadingBudgetIntent() {
        when(productSpuService.getSpuPage(any(AppProductSpuPageReqVO.class)))
                .thenReturn(new PageResult<>(Arrays.asList(
                        spu(1001L, "Cream Fabric Sofa", "Soft cream fabric sofa", 699900, 899900, 12),
                        spu(1002L, "Leather Lounge Sofa", "Premium leather", 1280000, 1580000, 4)
                ), 2L));

        FurnitureProductSearchResult result = tool.searchForAssistant("不超过8000元的布艺沙发");

        assertEquals(1, result.getProducts().size());
        assertEquals(1001L, result.getProducts().get(0).getId());
    }

    @Test
    void searchForAssistant_shouldUnderstandExpandedChineseFurnitureCategories() {
        when(productSpuService.getSpuPage(any(AppProductSpuPageReqVO.class)))
                .thenReturn(new PageResult<>(Arrays.asList(
                        spu(2001L, "Neutral Wool Rug", "Soft beige rug", 189900, 259900, 9)
                ), 1L));

        FurnitureProductSearchResult result = tool.searchForAssistant("\u627e\u4e00\u5f20\u7c73\u8272\u5730\u6bef");

        ArgumentCaptor<AppProductSpuPageReqVO> captor = ArgumentCaptor.forClass(AppProductSpuPageReqVO.class);
        verify(productSpuService).getSpuPage(captor.capture());
        assertEquals("rug", captor.getValue().getKeyword());
        assertEquals(1, result.getProducts().size());
        assertEquals(2001L, result.getProducts().get(0).getId());
    }

    @Test
    void searchForAssistant_shouldPreferSpecificChineseCategoryOverBroadAlias() {
        when(productSpuService.getSpuPage(any(AppProductSpuPageReqVO.class)))
                .thenReturn(new PageResult<>(Arrays.asList(
                        spu(2002L, "Wood Nightstand", "Bedside storage", 149900, 199900, 18)
                ), 1L));

        FurnitureProductSearchResult result = tool.searchForAssistant("\u627e\u4e00\u4e2a\u5e8a\u5934\u67dc");

        ArgumentCaptor<AppProductSpuPageReqVO> captor = ArgumentCaptor.forClass(AppProductSpuPageReqVO.class);
        verify(productSpuService).getSpuPage(captor.capture());
        assertEquals("nightstand", captor.getValue().getKeyword());
        assertEquals(1, result.getProducts().size());
    }

    @Test
    void searchForAssistant_shouldPreferSpecificLampCategoryOverBroadLightingAlias() {
        when(productSpuService.getSpuPage(any(AppProductSpuPageReqVO.class)))
                .thenReturn(new PageResult<>(Arrays.asList(
                        spu(2003L, "White Globe Table Lamp", "Bedside lighting", 89900, 129900, 26)
                ), 1L));

        FurnitureProductSearchResult result = tool.searchForAssistant("\u627e\u4e00\u76cf\u53f0\u706f");

        ArgumentCaptor<AppProductSpuPageReqVO> captor = ArgumentCaptor.forClass(AppProductSpuPageReqVO.class);
        verify(productSpuService).getSpuPage(captor.capture());
        assertEquals("table lamp", captor.getValue().getKeyword());
        assertEquals(1, result.getProducts().size());
    }

    @Test
    void searchProducts_shouldUseStructuredRequestLimitAndBudget() {
        when(productSpuService.getSpuPage(any(AppProductSpuPageReqVO.class)))
                .thenReturn(new PageResult<>(Arrays.asList(
                        spu(1001L, "Fabric Track Arm Sofa", "A soft cream sofa", 699900, 899900, 12),
                        spu(1002L, "Compact Apartment Sofa", "Fits small rooms", 799900, 999900, 8)
                ), 2L));
        FurnitureProductSearchRequest request = new FurnitureProductSearchRequest();
        request.setKeyword("sofa");
        request.setMaxPrice(new BigDecimal("7000"));
        request.setLimit(1);

        FurnitureProductSearchResult result = tool.searchProducts(request);

        assertEquals(1, result.getProducts().size());
        assertEquals("Fabric Track Arm Sofa", result.getProducts().get(0).getName());
    }

    private static ProductSpuDO spu(Long id, String name, String introduction, Integer price,
                                    Integer marketPrice, Integer stock) {
        return ProductSpuDO.builder()
                .id(id)
                .name(name)
                .introduction(introduction)
                .picUrl("/images/" + id + ".jpg")
                .price(price)
                .marketPrice(marketPrice)
                .stock(stock)
                .build();
    }

}
