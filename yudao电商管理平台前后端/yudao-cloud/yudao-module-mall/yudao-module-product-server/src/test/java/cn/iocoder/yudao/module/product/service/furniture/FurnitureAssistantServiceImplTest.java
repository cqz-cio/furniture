package cn.iocoder.yudao.module.product.service.furniture;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.product.controller.app.furniture.vo.FurnitureAssistantChatReqVO;
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
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FurnitureAssistantServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private FurnitureAssistantServiceImpl service;

    @Mock
    private ProductSpuService productSpuService;

    @Mock
    private FurnitureAssistantKnowledgeService knowledgeService;

    @Test
    void chat_shouldSearchProductSpusAndFilterByBudget() {
        when(knowledgeService.search(anyString())).thenReturn(Collections.emptyList());
        when(productSpuService.getSpuPage(any(AppProductSpuPageReqVO.class)))
                .thenReturn(new PageResult<>(Arrays.asList(
                        spu(1001L, "Fabric Track Arm Sofa", "A soft cream sofa", 699900, 899900, 12),
                        spu(1002L, "Oversized Leather Sofa", "Premium leather", 1280000, 1580000, 4),
                        spu(1003L, "Compact Apartment Sofa", "Fits small rooms", 799900, 999900, 8)
                ), 3L));

        FurnitureAssistantChatReqVO reqVO = new FurnitureAssistantChatReqVO();
        reqVO.setMessage("cream fabric sofa under 8000");

        FurnitureAssistantChatRespVO response = service.chat(reqVO);

        ArgumentCaptor<AppProductSpuPageReqVO> captor = ArgumentCaptor.forClass(AppProductSpuPageReqVO.class);
        verify(productSpuService).getSpuPage(captor.capture());
        assertEquals("sofa", captor.getValue().getKeyword());
        assertEquals(1, captor.getValue().getPageNo());
        assertEquals(10, captor.getValue().getPageSize());

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

        verify(productSpuService, never()).getSpuPage(any(AppProductSpuPageReqVO.class));
        assertTrue(response.getAnswer().contains("Membership prices can be used"));
        assertEquals(0, response.getProducts().size());
        assertEquals(1, response.getSources().size());
        assertEquals("knowledge", response.getSources().get(0).getType());
        assertEquals("Membership Rules", response.getSources().get(0).getName());
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
