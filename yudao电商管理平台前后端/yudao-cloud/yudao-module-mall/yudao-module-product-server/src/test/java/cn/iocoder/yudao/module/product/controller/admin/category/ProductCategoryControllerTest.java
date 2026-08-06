package cn.iocoder.yudao.module.product.controller.admin.category;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.product.controller.admin.category.vo.ProductCategorySaveReqVO;
import cn.iocoder.yudao.module.product.controller.admin.category.vo.ProductNavigationCategoryCreateReqVO;
import cn.iocoder.yudao.module.product.service.category.ProductCategoryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductCategoryControllerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ProductCategoryController controller;

    @Mock
    private ProductCategoryService categoryService;

    @Test
    void createNavigationCategoryShouldKeepGenericImageFieldWithoutRequiringUpload() {
        ProductNavigationCategoryCreateReqVO request = new ProductNavigationCategoryCreateReqVO()
                .setParentId(1L)
                .setName("  Contract Furniture  ");
        when(categoryService.createCategory(any())).thenReturn(88L);

        CommonResult<Long> result = controller.createNavigationCategory(request);

        ArgumentCaptor<ProductCategorySaveReqVO> captor =
                ArgumentCaptor.forClass(ProductCategorySaveReqVO.class);
        verify(categoryService).createCategory(captor.capture());
        ProductCategorySaveReqVO saved = captor.getValue();
        assertEquals(88L, result.getData());
        assertEquals(1L, saved.getParentId());
        assertEquals("Contract Furniture", saved.getName());
        assertEquals("", saved.getPicUrl());
        assertEquals(0, saved.getSort());
        assertEquals(CommonStatusEnum.ENABLE.getStatus(), saved.getStatus());
    }

}
