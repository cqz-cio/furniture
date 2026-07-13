package cn.iocoder.yudao.module.erp.api.integration;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.erp.api.integration.dto.MallErpProductDTO;
import cn.iocoder.yudao.module.erp.api.integration.dto.MallErpStockDTO;
import cn.iocoder.yudao.module.erp.api.integration.dto.MallErpStockRequestDTO;
import cn.iocoder.yudao.module.erp.service.integration.MallErpProductSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequiredArgsConstructor
public class MallErpProductApiImpl implements MallErpProductApi {
    private final MallErpProductSyncService syncService;

    public CommonResult<MallErpProductDTO> syncMallSku(Long mallSpuId, Long mallSkuId) {
        return success(syncService.syncMallSku(mallSpuId, mallSkuId));
    }

    public CommonResult<List<MallErpProductDTO>> syncAllMallSkus() {
        return success(syncService.syncAll());
    }

    public CommonResult<MallErpProductDTO> getByMallSkuId(Long mallSkuId) {
        return success(syncService.getByMallSkuId(mallSkuId));
    }

    public CommonResult<MallErpStockDTO> getSellableStock(Long mallSkuId) {
        return success(syncService.getSellableStock(mallSkuId));
    }

    public CommonResult<List<MallErpStockDTO>> validateSellableStock(List<MallErpStockRequestDTO> items) {
        return success(items.stream().map(syncService::validateStock).collect(java.util.stream.Collectors.toList()));
    }

    public CommonResult<Set<Long>> getMappedMallSkuIds(Collection<Long> mallSkuIds) {
        return success(syncService.getMappedMallSkuIds(mallSkuIds));
    }
}
