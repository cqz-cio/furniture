package cn.iocoder.yudao.module.product.controller.app.spu.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Schema(description = "用户 App - 商品字段展示策略 Response VO")
@Data
public class AppProductFieldPolicyRespVO {

    @Schema(description = "策略来源", requiredMode = Schema.RequiredMode.REQUIRED, example = "erp-tenant")
    private String source;

    @Schema(description = "字段编码与公开状态", requiredMode = Schema.RequiredMode.REQUIRED)
    private Map<String, Boolean> fields;

}
