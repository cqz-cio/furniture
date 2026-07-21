package cn.iocoder.yudao.module.erp.service.integration;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.erp.api.integration.dto.MallErpProductDTO;
import cn.iocoder.yudao.module.erp.api.integration.dto.MallErpStockDTO;
import cn.iocoder.yudao.module.erp.api.integration.dto.MallErpStockRequestDTO;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertMap;

@Service
@RequiredArgsConstructor
public class MallErpProductSyncServiceImpl implements MallErpProductSyncService {
    private static final String FURNITURE_CATEGORY_CODE = "FURNITURE";

    private final ProductSkuApi productSkuApi;
    private final ProductSpuApi productSpuApi;
    private final ErpProductMapper erpProductMapper;
    private final ErpProductUnitMapper unitMapper;
    private final ErpProductCategoryMapper categoryMapper;
    private final ErpStockMapper erpStockMapper;
    private final MallErpProductMappingMapper mappingMapper;
    private final MallErpSyncLogMapper syncLogMapper;

    @Override
    public Set<Long> getMappedMallSkuIds(Collection<Long> mallSkuIds) {
        if (mallSkuIds == null || mallSkuIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<MallErpProductMappingDO> mappings = mappingMapper.selectListByMallSkuIds(mallSkuIds);
        if (mappings.isEmpty()) {
            return Collections.emptySet();
        }
        Map<Long, ErpProductDO> products = convertMap(
                erpProductMapper.selectBatchIds(mappings.stream().map(MallErpProductMappingDO::getErpProductId)
                        .collect(Collectors.toSet())), ErpProductDO::getId);
        return mappings.stream().filter(mapping -> {
                    ErpProductDO product = products.get(mapping.getErpProductId());
                    return product != null && CommonStatusEnum.ENABLE.getStatus().equals(product.getStatus());
                }).map(MallErpProductMappingDO::getMallSkuId).collect(Collectors.toSet());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MallErpProductDTO syncMallSku(Long mallSpuId, Long mallSkuId) {
        ProductSkuRespDTO sku = productSkuApi.getSku(mallSkuId).getCheckedData();
        ProductSpuRespDTO spu = productSpuApi.getSpu(mallSpuId).getCheckedData();
        if (!mallSpuId.equals(sku.getSpuId())) {
            throw new IllegalArgumentException("SKU does not belong to the requested SPU");
        }
        String productCode = "RH-" + TenantContextHolder.getTenantId() + "-" + mallSkuId;
        MallErpProductMappingDO mapping = mappingMapper.selectByMallSkuId(mallSkuId);
        ErpProductDO product = mapping == null ? new ErpProductDO() : erpProductMapper.selectById(mapping.getErpProductId());
        if (product == null) {
            throw new IllegalStateException("Mapped ERP product does not exist: " + mapping.getErpProductId());
        }
        if (mapping == null) {
            ErpProductUnitDO unit = unitMapper.selectListByStatus(CommonStatusEnum.ENABLE.getStatus()).stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("No enabled ERP product unit"));
            product.setUnitId(unit.getId());
        }
        product.setCategoryId(resolveErpCategory(spu).getId());
        product.setName(spu.getName());
        product.setBarCode(productCode);
        product.setStatus(CommonStatusEnum.ENABLE.getStatus());
        product.setStandard("Mall SKU " + mallSkuId);
        product.setRemark("Synchronized from mall product service");
        product.setWeight(sku.getWeight() == null ? BigDecimal.ZERO : BigDecimal.valueOf(sku.getWeight()));
        product.setPurchasePrice(toYuan(sku.getCostPrice()));
        product.setSalePrice(toYuan(sku.getPrice()));
        product.setMinPrice(toYuan(sku.getPrice()));
        if (mapping == null) {
            erpProductMapper.insert(product);
            mapping = new MallErpProductMappingDO();
            mapping.setMallSpuId(mallSpuId);
            mapping.setMallSkuId(mallSkuId);
            mapping.setErpProductId(product.getId());
            mapping.setErpProductCode(productCode);
            mapping.setVersion(0);
            mappingMapper.insert(mapping);
        } else {
            erpProductMapper.updateById(product);
        }
        mapping.setSyncStatus("SUCCESS");
        mapping.setLastSyncedAt(LocalDateTime.now());
        mapping.setLastError("");
        mappingMapper.updateById(mapping);
        insertSuccessLog(mallSkuId, productCode);
        return toDTO(mapping, product);
    }

    private ErpProductCategoryDO resolveErpCategory(ProductSpuRespDTO spu) {
        ErpProductCategoryDO root = categoryMapper.selectList().stream()
                .filter(category -> FURNITURE_CATEGORY_CODE.equals(category.getCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No ERP furniture category"));
        if (spu.getCategoryName() == null || spu.getCategoryName().isBlank()) {
            return root;
        }
        ErpProductCategoryDO category = categoryMapper.selectByParentIdAndName(root.getId(), spu.getCategoryName());
        if (category != null) {
            return category;
        }
        category = new ErpProductCategoryDO();
        category.setParentId(root.getId());
        category.setName(spu.getCategoryName());
        category.setCode("MALL_CATEGORY_" + spu.getCategoryId());
        category.setSort(100);
        category.setStatus(CommonStatusEnum.ENABLE.getStatus());
        categoryMapper.insert(category);
        return category;
    }

    @Override
    public List<MallErpProductDTO> syncAll() {
        return mappingMapper.selectList().stream()
                .map(mapping -> syncMallSku(mapping.getMallSpuId(), mapping.getMallSkuId()))
                .collect(Collectors.toList());
    }

    @Override
    public MallErpProductDTO getByMallSkuId(Long mallSkuId) {
        MallErpProductMappingDO mapping = mappingMapper.selectByMallSkuId(mallSkuId);
        if (mapping == null) {
            return null;
        }
        return toDTO(mapping, erpProductMapper.selectById(mapping.getErpProductId()));
    }

    @Override
    public MallErpStockDTO getSellableStock(Long mallSkuId) {
        MallErpProductMappingDO mapping = requireMapping(mallSkuId);
        BigDecimal stock = erpStockMapper.selectSumByProductId(mapping.getErpProductId());
        return new MallErpStockDTO().setMallSkuId(mallSkuId).setErpProductId(mapping.getErpProductId())
                .setSellableStock(stock).setAvailable(stock.compareTo(BigDecimal.ZERO) > 0);
    }

    @Override
    public MallErpStockDTO validateStock(MallErpStockRequestDTO request) {
        if (request.getCount() == null || request.getCount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("count must be greater than zero");
        }
        MallErpStockDTO stock = getSellableStock(request.getMallSkuId());
        return stock.setRequestedCount(request.getCount())
                .setAvailable(stock.getSellableStock().compareTo(request.getCount()) >= 0);
    }

    private MallErpProductMappingDO requireMapping(Long mallSkuId) {
        MallErpProductMappingDO mapping = mappingMapper.selectByMallSkuId(mallSkuId);
        if (mapping == null) {
            throw new IllegalStateException("Mall SKU is not mapped to ERP: " + mallSkuId);
        }
        return mapping;
    }

    private MallErpProductDTO toDTO(MallErpProductMappingDO mapping, ErpProductDO product) {
        BigDecimal stock = erpStockMapper.selectSumByProductId(mapping.getErpProductId());
        return new MallErpProductDTO().setMallSpuId(mapping.getMallSpuId()).setMallSkuId(mapping.getMallSkuId())
                .setErpProductId(mapping.getErpProductId()).setErpProductCode(mapping.getErpProductCode())
                .setBaseName(product.getName()).setCostPrice(product.getPurchasePrice())
                .setEnabled(CommonStatusEnum.ENABLE.getStatus().equals(product.getStatus()))
                .setSellableStock(stock).setSyncStatus(mapping.getSyncStatus())
                .setLastSyncedAt(mapping.getLastSyncedAt());
    }

    private void insertSuccessLog(Long mallSkuId, String productCode) {
        MallErpSyncLogDO log = new MallErpSyncLogDO();
        log.setEntityType("PRODUCT_SKU");
        log.setEntityId(mallSkuId);
        log.setDirection("MALL_TO_ERP");
        log.setEventType("UPSERT");
        log.setIdempotencyKey(productCode + "-" + System.currentTimeMillis());
        log.setRequestSummary("mallSkuId=" + mallSkuId);
        log.setSyncStatus("SUCCESS");
        log.setLastError("");
        log.setRetryCount(0);
        syncLogMapper.insert(log);
    }

    private static BigDecimal toYuan(Integer cents) {
        return cents == null ? BigDecimal.ZERO : BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
