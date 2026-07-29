package cn.iocoder.yudao.module.erp.service.integration;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.erp.dal.dataobject.integration.MallErpProductMappingDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.yudao.module.erp.dal.mysql.integration.MallErpProductMappingMapper;
import cn.iocoder.yudao.module.erp.dal.mysql.product.ErpProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyCollection;
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

    private static MallErpProductMappingDO mapping(Long mallSkuId, Long erpProductId) {
        return new MallErpProductMappingDO().setMallSkuId(mallSkuId).setErpProductId(erpProductId);
    }
}
