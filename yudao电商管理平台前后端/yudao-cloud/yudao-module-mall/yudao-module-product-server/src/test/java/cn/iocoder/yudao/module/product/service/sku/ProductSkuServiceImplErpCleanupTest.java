package cn.iocoder.yudao.module.product.service.sku;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.erp.api.integration.MallErpProductApi;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSkuSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.yudao.module.product.dal.mysql.sku.ProductSkuMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductSkuServiceImplErpCleanupTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ProductSkuServiceImpl service;

    @Mock
    private ProductSkuMapper productSkuMapper;
    @Mock
    private MallErpProductApi mallErpProductApi;

    @Test
    void deleteSkuUnlinksErpMappingBeforeDeletingSku() {
        ProductSkuDO sku = new ProductSkuDO().setId(101L).setSpuId(91L);
        when(productSkuMapper.selectById(101L)).thenReturn(sku);
        when(mallErpProductApi.unlinkMallSkus(Collections.singleton(101L)))
                .thenReturn(CommonResult.success(true));

        service.deleteSku(101L);

        InOrder ordered = inOrder(mallErpProductApi, productSkuMapper);
        ordered.verify(mallErpProductApi).unlinkMallSkus(Collections.singleton(101L));
        ordered.verify(productSkuMapper).deleteById(101L);
    }

    @Test
    void deleteSkuBySpuIdUnlinksEverySkuBeforeDeletingThem() {
        List<ProductSkuDO> skus = List.of(
                new ProductSkuDO().setId(101L).setSpuId(91L),
                new ProductSkuDO().setId(102L).setSpuId(91L));
        when(productSkuMapper.selectListBySpuId(91L)).thenReturn(skus);
        when(mallErpProductApi.unlinkMallSkus(anyCollection()))
                .thenReturn(CommonResult.success(true));

        service.deleteSkuBySpuId(91L);

        verify(mallErpProductApi).unlinkMallSkus(eq(java.util.Set.of(101L, 102L)));
        verify(productSkuMapper).deleteBySpuId(91L);
    }

    @Test
    void updateSkuListUnlinksRemovedSkuBeforeDeletingIt() {
        ProductSkuDO existing = new ProductSkuDO().setId(101L).setSpuId(91L)
                .setProperties(Collections.emptyList());
        when(productSkuMapper.selectListBySpuId(91L)).thenReturn(Collections.singletonList(existing));
        when(mallErpProductApi.unlinkMallSkus(anyCollection()))
                .thenReturn(CommonResult.success(true));

        service.updateSkuList(91L, Collections.<ProductSkuSaveReqVO>emptyList());

        verify(mallErpProductApi).unlinkMallSkus(anyCollection());
        verify(productSkuMapper).deleteByIds(anyCollection());
    }
}
