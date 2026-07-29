package cn.iocoder.yudao.module.system.api.tenant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "RPC 服务 - 租户信息 Response DTO")
@Data
public class TenantRespDTO {

    @Schema(description = "租户 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "162")
    private Long id;

    @Schema(description = "租户名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "Vanz家具")
    private String name;

    @Schema(description = "租户编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "VANZ")
    private String code;

}
