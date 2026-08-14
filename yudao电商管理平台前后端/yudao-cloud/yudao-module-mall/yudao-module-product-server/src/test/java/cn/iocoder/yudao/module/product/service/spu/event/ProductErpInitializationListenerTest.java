package cn.iocoder.yudao.module.product.service.spu.event;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.erp.api.integration.MallErpProductApi;
import cn.iocoder.yudao.module.erp.api.integration.dto.MallErpProductDTO;
import cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Arrays;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductErpInitializationListenerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ProductErpInitializationListener listener;
    @Mock
    private ProductSkuService productSkuService;
    @Mock
    private MallErpProductApi mallErpProductApi;

    @Test
    void initializesEveryCommittedSkuAndContinuesAfterOneFailure() {
        when(productSkuService.getSkuListBySpuId(91L)).thenReturn(Arrays.asList(
                new ProductSkuDO().setId(101L).setSpuId(91L),
                new ProductSkuDO().setId(102L).setSpuId(91L)));
        when(mallErpProductApi.initializeMallSku(91L, 101L))
                .thenThrow(new IllegalStateException("temporary ERP failure"));
        when(mallErpProductApi.initializeMallSku(91L, 102L)).thenReturn(CommonResult.success(
                new MallErpProductDTO().setMallSpuId(91L).setMallSkuId(102L).setSyncStatus("SUCCESS")));

        listener.initializeErpProducts(new ProductSpuCreatedEvent(91L));

        verify(mallErpProductApi).initializeMallSku(91L, 101L);
        verify(mallErpProductApi).initializeMallSku(91L, 102L);
    }

}
