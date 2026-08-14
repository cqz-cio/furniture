package cn.iocoder.yudao.module.product.service.brand;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.product.dal.dataobject.brand.ProductBrandDO;
import cn.iocoder.yudao.module.product.dal.mysql.brand.ProductBrandMapper;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.BRAND_HAVE_BIND_SPU;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductBrandServiceImplDeleteTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ProductBrandServiceImpl service;
    @Mock
    private ProductBrandMapper brandMapper;
    @Mock
    private ProductSpuService productSpuService;

    @Test
    void deleteBrandRejectsBrandUsedByProducts() {
        when(brandMapper.selectById(10L)).thenReturn(new ProductBrandDO().setId(10L));
        when(productSpuService.getSpuCountByBrandId(10L)).thenReturn(2L);

        assertServiceException(() -> service.deleteBrand(10L), BRAND_HAVE_BIND_SPU);

        verify(brandMapper, never()).deleteById(10L);
    }

}
