package cn.iocoder.yudao.module.erp.api.integration;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.erp.api.integration.dto.MallErpProductDTO;
import cn.iocoder.yudao.module.erp.api.integration.dto.MallErpStockDTO;
import cn.iocoder.yudao.module.erp.api.integration.dto.MallErpStockRequestDTO;
import cn.iocoder.yudao.module.erp.enums.ApiConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@FeignClient(name = ApiConstants.NAME)
public interface MallErpProductApi {

    String PREFIX = ApiConstants.PREFIX + "/mall-integration";

    @PostMapping(PREFIX + "/sync-sku")
    CommonResult<MallErpProductDTO> syncMallSku(@RequestParam("mallSpuId") Long mallSpuId,
                                                @RequestParam("mallSkuId") Long mallSkuId);

    @PostMapping(PREFIX + "/sync-all")
    CommonResult<List<MallErpProductDTO>> syncAllMallSkus();

    @PostMapping(PREFIX + "/unlink-skus")
    CommonResult<Boolean> unlinkMallSkus(@RequestBody Collection<Long> mallSkuIds);

    @GetMapping(PREFIX + "/get-by-mall-sku")
    CommonResult<MallErpProductDTO> getByMallSkuId(@RequestParam("mallSkuId") Long mallSkuId);

    @GetMapping(PREFIX + "/sellable-stock")
    CommonResult<MallErpStockDTO> getSellableStock(@RequestParam("mallSkuId") Long mallSkuId);

    @PostMapping(PREFIX + "/validate-stock")
    CommonResult<List<MallErpStockDTO>> validateSellableStock(@RequestBody List<MallErpStockRequestDTO> items);

    @PostMapping(PREFIX + "/mapped-sku-ids")
    CommonResult<Set<Long>> getMappedMallSkuIds(@RequestBody Collection<Long> mallSkuIds);

}
