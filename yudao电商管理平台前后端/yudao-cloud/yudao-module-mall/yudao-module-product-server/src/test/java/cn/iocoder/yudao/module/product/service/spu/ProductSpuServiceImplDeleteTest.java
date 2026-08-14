package cn.iocoder.yudao.module.product.service.spu;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.member.api.giftregistry.MemberGiftRegistryApi;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.yudao.module.product.enums.spu.ProductSpuStatusEnum;
import cn.iocoder.yudao.module.product.service.favorite.ProductFavoriteService;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import cn.iocoder.yudao.module.promotion.api.product.PromotionProductApi;
import cn.iocoder.yudao.module.trade.api.cart.CartApi;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductSpuServiceImplDeleteTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ProductSpuServiceImpl service;

    @Mock
    private ProductSpuMapper productSpuMapper;
    @Mock
    private ProductSkuService productSkuService;
    @Mock
    private ProductFavoriteService productFavoriteService;
    @Mock
    private PromotionProductApi promotionProductApi;
    @Mock
    private CartApi cartApi;
    @Mock
    private MemberGiftRegistryApi memberGiftRegistryApi;

    @Test
    void deleteSpuCleansCurrentDataBeforeDeletingMasterRecord() {
        when(productSpuMapper.selectById(91L)).thenReturn(new ProductSpuDO()
                .setId(91L).setStatus(ProductSpuStatusEnum.RECYCLE.getStatus()));
        when(promotionProductApi.validateSpuDeletable(91L)).thenReturn(CommonResult.success(true));
        when(cartApi.deleteCartBySpuId(91L)).thenReturn(CommonResult.success(true));
        when(memberGiftRegistryApi.deleteItemsBySpuId(91L)).thenReturn(CommonResult.success(true));

        service.deleteSpu(91L);

        InOrder ordered = inOrder(productSpuMapper, promotionProductApi, cartApi, memberGiftRegistryApi,
                productFavoriteService, productSkuService);
        ordered.verify(productSpuMapper).selectById(91L);
        ordered.verify(promotionProductApi).validateSpuDeletable(91L);
        ordered.verify(cartApi).deleteCartBySpuId(91L);
        ordered.verify(memberGiftRegistryApi).deleteItemsBySpuId(91L);
        ordered.verify(productFavoriteService).deleteFavoriteBySpuId(91L);
        ordered.verify(productSkuService).deleteSkuBySpuId(91L);
        ordered.verify(productSpuMapper).deleteById(91L);
    }

    @Test
    void deleteSpuStopsWhenPromotionReferenceExists() {
        when(productSpuMapper.selectById(91L)).thenReturn(new ProductSpuDO()
                .setId(91L).setStatus(ProductSpuStatusEnum.RECYCLE.getStatus()));
        when(promotionProductApi.validateSpuDeletable(91L))
                .thenThrow(new IllegalStateException("active promotion"));

        assertThrows(IllegalStateException.class, () -> service.deleteSpu(91L));

        verify(cartApi, never()).deleteCartBySpuId(91L);
        verify(memberGiftRegistryApi, never()).deleteItemsBySpuId(91L);
        verify(productFavoriteService, never()).deleteFavoriteBySpuId(91L);
        verify(productSkuService, never()).deleteSkuBySpuId(91L);
        verify(productSpuMapper, never()).deleteById(91L);
    }

}
