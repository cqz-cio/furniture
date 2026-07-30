package cn.iocoder.yudao.module.system.controller.admin.tenant.vo.tenant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - 当前有效租户业务配置 Response VO")
@Data
public class TenantBusinessProfileRespVO {

    @Schema(description = "当前有效租户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "162")
    private Long tenantId;

    @Schema(description = "业务模式", requiredMode = Schema.RequiredMode.REQUIRED, example = "B2B")
    private String businessMode;

    @Schema(description = "是否启用库存管理界面", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
    private Boolean inventoryEnabled;

    @Schema(description = "网站公开商品字段", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> websiteProductFields;

    @Schema(description = "ERP 商品管理字段状态：WEBSITE、INTERNAL、NOT_APPLICABLE",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Map<String, String> productFieldStates;

}
