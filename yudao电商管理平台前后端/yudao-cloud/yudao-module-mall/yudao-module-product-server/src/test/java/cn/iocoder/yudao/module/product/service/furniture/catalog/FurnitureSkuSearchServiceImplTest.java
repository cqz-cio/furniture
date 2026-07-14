package cn.iocoder.yudao.module.product.service.furniture.catalog;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.product.dal.dataobject.furniture.FurnitureSkuSearchDO;
import cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.yudao.module.product.dal.mysql.furniture.FurnitureSkuSearchMapper;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FurnitureSkuSearchServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private FurnitureSkuSearchServiceImpl service;

    @Mock
    private FurnitureSkuSearchMapper mapper;
    @Mock
    private ProductSkuService productSkuService;

    @Test
    void upsert_shouldValidateSkuRelationshipAndDimensions() {
        ProductSkuDO sku = ProductSkuDO.builder().id(2001L).spuId(1001L).build();
        when(productSkuService.getSku(2001L)).thenReturn(sku);
        FurnitureSkuSearchDO value = validProjection();

        service.upsert(value);

        verify(mapper).insert(value);
    }

    @Test
    void upsert_shouldRejectMissingSku() {
        FurnitureSkuSearchDO value = validProjection();

        assertThrows(IllegalArgumentException.class, () -> service.upsert(value));
        verifyNoInteractions(mapper);
    }

    @Test
    void upsert_shouldRejectMismatchedSpu() {
        when(productSkuService.getSku(2001L))
                .thenReturn(ProductSkuDO.builder().id(2001L).spuId(9999L).build());
        FurnitureSkuSearchDO value = validProjection();

        assertThrows(IllegalArgumentException.class, () -> service.upsert(value));
        verifyNoInteractions(mapper);
    }

    @Test
    void upsert_shouldRejectNonPositiveDimensionsAndSeatCount() {
        when(productSkuService.getSku(2001L))
                .thenReturn(ProductSkuDO.builder().id(2001L).spuId(1001L).build());
        FurnitureSkuSearchDO value = validProjection();
        value.setDepthMm(0);

        assertThrows(IllegalArgumentException.class, () -> service.upsert(value));
        verifyNoInteractions(mapper);
    }

    @Test
    void upsert_shouldRejectUnknownControlledCode() {
        when(productSkuService.getSku(2001L))
                .thenReturn(ProductSkuDO.builder().id(2001L).spuId(1001L).build());
        FurnitureSkuSearchDO value = validProjection();
        value.setCategoryCode("unknown");

        assertThrows(IllegalArgumentException.class, () -> service.upsert(value));
        verifyNoInteractions(mapper);
    }

    private static FurnitureSkuSearchDO validProjection() {
        return FurnitureSkuSearchDO.builder()
                .skuId(2001L).spuId(1001L).categoryCode("sofa")
                .materialCodes(Arrays.asList("fabric"))
                .styleCodes(Arrays.asList("modern"))
                .roomTypeCodes(Arrays.asList("living-room"))
                .featureCodes(Arrays.asList("shallow-depth"))
                .seatCount(3).widthMm(2180).depthMm(880).heightMm(820)
                .petFriendly(true).easyClean(true).build();
    }

}
