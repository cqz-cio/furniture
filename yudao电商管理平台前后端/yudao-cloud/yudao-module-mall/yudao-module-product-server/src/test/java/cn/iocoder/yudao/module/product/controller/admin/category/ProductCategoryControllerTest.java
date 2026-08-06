package cn.iocoder.yudao.module.product.controller.admin.category;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.product.controller.admin.category.vo.ProductNavigationCategoryCreateReqVO;
import cn.iocoder.yudao.module.product.service.category.ProductCategoryService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductCategoryControllerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ProductCategoryController controller;

    @Mock
    private ProductCategoryService categoryService;

    @Test
    void createNavigationCategoryShouldUseDedicatedServiceMethod() {
        ProductNavigationCategoryCreateReqVO request = new ProductNavigationCategoryCreateReqVO()
                .setParentId(1L)
                .setName("  Contract Furniture  ");
        when(categoryService.createNavigationCategory(request)).thenReturn(88L);

        CommonResult<Long> result = controller.createNavigationCategory(request);

        verify(categoryService).createNavigationCategory(request);
        assertEquals(88L, result.getData());
    }

}
