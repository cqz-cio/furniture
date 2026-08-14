package cn.iocoder.yudao.module.promotion.api.product;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.promotion.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC 服务 - 营销商品引用")
public interface PromotionProductApi {

    String PREFIX = ApiConstants.PREFIX + "/product-reference";

    @GetMapping(PREFIX + "/validate-deletable")
    @Operation(summary = "校验商品是否可删除")
    @Parameter(name = "spuId", description = "商品 SPU 编号", required = true)
    CommonResult<Boolean> validateSpuDeletable(@RequestParam("spuId") Long spuId);

}
