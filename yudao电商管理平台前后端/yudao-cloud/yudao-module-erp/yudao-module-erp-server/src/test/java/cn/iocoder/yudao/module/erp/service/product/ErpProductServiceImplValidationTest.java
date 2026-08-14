package cn.iocoder.yudao.module.erp.service.product;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.erp.controller.admin.product.vo.product.ProductSaveReqVO;
import cn.iocoder.yudao.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.yudao.module.erp.dal.mysql.product.ErpProductMapper;
import cn.iocoder.yudao.module.erp.service.common.ErpReferenceValidationService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ErpProductServiceImplValidationTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ErpProductServiceImpl service;
    @Mock
    private ErpProductMapper productMapper;
    @Mock
    private ErpProductCategoryService productCategoryService;
    @Mock
    private ErpProductUnitService productUnitService;
    @Mock
    private ErpReferenceValidationService referenceValidationService;

    @Test
    void createProductValidatesCategoryAndUnit() {
        ProductSaveReqVO reqVO = new ProductSaveReqVO();
        reqVO.setName("Chair");
        reqVO.setBarCode("VANZ-1");
        reqVO.setCategoryId(11L);
        reqVO.setUnitId(12L);

        service.createProduct(reqVO);

        verify(productCategoryService).validateProductCategory(11L);
        verify(productUnitService).validateProductUnit(12L);
        verify(productMapper).insert(any(ErpProductDO.class));
    }

    @Test
    void deleteProductStopsWhenReferenceValidationFails() {
        when(productMapper.selectById(21L)).thenReturn(new ErpProductDO().setId(21L));
        org.mockito.Mockito.doThrow(new IllegalStateException("referenced"))
                .when(referenceValidationService).validateProductDeletable(21L);

        assertThrows(IllegalStateException.class, () -> service.deleteProduct(21L));

        verify(productMapper, never()).deleteById(21L);
    }

}
