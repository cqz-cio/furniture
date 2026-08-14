package cn.iocoder.yudao.module.erp.service.integration;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.erp.api.integration.dto.MallErpProductDTO;
import cn.iocoder.yudao.module.erp.api.integration.dto.MallErpSyncSummaryDTO;
import cn.iocoder.yudao.module.erp.dal.dataobject.integration.MallErpProductMappingDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.integration.MallErpSyncLogDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.yudao.module.erp.dal.mysql.integration.MallErpProductMappingMapper;
import cn.iocoder.yudao.module.erp.dal.mysql.integration.MallErpSyncLogMapper;
import cn.iocoder.yudao.module.erp.dal.mysql.product.ErpProductMapper;
import cn.iocoder.yudao.module.erp.dal.mysql.stock.ErpStockMapper;
import cn.iocoder.yudao.module.product.api.sku.ProductSkuApi;
import cn.iocoder.yudao.module.product.api.sku.dto.ProductSkuRespDTO;
import cn.iocoder.yudao.module.product.api.spu.ProductSpuApi;
import cn.iocoder.yudao.module.product.api.spu.dto.ProductSpuRespDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private ErpStockMapper stockMapper;
    @Mock
    private MallErpProductCodeGenerator productCodeGenerator;
    @Mock
    private MallErpSyncLogMapper syncLogMapper;
    @Mock
    private ProductSkuApi productSkuApi;
    @Mock
    private ProductSpuApi productSpuApi;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContextHolder.setTenantId(162L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void getMappedMallSkuIdsReturnsOnlySuccessfulMappingsWithEnabledErpProducts() {
        MallErpProductMappingDO enabledMapping = mapping(101L, 201L).setSyncStatus("SUCCESS");
        MallErpProductMappingDO failedMapping = mapping(102L, 202L).setSyncStatus("FAILED");
        MallErpProductMappingDO disabledMapping = mapping(103L, 203L).setSyncStatus("SUCCESS");
        when(mappingMapper.selectListByMallSkuIds(Arrays.asList(101L, 102L, 103L, 104L)))
                .thenReturn(Arrays.asList(enabledMapping, failedMapping, disabledMapping));
        when(productMapper.selectBatchIds(anyCollection())).thenReturn(Arrays.asList(
                ErpProductDO.builder().id(201L).status(CommonStatusEnum.ENABLE.getStatus()).build(),
                ErpProductDO.builder().id(202L).status(CommonStatusEnum.ENABLE.getStatus()).build(),
                ErpProductDO.builder().id(203L).status(CommonStatusEnum.DISABLE.getStatus()).build()));

        Set<Long> result = service.getMappedMallSkuIds(Arrays.asList(101L, 102L, 103L, 104L));

        assertEquals(Collections.singleton(101L), result);
    }

    @Test
    void getByMallSkuIdReturnsNullWhenSkuIsNotMapped() {
        when(mappingMapper.selectByMallSkuId(999L)).thenReturn(null);

        assertNull(service.getByMallSkuId(999L));
    }

    @Test
    void syncMallSkuCreatesOnlyWebMappingForExistingErpProduct() {
        ProductSkuRespDTO sku = sku(101L, 91L);
        ProductSpuRespDTO spu = new ProductSpuRespDTO().setId(91L).setName("Web title");
        ErpProductDO erpProduct = erpProduct(201L, "VANZ-162-101", "ERP base name");
        when(productSkuApi.getSku(101L)).thenReturn(CommonResult.success(sku));
        when(productSpuApi.getSpu(91L)).thenReturn(CommonResult.success(spu));
        when(productCodeGenerator.generate(162L, 101L)).thenReturn("VANZ-162-101");
        when(productMapper.selectByBarCode("VANZ-162-101")).thenReturn(erpProduct);
        when(stockMapper.selectSumByProductId(201L)).thenReturn(new BigDecimal("8"));

        MallErpProductDTO result = service.syncMallSku(91L, 101L);

        assertEquals("SUCCESS", result.getSyncStatus());
        assertEquals("VANZ-162-101", result.getErpProductCode());
        assertEquals("ERP base name", result.getBaseName());
        assertEquals(new BigDecimal("8"), result.getSellableStock());
        ArgumentCaptor<MallErpProductMappingDO> mappingCaptor =
                ArgumentCaptor.forClass(MallErpProductMappingDO.class);
        verify(mappingMapper).insert(mappingCaptor.capture());
        assertEquals(201L, mappingCaptor.getValue().getErpProductId());
        assertEquals("SUCCESS", mappingCaptor.getValue().getSyncStatus());
        verify(productMapper, never()).insert(any(ErpProductDO.class));
        verify(productMapper, never()).updateById(any(ErpProductDO.class));
        ArgumentCaptor<MallErpSyncLogDO> logCaptor = ArgumentCaptor.forClass(MallErpSyncLogDO.class);
        verify(syncLogMapper).insert(logCaptor.capture());
        assertEquals("ERP_TO_MALL", logCaptor.getValue().getDirection());
        assertEquals("MAP", logCaptor.getValue().getEventType());
    }

    @Test
    void syncMallSkuRefreshesMappingWithoutChangingErpProduct() {
        ProductSkuRespDTO sku = sku(101L, 91L);
        ProductSpuRespDTO spu = new ProductSpuRespDTO().setId(91L).setName("Changed web title");
        MallErpProductMappingDO existing = mapping(101L, 201L)
                .setMallSpuId(91L).setErpProductCode("OLD-CODE").setSyncStatus("SUCCESS");
        ErpProductDO erpProduct = erpProduct(201L, "ERP-CODE", "Authoritative ERP name");
        when(productSkuApi.getSku(101L)).thenReturn(CommonResult.success(sku));
        when(productSpuApi.getSpu(91L)).thenReturn(CommonResult.success(spu));
        when(mappingMapper.selectByMallSkuId(101L)).thenReturn(existing);
        when(productMapper.selectById(201L)).thenReturn(erpProduct);
        when(stockMapper.selectSumByProductId(201L)).thenReturn(new BigDecimal("3"));

        MallErpProductDTO result = service.syncMallSku(91L, 101L);

        assertEquals("SUCCESS", result.getSyncStatus());
        assertEquals("ERP-CODE", result.getErpProductCode());
        assertEquals("Authoritative ERP name", result.getBaseName());
        verify(mappingMapper).updateById(existing);
        verify(mappingMapper, never()).insert(any(MallErpProductMappingDO.class));
        verify(productMapper, never()).insert(any(ErpProductDO.class));
        verify(productMapper, never()).updateById(any(ErpProductDO.class));
    }

    @Test
    void syncMallSkuKeepsWebSkuUnmappedWhenErpProductDoesNotExist() {
        when(productSkuApi.getSku(101L)).thenReturn(CommonResult.success(sku(101L, 91L)));
        when(productSpuApi.getSpu(91L)).thenReturn(
                CommonResult.success(new ProductSpuRespDTO().setId(91L)));
        when(productCodeGenerator.generate(162L, 101L)).thenReturn("VANZ-162-101");
        when(productMapper.selectByBarCode("VANZ-162-101")).thenReturn(null);

        MallErpProductDTO result = service.syncMallSku(91L, 101L);

        assertEquals("UNMAPPED", result.getSyncStatus());
        assertTrue(result.getLastError().contains("VANZ-162-101"));
        verify(mappingMapper, never()).insert(any(MallErpProductMappingDO.class));
        verify(productMapper, never()).insert(any(ErpProductDO.class));
        verify(productMapper, never()).updateById(any(ErpProductDO.class));
    }

    @Test
    void syncMallSkuReportsConflictWhenErpProductIsAlreadyMappedElsewhere() {
        when(productSkuApi.getSku(101L)).thenReturn(CommonResult.success(sku(101L, 91L)));
        when(productSpuApi.getSpu(91L)).thenReturn(
                CommonResult.success(new ProductSpuRespDTO().setId(91L)));
        when(productCodeGenerator.generate(162L, 101L)).thenReturn("VANZ-162-101");
        ErpProductDO erpProduct = erpProduct(201L, "VANZ-162-101", "Existing ERP product");
        when(productMapper.selectByBarCode("VANZ-162-101")).thenReturn(erpProduct);
        when(mappingMapper.selectByErpProductId(201L)).thenReturn(mapping(999L, 201L));

        MallErpProductDTO result = service.syncMallSku(91L, 101L);

        assertEquals("FAILED", result.getSyncStatus());
        assertTrue(result.getLastError().contains("999"));
        verify(mappingMapper, never()).insert(any(MallErpProductMappingDO.class));
        verify(productMapper, never()).insert(any(ErpProductDO.class));
        verify(productMapper, never()).updateById(any(ErpProductDO.class));
    }

    @Test
    void syncAllScansEveryRequestedWebSkuAndReturnsAccurateSummary() {
        ProductSkuRespDTO firstSku = sku(101L, 91L);
        ProductSkuRespDTO secondSku = sku(102L, 92L);
        when(productSkuApi.getSkuList(anyCollection()))
                .thenReturn(CommonResult.success(Arrays.asList(firstSku, secondSku)));
        when(productSpuApi.getSpuList(anyCollection())).thenReturn(CommonResult.success(Arrays.asList(
                new ProductSpuRespDTO().setId(91L), new ProductSpuRespDTO().setId(92L))));
        when(productCodeGenerator.generate(any(Long.class), any(Long.class))).thenAnswer(invocation ->
                "VANZ-162-" + invocation.getArgument(1));
        when(productMapper.selectByBarCode(anyString())).thenAnswer(invocation -> {
            String code = invocation.getArgument(0);
            return code.endsWith("101") ? erpProduct(201L, code, "ERP product") : null;
        });
        when(stockMapper.selectSumByProductId(201L)).thenReturn(BigDecimal.ZERO);

        MallErpSyncSummaryDTO result = service.syncAll(Arrays.asList(101L, 102L));

        assertEquals(2, result.getTotalSkus());
        assertEquals(1, result.getMappedSkus());
        assertEquals(1, result.getNewMappings());
        assertEquals(0, result.getRefreshedMappings());
        assertEquals(1, result.getUnmappedSkus());
        assertEquals(0, result.getFailedSkus());
        assertEquals(2, result.getItems().size());
        verify(mappingMapper).insert(any(MallErpProductMappingDO.class));
        verify(productMapper, never()).insert(any(ErpProductDO.class));
        verify(productMapper, never()).updateById(any(ErpProductDO.class));
    }

    @Test
    void getByMallSkuIdReportsFailedMappingWhenErpProductWasRemoved() {
        MallErpProductMappingDO existing = mapping(101L, 201L)
                .setMallSpuId(91L).setSyncStatus("SUCCESS").setLastError("ERP product missing");
        when(mappingMapper.selectByMallSkuId(101L)).thenReturn(existing);
        when(productMapper.selectById(201L)).thenReturn(null);

        MallErpProductDTO result = service.getByMallSkuId(101L);

        assertEquals("FAILED", result.getSyncStatus());
        assertFalse(result.getEnabled());
    }

    @Test
    void unlinkMallSkusStillDisablesErpProductForExplicitWebDeletion() {
        MallErpProductMappingDO first = mapping(101L, 201L).setErpProductCode("VANZ-162-101");
        MallErpProductMappingDO second = mapping(102L, 202L).setErpProductCode("VANZ-162-102");
        when(mappingMapper.selectListByMallSkuIds(Arrays.asList(101L, 102L)))
                .thenReturn(Arrays.asList(first, second));
        when(productMapper.selectBatchIds(anyCollection())).thenReturn(Arrays.asList(
                ErpProductDO.builder().id(201L).status(CommonStatusEnum.ENABLE.getStatus()).build(),
                ErpProductDO.builder().id(202L).status(CommonStatusEnum.DISABLE.getStatus()).build()));

        service.unlinkMallSkus(Arrays.asList(101L, 102L));

        verify(productMapper).updateById(any(ErpProductDO.class));
        verify(mappingMapper, times(2)).updateById(any(MallErpProductMappingDO.class));
        verify(mappingMapper).deleteByMallSkuIds(Set.of(101L, 102L));
        verify(syncLogMapper, times(2)).insert(any(MallErpSyncLogDO.class));
    }

    private static ProductSkuRespDTO sku(Long skuId, Long spuId) {
        return new ProductSkuRespDTO().setId(skuId).setSpuId(spuId).setBarCode("");
    }

    private static ErpProductDO erpProduct(Long id, String code, String name) {
        return ErpProductDO.builder().id(id).barCode(code).name(name)
                .status(CommonStatusEnum.ENABLE.getStatus())
                .purchasePrice(new BigDecimal("12.00")).build();
    }

    private static MallErpProductMappingDO mapping(Long mallSkuId, Long erpProductId) {
        return new MallErpProductMappingDO().setMallSkuId(mallSkuId).setErpProductId(erpProductId);
    }

}
