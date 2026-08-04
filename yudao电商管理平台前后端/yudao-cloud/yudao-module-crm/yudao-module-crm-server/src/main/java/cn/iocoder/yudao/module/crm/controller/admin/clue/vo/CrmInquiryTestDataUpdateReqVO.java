package cn.iocoder.yudao.module.crm.controller.admin.clue.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 询盘经营/测试数据标记 Request VO")
@Data
public class CrmInquiryTestDataUpdateReqVO {

    @Schema(description = "询盘编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "询盘编号不能为空")
    private Long id;

    @Schema(description = "是否测试数据", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    @NotNull(message = "测试数据标记不能为空")
    private Boolean testData;

}
