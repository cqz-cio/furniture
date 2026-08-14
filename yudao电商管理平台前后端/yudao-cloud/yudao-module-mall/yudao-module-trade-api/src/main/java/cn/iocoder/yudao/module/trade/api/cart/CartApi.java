package cn.iocoder.yudao.module.trade.api.cart;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.trade.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC 服务 - 购物车")
public interface CartApi {

    String PREFIX = ApiConstants.PREFIX + "/cart";

    @DeleteMapping(PREFIX + "/by-spu-id")
    @Operation(summary = "清理指定商品的购物车记录")
    @Parameter(name = "spuId", description = "商品 SPU 编号", required = true)
    CommonResult<Boolean> deleteCartBySpuId(@RequestParam("spuId") Long spuId);

}
