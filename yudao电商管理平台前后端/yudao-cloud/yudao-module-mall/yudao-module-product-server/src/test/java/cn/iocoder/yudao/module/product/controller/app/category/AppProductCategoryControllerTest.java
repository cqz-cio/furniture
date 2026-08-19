package cn.iocoder.yudao.module.product.controller.app.category;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.product.controller.app.category.vo.AppProductCategoryRespVO;
import cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO;
import cn.iocoder.yudao.module.product.service.category.ProductCategoryService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static cn.iocoder.yudao.framework.common.enums.CommonStatusEnum.ENABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class AppProductCategoryControllerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private AppProductCategoryController controller;
    @Mock
    private ProductCategoryService categoryService;

    @Test
    void shouldReturnStableCodeCategoryTree() {
        when(categoryService.getEnableCategoryList()).thenReturn(List.of(
                category(10L, 0L, "dining-room", "Dining Room Furniture", 10),
                category(101L, 10L, "dining-chair", "DINING CHAIRS", 10),
                category(102L, 10L, "bar-stool", "BAR STOOLS", 20)));

        List<AppProductCategoryRespVO> result = controller.getCategoryTree().getData();

        assertEquals(1, result.size());
        assertEquals("dining-room", result.get(0).getCode());
        assertEquals(List.of("dining-chair", "bar-stool"),
                result.get(0).getChildren().stream().map(AppProductCategoryRespVO::getCode).toList());
        assertEquals(10L, result.get(0).getChildren().get(0).getParentId());
    }

    private static ProductCategoryDO category(
            Long id, Long parentId, String code, String name, Integer sort) {
        return new ProductCategoryDO().setId(id).setParentId(parentId).setCode(code)
                .setName(name).setSort(sort).setStatus(ENABLE.getStatus());
    }

}
