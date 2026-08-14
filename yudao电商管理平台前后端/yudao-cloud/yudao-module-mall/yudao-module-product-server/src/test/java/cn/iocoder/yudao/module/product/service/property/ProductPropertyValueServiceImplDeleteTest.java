package cn.iocoder.yudao.module.product.service.property;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.product.dal.dataobject.property.ProductPropertyValueDO;
import cn.iocoder.yudao.module.product.dal.mysql.property.ProductPropertyValueMapper;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.PROPERTY_VALUE_DELETE_FAIL_SKU_EXISTS;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductPropertyValueServiceImplDeleteTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ProductPropertyValueServiceImpl service;
    @Mock
    private ProductPropertyValueMapper propertyValueMapper;
    @Mock
    private ProductSkuService productSkuService;

    @Test
    void deletePropertyValueRejectsValueUsedBySku() {
        when(propertyValueMapper.selectById(20L)).thenReturn(new ProductPropertyValueDO().setId(20L));
        when(productSkuService.getSkuCountByPropertyValueId(20L)).thenReturn(1L);

        assertServiceException(() -> service.deletePropertyValue(20L), PROPERTY_VALUE_DELETE_FAIL_SKU_EXISTS);

        verify(propertyValueMapper, never()).deleteById(20L);
    }

}
