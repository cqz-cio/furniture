package cn.iocoder.yudao.module.erp.service.integration;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.erp.dal.dataobject.integration.MallErpProductMappingDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.product.ErpProductCategoryDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.yudao.module.erp.dal.mysql.integration.MallErpProductMappingMapper;
import cn.iocoder.yudao.module.erp.dal.mysql.product.ErpProductCategoryMapper;
import cn.iocoder.yudao.module.erp.dal.mysql.product.ErpProductMapper;
import cn.iocoder.yudao.module.product.api.spu.dto.ProductSpuRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MallErpProductSyncServiceImplTest {

    @InjectMocks
    private MallErpProductSyncServiceImpl service;
    @Mock
    private MallErpProductMappingMapper mappingMapper;
    @Mock
    private ErpProductMapper productMapper;
    @Mock
    private MallErpProductCodeGenerator productCodeGenerator;
    @Mock
    private ErpProductCategoryMapper categoryMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getMappedMallSkuIdsReturnsOnlyMappingsWithEnabledErpProducts() {
        MallErpProductMappingDO enabledMapping = mapping(101L, 201L);
        MallErpProductMappingDO missingProductMapping = mapping(102L, 202L);
        MallErpProductMappingDO disabledMapping = mapping(103L, 203L);
        when(mappingMapper.selectListByMallSkuIds(Arrays.asList(101L, 102L, 103L, 104L)))
                .thenReturn(Arrays.asList(enabledMapping, missingProductMapping, disabledMapping));
        when(productMapper.selectBatchIds(anyCollection())).thenReturn(Arrays.asList(
                ErpProductDO.builder().id(201L).status(CommonStatusEnum.ENABLE.getStatus()).build(),
                ErpProductDO.builder().id(203L).status(CommonStatusEnum.DISABLE.getStatus()).build()));

        Set<Long> result = service.getMappedMallSkuIds(Arrays.asList(101L, 102L, 103L, 104L));

        assertEquals(Collections.singleton(101L), result);
    }

    @Test
    void getMappedMallSkuIdsReturnsEmptyForEmptyInput() {
        assertEquals(new HashSet<>(), service.getMappedMallSkuIds(Collections.emptyList()));
    }

    @Test
    void getByMallSkuIdReturnsNullWhenSkuIsNotMapped() {
        when(mappingMapper.selectByMallSkuId(999L)).thenReturn(null);

        assertNull(service.getByMallSkuId(999L));
    }

    @Test
    void resolveErpCategoryReusesStableMallCategoryCodeWhenNameChanges() {
        ErpProductCategoryDO root = ErpProductCategoryDO.builder()
                .id(6L).parentId(0L).name("Furniture").code("FURNITURE").build();
        ErpProductCategoryDO existingCategory = ErpProductCategoryDO.builder()
                .id(7L).parentId(6L).name("Dining Room").code("MALL_CATEGORY_25").build();
        when(categoryMapper.selectList()).thenReturn(Arrays.asList(root, existingCategory));
        ProductSpuRespDTO spu = new ProductSpuRespDTO();
        spu.setCategoryId(25L);
        spu.setCategoryName("Dining Room Furniture");

        ErpProductCategoryDO result = ReflectionTestUtils.invokeMethod(service, "resolveErpCategory", spu);

        assertSame(existingCategory, result);
        verify(categoryMapper, never()).insert(any(ErpProductCategoryDO.class));
    }

    private static MallErpProductMappingDO mapping(Long mallSkuId, Long erpProductId) {
        return new MallErpProductMappingDO().setMallSkuId(mallSkuId).setErpProductId(erpProductId);
    }
}
