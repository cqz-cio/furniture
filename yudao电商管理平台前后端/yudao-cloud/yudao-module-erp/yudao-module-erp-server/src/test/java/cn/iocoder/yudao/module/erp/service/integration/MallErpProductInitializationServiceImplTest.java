package cn.iocoder.yudao.module.erp.service.integration;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.erp.api.integration.dto.MallErpProductDTO;
import cn.iocoder.yudao.module.erp.dal.dataobject.integration.MallErpProductMappingDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.integration.MallErpSyncLogDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.product.ErpProductCategoryDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.product.ErpProductUnitDO;
import cn.iocoder.yudao.module.erp.dal.mysql.integration.MallErpProductMappingMapper;
import cn.iocoder.yudao.module.erp.dal.mysql.integration.MallErpSyncLogMapper;
import cn.iocoder.yudao.module.erp.dal.mysql.product.ErpProductCategoryMapper;
import cn.iocoder.yudao.module.erp.dal.mysql.product.ErpProductMapper;
import cn.iocoder.yudao.module.erp.dal.mysql.product.ErpProductUnitMapper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MallErpProductInitializationServiceImplTest {

    @InjectMocks
    private MallErpProductInitializationServiceImpl service;
    @Mock
    private ProductSkuApi productSkuApi;
    @Mock
    private ProductSpuApi productSpuApi;
    @Mock
    private MallErpProductCodeGenerator productCodeGenerator;
    @Mock
    private ErpProductMapper productMapper;
    @Mock
    private ErpProductCategoryMapper categoryMapper;
    @Mock
    private ErpProductUnitMapper unitMapper;
    @Mock
    private ErpStockMapper stockMapper;
    @Mock
    private MallErpProductMappingMapper mappingMapper;
    @Mock
    private MallErpSyncLogMapper syncLogMapper;

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
    void initializeCreatesErpProductAndMappingOnce() {
        stubWebProduct();
        when(productCodeGenerator.generate(162L, 101L)).thenReturn("VANZ-162-101");
        when(categoryMapper.selectByCode("MALL_CATEGORY_301")).thenReturn(enabledCategory(401L));
        when(unitMapper.selectByName("Piece")).thenReturn(enabledUnit(501L));
        doAnswer(invocation -> {
            ((ErpProductDO) invocation.getArgument(0)).setId(201L);
            return 1;
        }).when(productMapper).insert(any(ErpProductDO.class));
        when(stockMapper.selectSumByProductId(201L)).thenReturn(null);

        MallErpProductDTO result = service.initializeMallSku(91L, 101L);

        assertEquals("SUCCESS", result.getSyncStatus());
        assertEquals(201L, result.getErpProductId());
        assertEquals("VANZ-162-101", result.getErpProductCode());
        assertEquals(BigDecimal.ZERO, result.getSellableStock());

        ArgumentCaptor<ErpProductDO> productCaptor = ArgumentCaptor.forClass(ErpProductDO.class);
        verify(productMapper).insert(productCaptor.capture());
        ErpProductDO product = productCaptor.getValue();
        assertEquals("Rustic Dining Table", product.getName());
        assertEquals(401L, product.getCategoryId());
        assertEquals(501L, product.getUnitId());
        assertEquals(new BigDecimal("123.45"), product.getPurchasePrice());
        assertEquals(new BigDecimal("199.99"), product.getSalePrice());
        assertEquals(new BigDecimal("12.500"), product.getWeight());
        assertEquals("mall-erp-initializer", product.getCreator());
        assertEquals("mall-erp-initializer", product.getUpdater());

        ArgumentCaptor<MallErpProductMappingDO> mappingCaptor =
                ArgumentCaptor.forClass(MallErpProductMappingDO.class);
        verify(mappingMapper).insert(mappingCaptor.capture());
        assertEquals(91L, mappingCaptor.getValue().getMallSpuId());
        assertEquals(101L, mappingCaptor.getValue().getMallSkuId());
        assertEquals(201L, mappingCaptor.getValue().getErpProductId());
        assertEquals("mall-erp-initializer", mappingCaptor.getValue().getCreator());
        assertEquals("mall-erp-initializer", mappingCaptor.getValue().getUpdater());

        ArgumentCaptor<MallErpSyncLogDO> logCaptor = ArgumentCaptor.forClass(MallErpSyncLogDO.class);
        verify(syncLogMapper).insert(logCaptor.capture());
        assertEquals("MALL_TO_ERP", logCaptor.getValue().getDirection());
        assertEquals("INITIALIZE", logCaptor.getValue().getEventType());
        assertTrue(logCaptor.getValue().getRequestSummary().contains("createdProduct=true"));
        assertEquals("mall-erp-initializer", logCaptor.getValue().getCreator());
        assertEquals("mall-erp-initializer", logCaptor.getValue().getUpdater());
    }

    @Test
    void initializeIsNoOpWhenMappingAlreadyExists() {
        stubWebProduct();
        MallErpProductMappingDO mapping = new MallErpProductMappingDO()
                .setMallSpuId(91L).setMallSkuId(101L).setErpProductId(201L)
                .setErpProductCode("ERP-AUTHORITATIVE").setSyncStatus("SUCCESS");
        ErpProductDO product = ErpProductDO.builder().id(201L).barCode("ERP-AUTHORITATIVE")
                .name("ERP authoritative name").status(CommonStatusEnum.ENABLE.getStatus())
                .purchasePrice(new BigDecimal("88.00")).build();
        when(mappingMapper.selectByMallSkuId(101L)).thenReturn(mapping);
        when(productMapper.selectById(201L)).thenReturn(product);
        when(stockMapper.selectSumByProductId(201L)).thenReturn(BigDecimal.ONE);

        MallErpProductDTO result = service.initializeMallSku(91L, 101L);

        assertEquals("ERP authoritative name", result.getBaseName());
        verify(productMapper, never()).insert(any(ErpProductDO.class));
        verify(productMapper, never()).updateById(any(ErpProductDO.class));
        verify(mappingMapper, never()).insert(any(MallErpProductMappingDO.class));
        verify(mappingMapper, never()).updateById(any(MallErpProductMappingDO.class));
        verify(syncLogMapper, never()).insert(any(MallErpSyncLogDO.class));
    }

    @Test
    void initializeMapsExistingErpProductWithoutChangingIt() {
        stubWebProduct();
        when(productCodeGenerator.generate(162L, 101L)).thenReturn("VANZ-162-101");
        ErpProductDO existing = ErpProductDO.builder().id(201L).barCode("VANZ-162-101")
                .name("Existing ERP product").status(CommonStatusEnum.ENABLE.getStatus())
                .purchasePrice(new BigDecimal("77.00")).build();
        when(productMapper.selectByBarCode("VANZ-162-101")).thenReturn(existing);
        when(stockMapper.selectSumByProductId(201L)).thenReturn(BigDecimal.ZERO);

        MallErpProductDTO result = service.initializeMallSku(91L, 101L);

        assertEquals("SUCCESS", result.getSyncStatus());
        assertEquals("Existing ERP product", result.getBaseName());
        verify(productMapper, never()).insert(any(ErpProductDO.class));
        verify(productMapper, never()).updateById(any(ErpProductDO.class));
        verify(mappingMapper).insert(any(MallErpProductMappingDO.class));
        ArgumentCaptor<MallErpSyncLogDO> logCaptor = ArgumentCaptor.forClass(MallErpSyncLogDO.class);
        verify(syncLogMapper).insert(logCaptor.capture());
        assertTrue(logCaptor.getValue().getRequestSummary().contains("createdProduct=false"));
    }

    @Test
    void initializeFailsWithoutWritingProductWhenErpPrerequisitesAreMissing() {
        stubWebProduct();
        when(productCodeGenerator.generate(162L, 101L)).thenReturn("VANZ-162-101");
        when(categoryMapper.selectByCode("MALL_CATEGORY_301")).thenReturn(null);
        when(categoryMapper.selectByCode("FURNITURE")).thenReturn(null);

        MallErpProductDTO result = service.initializeMallSku(91L, 101L);

        assertEquals("FAILED", result.getSyncStatus());
        assertTrue(result.getLastError().contains("Furniture category"));
        verify(productMapper, never()).insert(any(ErpProductDO.class));
        verify(mappingMapper, never()).insert(any(MallErpProductMappingDO.class));
        ArgumentCaptor<MallErpSyncLogDO> logCaptor = ArgumentCaptor.forClass(MallErpSyncLogDO.class);
        verify(syncLogMapper).insert(logCaptor.capture());
        assertEquals("MALL_TO_ERP", logCaptor.getValue().getDirection());
        assertEquals("FAILED", logCaptor.getValue().getSyncStatus());
    }

    private void stubWebProduct() {
        ProductSkuRespDTO sku = new ProductSkuRespDTO().setId(101L).setSpuId(91L)
                .setBarCode("").setPrice(19999).setCostPrice(12345).setWeight(12.5);
        ProductSpuRespDTO spu = new ProductSpuRespDTO().setId(91L)
                .setName("Rustic Dining Table").setCategoryId(301L);
        when(productSkuApi.getSku(101L)).thenReturn(CommonResult.success(sku));
        when(productSpuApi.getSpu(91L)).thenReturn(CommonResult.success(spu));
    }

    private static ErpProductCategoryDO enabledCategory(Long id) {
        return ErpProductCategoryDO.builder().id(id).status(CommonStatusEnum.ENABLE.getStatus()).build();
    }

    private static ErpProductUnitDO enabledUnit(Long id) {
        return ErpProductUnitDO.builder().id(id).name("Piece")
                .status(CommonStatusEnum.ENABLE.getStatus()).build();
    }

}
