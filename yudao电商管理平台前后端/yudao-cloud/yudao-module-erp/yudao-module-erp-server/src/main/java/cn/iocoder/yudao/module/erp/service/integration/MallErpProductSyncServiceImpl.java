package cn.iocoder.yudao.module.erp.service.integration;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.erp.api.integration.dto.MallErpProductDTO;
import cn.iocoder.yudao.module.erp.api.integration.dto.MallErpStockDTO;
import cn.iocoder.yudao.module.erp.api.integration.dto.MallErpStockRequestDTO;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertMap;

/**
 * ERP 商品与 Web SKU 的映射同步。
 *
 * ERP 是商品编码、启用状态和实际库存的权威数据源。同步只读取 ERP 数据并维护
 * Web 侧映射及同步状态，不得创建或修改 ERP 商品资料。
 */
@Service
@RequiredArgsConstructor
public class MallErpProductSyncServiceImpl implements MallErpProductSyncService {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_UNMAPPED = "UNMAPPED";

    private final ProductSkuApi productSkuApi;
    private final ProductSpuApi productSpuApi;
    private final MallErpProductCodeGenerator productCodeGenerator;
    private final ErpProductMapper erpProductMapper;
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
                    return STATUS_SUCCESS.equals(mapping.getSyncStatus()) && product != null
                            && CommonStatusEnum.ENABLE.getStatus().equals(product.getStatus());
                }).map(MallErpProductMappingDO::getMallSkuId).collect(Collectors.toSet());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MallErpProductDTO syncMallSku(Long mallSpuId, Long mallSkuId) {
        ProductSkuRespDTO sku = productSkuApi.getSku(mallSkuId).getCheckedData();
        ProductSpuRespDTO spu = productSpuApi.getSpu(mallSpuId).getCheckedData();
        if (sku == null) {
            return failedResult(mallSpuId, mallSkuId, "Web SKU does not exist: " + mallSkuId);
        }
        if (spu == null) {
            return failedResult(mallSpuId, mallSkuId, "Web SPU does not exist: " + mallSpuId);
        }
        if (!mallSpuId.equals(sku.getSpuId())) {
            return failedResult(mallSpuId, mallSkuId, "SKU does not belong to the requested SPU");
        }
        return synchronizeResolved(spu, sku).product;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MallErpSyncSummaryDTO syncAll(Collection<Long> mallSkuIds) {
        LinkedHashSet<Long> requestedSkuIds = mallSkuIds == null ? new LinkedHashSet<>()
                : mallSkuIds.stream().filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        MallErpSyncSummaryDTO summary = new MallErpSyncSummaryDTO().setTotalSkus(requestedSkuIds.size());
        if (requestedSkuIds.isEmpty()) {
            return summary;
        }

        List<ProductSkuRespDTO> skuList = productSkuApi.getSkuList(requestedSkuIds).getCheckedData();
        Map<Long, ProductSkuRespDTO> skus = convertMap(
                skuList == null ? Collections.emptyList() : skuList, ProductSkuRespDTO::getId);
        Set<Long> spuIds = skus.values().stream().map(ProductSkuRespDTO::getSpuId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        List<ProductSpuRespDTO> spuList = spuIds.isEmpty() ? Collections.emptyList()
                : productSpuApi.getSpuList(spuIds).getCheckedData();
        Map<Long, ProductSpuRespDTO> spus = convertMap(
                spuList == null ? Collections.emptyList() : spuList, ProductSpuRespDTO::getId);

        for (Long mallSkuId : requestedSkuIds) {
            ProductSkuRespDTO sku = skus.get(mallSkuId);
            if (sku == null) {
                recordOutcome(summary, new SyncOutcome(
                        failedResult(null, mallSkuId, "Web SKU does not exist: " + mallSkuId), false));
                continue;
            }
            ProductSpuRespDTO spu = spus.get(sku.getSpuId());
            if (spu == null) {
                recordOutcome(summary, new SyncOutcome(
                        failedResult(sku.getSpuId(), mallSkuId,
                                "Web SPU does not exist: " + sku.getSpuId()), false));
                continue;
            }
            recordOutcome(summary, synchronizeResolved(spu, sku));
        }
        return summary;
    }

    private SyncOutcome synchronizeResolved(ProductSpuRespDTO spu, ProductSkuRespDTO sku) {
        MallErpProductMappingDO mapping = mappingMapper.selectByMallSkuId(sku.getId());
        if (mapping != null) {
            ErpProductDO product = erpProductMapper.selectById(mapping.getErpProductId());
            if (product == null) {
                String error = "Mapped ERP product does not exist: " + mapping.getErpProductId();
                mapping.setSyncStatus(STATUS_FAILED);
                mapping.setLastSyncedAt(LocalDateTime.now());
                mapping.setLastError(error);
                mappingMapper.updateById(mapping);
                insertFailureLog(sku.getId(), mapping.getErpProductCode(), error);
                return new SyncOutcome(toDTOWithoutProduct(mapping), false);
            }
            mapping.setMallSpuId(spu.getId());
            mapping.setErpProductCode(product.getBarCode());
            mapping.setSyncStatus(STATUS_SUCCESS);
            mapping.setLastSyncedAt(LocalDateTime.now());
            mapping.setLastError("");
            mappingMapper.updateById(mapping);
            insertSuccessLog(mapping, "REFRESH");
            return new SyncOutcome(toDTO(mapping, product), false);
        }

        MatchResult match = findErpProduct(sku);
        if (match.product == null) {
            String error = "ERP product not found for code: " + match.requestedCode;
            insertFailureLog(sku.getId(), match.requestedCode, error);
            return new SyncOutcome(new MallErpProductDTO()
                    .setMallSpuId(spu.getId()).setMallSkuId(sku.getId())
                    .setErpProductCode(match.requestedCode).setSyncStatus(STATUS_UNMAPPED)
                    .setLastSyncedAt(LocalDateTime.now()).setLastError(error), false);
        }

        MallErpProductMappingDO occupiedMapping = mappingMapper.selectByErpProductId(match.product.getId());
        if (occupiedMapping != null && !sku.getId().equals(occupiedMapping.getMallSkuId())) {
            String error = "ERP product is already mapped to Web SKU: " + occupiedMapping.getMallSkuId();
            insertFailureLog(sku.getId(), match.product.getBarCode(), error);
            return new SyncOutcome(new MallErpProductDTO()
                    .setMallSpuId(spu.getId()).setMallSkuId(sku.getId())
                    .setErpProductId(match.product.getId()).setErpProductCode(match.product.getBarCode())
                    .setSyncStatus(STATUS_FAILED).setLastSyncedAt(LocalDateTime.now()).setLastError(error), false);
        }

        mapping = new MallErpProductMappingDO();
        mapping.setMallSpuId(spu.getId());
        mapping.setMallSkuId(sku.getId());
        mapping.setErpProductId(match.product.getId());
        mapping.setErpProductCode(match.product.getBarCode());
        mapping.setSyncStatus(STATUS_SUCCESS);
        mapping.setLastSyncedAt(LocalDateTime.now());
        mapping.setLastError("");
        mapping.setVersion(0);
        mappingMapper.insert(mapping);
        insertSuccessLog(mapping, "MAP");
        return new SyncOutcome(toDTO(mapping, match.product), true);
    }

    private MatchResult findErpProduct(ProductSkuRespDTO sku) {
        LinkedHashSet<String> candidateCodes = new LinkedHashSet<>();
        if (sku.getBarCode() != null && !sku.getBarCode().isBlank()) {
            candidateCodes.add(sku.getBarCode().trim());
        }
        candidateCodes.add(productCodeGenerator.generate(
                TenantContextHolder.getRequiredTenantId(), sku.getId()));
        for (String candidateCode : candidateCodes) {
            ErpProductDO product = erpProductMapper.selectByBarCode(candidateCode);
            if (product != null) {
                return new MatchResult(candidateCode, product);
            }
        }
        return new MatchResult(String.join(" / ", candidateCodes), null);
    }

    private void recordOutcome(MallErpSyncSummaryDTO summary, SyncOutcome outcome) {
        summary.getItems().add(outcome.product);
        if (STATUS_SUCCESS.equals(outcome.product.getSyncStatus())) {
            summary.setMappedSkus(summary.getMappedSkus() + 1);
            if (outcome.created) {
                summary.setNewMappings(summary.getNewMappings() + 1);
            } else {
                summary.setRefreshedMappings(summary.getRefreshedMappings() + 1);
            }
        } else if (STATUS_UNMAPPED.equals(outcome.product.getSyncStatus())) {
            summary.setUnmappedSkus(summary.getUnmappedSkus() + 1);
        } else {
            summary.setFailedSkus(summary.getFailedSkus() + 1);
        }
    }

    private MallErpProductDTO failedResult(Long mallSpuId, Long mallSkuId, String error) {
        insertFailureLog(mallSkuId, null, error);
        return new MallErpProductDTO().setMallSpuId(mallSpuId).setMallSkuId(mallSkuId)
                .setSyncStatus(STATUS_FAILED).setLastSyncedAt(LocalDateTime.now()).setLastError(error);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlinkMallSkus(Collection<Long> mallSkuIds) {
        if (mallSkuIds == null || mallSkuIds.isEmpty()) {
            return;
        }
        List<MallErpProductMappingDO> mappings = mappingMapper.selectListByMallSkuIds(mallSkuIds);
        if (mappings.isEmpty()) {
            return;
        }
        Map<Long, ErpProductDO> products = convertMap(
                erpProductMapper.selectBatchIds(mappings.stream()
                        .map(MallErpProductMappingDO::getErpProductId).collect(Collectors.toSet())),
                ErpProductDO::getId);
        for (MallErpProductMappingDO mapping : mappings) {
            ErpProductDO product = products.get(mapping.getErpProductId());
            if (product != null && !CommonStatusEnum.DISABLE.getStatus().equals(product.getStatus())) {
                erpProductMapper.updateById(new ErpProductDO()
                        .setId(product.getId())
                        .setStatus(CommonStatusEnum.DISABLE.getStatus()));
            }
            mapping.setSyncStatus("UNLINKED");
            mapping.setLastSyncedAt(LocalDateTime.now());
            mapping.setLastError("");
            mappingMapper.updateById(mapping);
            insertUnlinkLog(mapping);
        }
        mappingMapper.deleteByMallSkuIds(mappings.stream()
                .map(MallErpProductMappingDO::getMallSkuId).collect(Collectors.toSet()));
    }

    @Override
    public MallErpProductDTO getByMallSkuId(Long mallSkuId) {
        MallErpProductMappingDO mapping = mappingMapper.selectByMallSkuId(mallSkuId);
        if (mapping == null) {
            return null;
        }
        ErpProductDO product = erpProductMapper.selectById(mapping.getErpProductId());
        return product == null ? toDTOWithoutProduct(mapping) : toDTO(mapping, product);
    }

    @Override
    public MallErpStockDTO getSellableStock(Long mallSkuId) {
        MallErpProductMappingDO mapping = requireMapping(mallSkuId);
        ErpProductDO product = erpProductMapper.selectById(mapping.getErpProductId());
        if (product == null || !CommonStatusEnum.ENABLE.getStatus().equals(product.getStatus())) {
            return unavailableStock(mallSkuId, mapping.getErpProductId());
        }
        BigDecimal stock = normalizeStock(erpStockMapper.selectSumByProductId(mapping.getErpProductId()));
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
        if (mapping == null || !STATUS_SUCCESS.equals(mapping.getSyncStatus())) {
            throw new IllegalStateException("Mall SKU is not mapped to ERP: " + mallSkuId);
        }
        return mapping;
    }

    private static MallErpStockDTO unavailableStock(Long mallSkuId, Long erpProductId) {
        return new MallErpStockDTO().setMallSkuId(mallSkuId).setErpProductId(erpProductId)
                .setSellableStock(BigDecimal.ZERO).setAvailable(false);
    }

    private MallErpProductDTO toDTO(MallErpProductMappingDO mapping, ErpProductDO product) {
        BigDecimal stock = normalizeStock(erpStockMapper.selectSumByProductId(mapping.getErpProductId()));
        return new MallErpProductDTO().setMallSpuId(mapping.getMallSpuId()).setMallSkuId(mapping.getMallSkuId())
                .setErpProductId(mapping.getErpProductId()).setErpProductCode(mapping.getErpProductCode())
                .setBaseName(product.getName()).setCostPrice(product.getPurchasePrice())
                .setEnabled(CommonStatusEnum.ENABLE.getStatus().equals(product.getStatus()))
                .setSellableStock(stock).setSyncStatus(mapping.getSyncStatus())
                .setLastSyncedAt(mapping.getLastSyncedAt()).setLastError(mapping.getLastError());
    }

    private MallErpProductDTO toDTOWithoutProduct(MallErpProductMappingDO mapping) {
        return new MallErpProductDTO().setMallSpuId(mapping.getMallSpuId()).setMallSkuId(mapping.getMallSkuId())
                .setErpProductId(mapping.getErpProductId()).setErpProductCode(mapping.getErpProductCode())
                .setEnabled(false).setSellableStock(BigDecimal.ZERO).setSyncStatus(STATUS_FAILED)
                .setLastSyncedAt(mapping.getLastSyncedAt()).setLastError(mapping.getLastError());
    }

    private static BigDecimal normalizeStock(BigDecimal stock) {
        return stock == null ? BigDecimal.ZERO : stock;
    }

    private void insertSuccessLog(MallErpProductMappingDO mapping, String eventType) {
        MallErpSyncLogDO log = new MallErpSyncLogDO();
        log.setEntityType("PRODUCT_SKU");
        log.setEntityId(mapping.getMallSkuId());
        log.setDirection("ERP_TO_MALL");
        log.setEventType(eventType);
        log.setIdempotencyKey(mapping.getErpProductCode() + "-" + eventType.toLowerCase()
                + "-" + System.currentTimeMillis());
        log.setRequestSummary("mallSkuId=" + mapping.getMallSkuId()
                + ",erpProductId=" + mapping.getErpProductId());
        log.setSyncStatus(STATUS_SUCCESS);
        log.setLastError("");
        log.setRetryCount(0);
        syncLogMapper.insert(log);
    }

    private void insertFailureLog(Long mallSkuId, String productCode, String error) {
        MallErpSyncLogDO log = new MallErpSyncLogDO();
        log.setEntityType("PRODUCT_SKU");
        log.setEntityId(mallSkuId);
        log.setDirection("ERP_TO_MALL");
        log.setEventType("MATCH");
        log.setIdempotencyKey((productCode == null ? "unmatched" : productCode)
                + "-match-" + System.currentTimeMillis());
        log.setRequestSummary("mallSkuId=" + mallSkuId);
        log.setSyncStatus(STATUS_FAILED);
        log.setLastError(error);
        log.setRetryCount(0);
        syncLogMapper.insert(log);
    }

    private void insertUnlinkLog(MallErpProductMappingDO mapping) {
        MallErpSyncLogDO log = new MallErpSyncLogDO();
        log.setEntityType("PRODUCT_SKU");
        log.setEntityId(mapping.getMallSkuId());
        log.setDirection("MALL_TO_ERP");
        log.setEventType("UNLINK");
        log.setIdempotencyKey(mapping.getErpProductCode() + "-unlink-" + System.currentTimeMillis());
        log.setRequestSummary("mallSkuId=" + mapping.getMallSkuId()
                + ",erpProductId=" + mapping.getErpProductId());
        log.setSyncStatus(STATUS_SUCCESS);
        log.setLastError("");
        log.setRetryCount(0);
        syncLogMapper.insert(log);
    }

    private static class SyncOutcome {
        private final MallErpProductDTO product;
        private final boolean created;

        private SyncOutcome(MallErpProductDTO product, boolean created) {
            this.product = product;
            this.created = created;
        }
    }

    private static class MatchResult {
        private final String requestedCode;
        private final ErpProductDO product;

        private MatchResult(String requestedCode, ErpProductDO product) {
            this.requestedCode = requestedCode;
            this.product = product;
        }
    }

}
