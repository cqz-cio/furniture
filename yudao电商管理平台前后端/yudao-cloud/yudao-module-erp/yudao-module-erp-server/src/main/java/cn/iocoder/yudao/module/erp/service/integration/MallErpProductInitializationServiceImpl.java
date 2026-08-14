package cn.iocoder.yudao.module.erp.service.integration;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Web 商品首次建档到 ERP。
 *
 * 与 ERP -> Web 同步服务严格分开：这里只有在没有 ERP 商品时才允许创建一次，
 * 已存在的 ERP 商品及映射永远不会被 Web 商品编辑覆盖。
 */
@Service
@RequiredArgsConstructor
public class MallErpProductInitializationServiceImpl implements MallErpProductInitializationService {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String ROOT_CATEGORY_CODE = "FURNITURE";
    private static final String MALL_CATEGORY_CODE_PREFIX = "MALL_CATEGORY_";
    private static final String DEFAULT_UNIT_NAME = "Piece";

    private final ProductSkuApi productSkuApi;
    private final ProductSpuApi productSpuApi;
    private final MallErpProductCodeGenerator productCodeGenerator;
    private final ErpProductMapper erpProductMapper;
    private final ErpProductCategoryMapper erpProductCategoryMapper;
    private final ErpProductUnitMapper erpProductUnitMapper;
    private final ErpStockMapper erpStockMapper;
    private final MallErpProductMappingMapper mappingMapper;
    private final MallErpSyncLogMapper syncLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MallErpProductDTO initializeMallSku(Long mallSpuId, Long mallSkuId) {
        ProductSkuRespDTO sku = productSkuApi.getSku(mallSkuId).getCheckedData();
        ProductSpuRespDTO spu = productSpuApi.getSpu(mallSpuId).getCheckedData();
        if (sku == null) {
            return failedResult(mallSpuId, mallSkuId, null,
                    "Web SKU does not exist: " + mallSkuId);
        }
        if (spu == null) {
            return failedResult(mallSpuId, mallSkuId, null,
                    "Web SPU does not exist: " + mallSpuId);
        }
        if (!mallSpuId.equals(sku.getSpuId())) {
            return failedResult(mallSpuId, mallSkuId, null,
                    "SKU does not belong to the requested SPU");
        }

        MallErpProductMappingDO existingMapping = mappingMapper.selectByMallSkuId(mallSkuId);
        if (existingMapping != null) {
            ErpProductDO existingProduct = erpProductMapper.selectById(existingMapping.getErpProductId());
            if (existingProduct == null) {
                return failedResult(mallSpuId, mallSkuId, existingMapping.getErpProductCode(),
                        "Mapped ERP product does not exist: " + existingMapping.getErpProductId());
            }
            // 幂等返回，绝不使用 Web 数据覆盖已存在的 ERP 商品或映射。
            return toDTO(existingMapping, existingProduct);
        }

        ErpProductDO erpProduct = findExistingErpProduct(sku);
        boolean createdProduct = false;
        if (erpProduct == null) {
            String productCode = productCodeGenerator.generate(
                    TenantContextHolder.getRequiredTenantId(), mallSkuId);
            ErpProductCategoryDO category = resolveCategory(spu);
            if (category == null) {
                return failedResult(mallSpuId, mallSkuId, productCode,
                        "No enabled ERP Furniture category is configured");
            }
            ErpProductUnitDO unit = resolveUnit();
            if (unit == null) {
                return failedResult(mallSpuId, mallSkuId, productCode,
                        "No enabled ERP product unit is configured");
            }
            erpProduct = buildInitialProduct(spu, sku, productCode, category.getId(), unit.getId());
            erpProductMapper.insert(erpProduct);
            createdProduct = true;
        }

        MallErpProductMappingDO occupiedMapping = mappingMapper.selectByErpProductId(erpProduct.getId());
        if (occupiedMapping != null && !mallSkuId.equals(occupiedMapping.getMallSkuId())) {
            return failedResult(mallSpuId, mallSkuId, erpProduct.getBarCode(),
                    "ERP product is already mapped to Web SKU: " + occupiedMapping.getMallSkuId());
        }

        MallErpProductMappingDO mapping = new MallErpProductMappingDO()
                .setMallSpuId(mallSpuId)
                .setMallSkuId(mallSkuId)
                .setErpProductId(erpProduct.getId())
                .setErpProductCode(erpProduct.getBarCode())
                .setSyncStatus(STATUS_SUCCESS)
                .setLastSyncedAt(LocalDateTime.now())
                .setLastError("")
                .setVersion(0);
        mappingMapper.insert(mapping);
        insertSuccessLog(mapping, createdProduct);
        return toDTO(mapping, erpProduct);
    }

    private ErpProductDO findExistingErpProduct(ProductSkuRespDTO sku) {
        LinkedHashSet<String> candidateCodes = new LinkedHashSet<>();
        if (sku.getBarCode() != null && !sku.getBarCode().isBlank()) {
            candidateCodes.add(sku.getBarCode().trim());
        }
        candidateCodes.add(productCodeGenerator.generate(
                TenantContextHolder.getRequiredTenantId(), sku.getId()));
        for (String candidateCode : candidateCodes) {
            ErpProductDO product = erpProductMapper.selectByBarCode(candidateCode);
            if (product != null) {
                return product;
            }
        }
        return null;
    }

    private ErpProductCategoryDO resolveCategory(ProductSpuRespDTO spu) {
        if (spu.getCategoryId() != null) {
            ErpProductCategoryDO category = erpProductCategoryMapper.selectByCode(
                    MALL_CATEGORY_CODE_PREFIX + spu.getCategoryId());
            if (isEnabled(category)) {
                return category;
            }
        }
        ErpProductCategoryDO rootCategory = erpProductCategoryMapper.selectByCode(ROOT_CATEGORY_CODE);
        return isEnabled(rootCategory) ? rootCategory : null;
    }

    private ErpProductUnitDO resolveUnit() {
        ErpProductUnitDO defaultUnit = erpProductUnitMapper.selectByName(DEFAULT_UNIT_NAME);
        if (isEnabled(defaultUnit)) {
            return defaultUnit;
        }
        List<ErpProductUnitDO> units = erpProductUnitMapper.selectListByStatus(
                CommonStatusEnum.ENABLE.getStatus());
        return units == null ? null : units.stream()
                .min(Comparator.comparing(ErpProductUnitDO::getId,
                        Comparator.nullsLast(Long::compareTo)))
                .orElse(null);
    }

    private static boolean isEnabled(ErpProductCategoryDO category) {
        return category != null && CommonStatusEnum.ENABLE.getStatus().equals(category.getStatus());
    }

    private static boolean isEnabled(ErpProductUnitDO unit) {
        return unit != null && CommonStatusEnum.ENABLE.getStatus().equals(unit.getStatus());
    }

    private static ErpProductDO buildInitialProduct(ProductSpuRespDTO spu, ProductSkuRespDTO sku,
                                                    String productCode, Long categoryId, Long unitId) {
        BigDecimal salePrice = toYuan(sku.getPrice());
        return ErpProductDO.builder()
                .name(spu.getName())
                .barCode(productCode)
                .categoryId(categoryId)
                .unitId(unitId)
                .status(CommonStatusEnum.ENABLE.getStatus())
                .standard("Web SKU " + sku.getId())
                .remark("Initialized from Web product creation; ERP owns subsequent changes")
                .expiryDay(0)
                .weight(toWeight(sku.getWeight()))
                .purchasePrice(toYuan(sku.getCostPrice()))
                .salePrice(salePrice)
                .minPrice(salePrice)
                .build();
    }

    private MallErpProductDTO toDTO(MallErpProductMappingDO mapping, ErpProductDO product) {
        BigDecimal stock = erpStockMapper.selectSumByProductId(product.getId());
        return new MallErpProductDTO()
                .setMallSpuId(mapping.getMallSpuId())
                .setMallSkuId(mapping.getMallSkuId())
                .setErpProductId(product.getId())
                .setErpProductCode(product.getBarCode())
                .setBaseName(product.getName())
                .setCostPrice(product.getPurchasePrice())
                .setEnabled(CommonStatusEnum.ENABLE.getStatus().equals(product.getStatus()))
                .setSellableStock(stock == null ? BigDecimal.ZERO : stock)
                .setSyncStatus(mapping.getSyncStatus())
                .setLastSyncedAt(mapping.getLastSyncedAt())
                .setLastError(mapping.getLastError());
    }

    private MallErpProductDTO failedResult(Long mallSpuId, Long mallSkuId, String productCode, String error) {
        insertFailureLog(mallSkuId, productCode, error);
        return new MallErpProductDTO()
                .setMallSpuId(mallSpuId)
                .setMallSkuId(mallSkuId)
                .setErpProductCode(productCode)
                .setEnabled(false)
                .setSellableStock(BigDecimal.ZERO)
                .setSyncStatus(STATUS_FAILED)
                .setLastSyncedAt(LocalDateTime.now())
                .setLastError(error);
    }

    private void insertSuccessLog(MallErpProductMappingDO mapping, boolean createdProduct) {
        MallErpSyncLogDO log = new MallErpSyncLogDO();
        log.setEntityType("PRODUCT_SKU");
        log.setEntityId(mapping.getMallSkuId());
        log.setDirection("MALL_TO_ERP");
        log.setEventType("INITIALIZE");
        log.setIdempotencyKey(mapping.getErpProductCode() + "-initialize-" + mapping.getMallSkuId());
        log.setRequestSummary("mallSkuId=" + mapping.getMallSkuId()
                + ",erpProductId=" + mapping.getErpProductId()
                + ",createdProduct=" + createdProduct);
        log.setSyncStatus(STATUS_SUCCESS);
        log.setLastError("");
        log.setRetryCount(0);
        syncLogMapper.insert(log);
    }

    private void insertFailureLog(Long mallSkuId, String productCode, String error) {
        MallErpSyncLogDO log = new MallErpSyncLogDO();
        log.setEntityType("PRODUCT_SKU");
        log.setEntityId(mallSkuId);
        log.setDirection("MALL_TO_ERP");
        log.setEventType("INITIALIZE");
        log.setIdempotencyKey((productCode == null ? "uninitialized" : productCode)
                + "-initialize-failed-" + System.currentTimeMillis());
        log.setRequestSummary("mallSkuId=" + mallSkuId);
        log.setSyncStatus(STATUS_FAILED);
        log.setLastError(error);
        log.setRetryCount(0);
        syncLogMapper.insert(log);
    }

    private static BigDecimal toYuan(Integer cents) {
        if (cents == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal toWeight(Double weight) {
        return weight == null ? BigDecimal.ZERO.setScale(3)
                : BigDecimal.valueOf(weight).setScale(3, RoundingMode.HALF_UP);
    }

}
