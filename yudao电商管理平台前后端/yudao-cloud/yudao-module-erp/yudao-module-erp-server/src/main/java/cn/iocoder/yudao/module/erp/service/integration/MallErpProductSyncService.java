package cn.iocoder.yudao.module.erp.service.integration;

import cn.iocoder.yudao.module.erp.api.integration.dto.MallErpProductDTO;
import cn.iocoder.yudao.module.erp.api.integration.dto.MallErpStockDTO;
import cn.iocoder.yudao.module.erp.api.integration.dto.MallErpStockRequestDTO;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface MallErpProductSyncService {
    MallErpProductDTO syncMallSku(Long mallSpuId, Long mallSkuId);
    List<MallErpProductDTO> syncAll();
    MallErpProductDTO getByMallSkuId(Long mallSkuId);
    MallErpStockDTO getSellableStock(Long mallSkuId);
    MallErpStockDTO validateStock(MallErpStockRequestDTO request);
    Set<Long> getMappedMallSkuIds(Collection<Long> mallSkuIds);
}
