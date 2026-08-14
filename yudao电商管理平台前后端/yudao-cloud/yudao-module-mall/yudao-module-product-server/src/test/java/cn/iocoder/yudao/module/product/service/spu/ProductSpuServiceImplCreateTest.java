package cn.iocoder.yudao.module.product.service.spu;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSkuSaveReqVO;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.yudao.module.product.service.brand.ProductBrandService;
import cn.iocoder.yudao.module.product.service.category.ProductCategoryService;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import cn.iocoder.yudao.module.product.service.spu.event.ProductSpuCreatedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductSpuServiceImplCreateTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ProductSpuServiceImpl service;
    @Mock
    private ProductSpuMapper productSpuMapper;
    @Mock
    private ProductSkuService productSkuService;
    @Mock
    private ProductBrandService brandService;
    @Mock
    private ProductCategoryService categoryService;
    @Mock
    private ProductAdminFieldPolicyService productAdminFieldPolicyService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    void publishesInitializationEventAfterSpuAndSkuCreation() {
        ProductSkuSaveReqVO sku = new ProductSkuSaveReqVO()
                .setName("Default").setPrice(19999).setMarketPrice(22999).setCostPrice(12345)
                .setPicUrl("https://example.test/table.png").setStock(0);
        ProductSpuSaveReqVO request = new ProductSpuSaveReqVO()
                .setName("Rustic Dining Table").setCategoryId(301L).setBrandId(1L)
                .setSpecType(false).setSkus(Collections.singletonList(sku));
        when(categoryService.getCategoryLevel(301L)).thenReturn(ProductCategoryDO.CATEGORY_LEVEL);
        doAnswer(invocation -> {
            ((ProductSpuDO) invocation.getArgument(0)).setId(91L);
            return 1;
        }).when(productSpuMapper).insert(any(ProductSpuDO.class));

        Long result = service.createSpu(request);

        assertEquals(91L, result);
        InOrder ordered = inOrder(productSpuMapper, productSkuService, eventPublisher);
        ordered.verify(productSpuMapper).insert(any(ProductSpuDO.class));
        ordered.verify(productSkuService).createSkuList(91L, request.getSkus());
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        ordered.verify(eventPublisher).publishEvent(eventCaptor.capture());
        ProductSpuCreatedEvent event = (ProductSpuCreatedEvent) eventCaptor.getValue();
        assertEquals(91L, event.spuId());
        verify(productSkuService).validateSkuList(request.getSkus(), false);
    }

}
