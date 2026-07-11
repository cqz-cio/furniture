package cn.iocoder.yudao.module.erp.api.integration;

import cn.iocoder.yudao.module.erp.api.integration.dto.MallErpProductDTO;
import cn.iocoder.yudao.module.erp.api.integration.dto.MallErpStockDTO;
import cn.iocoder.yudao.module.erp.api.integration.dto.MallErpStockRequestDTO;

import java.util.List;

public interface MallErpProductApi {

    MallErpProductDTO syncMallSku(Long mallSpuId, Long mallSkuId);

    List<MallErpProductDTO> syncAllMallSkus();

    MallErpProductDTO getByMallSkuId(Long mallSkuId);

    MallErpStockDTO getSellableStock(Long mallSkuId);

    List<MallErpStockDTO> validateSellableStock(List<MallErpStockRequestDTO> items);

}
