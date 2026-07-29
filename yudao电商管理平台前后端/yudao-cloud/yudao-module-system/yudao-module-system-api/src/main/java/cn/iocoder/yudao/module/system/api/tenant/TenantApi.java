package cn.iocoder.yudao.module.system.api.tenant;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.system.api.tenant.dto.TenantRespDTO;
import cn.iocoder.yudao.module.system.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import static cn.iocoder.yudao.module.system.api.tenant.TenantApi.PREFIX;

@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC 服务 - 租户信息")
public interface TenantApi {

    String PREFIX = ApiConstants.PREFIX + "/tenant";

    @GetMapping(PREFIX + "/get")
    @Operation(summary = "通过租户 ID 查询租户")
    @Parameter(name = "id", description = "租户编号", example = "162", required = true)
    CommonResult<TenantRespDTO> getTenant(@RequestParam("id") Long id);

}
