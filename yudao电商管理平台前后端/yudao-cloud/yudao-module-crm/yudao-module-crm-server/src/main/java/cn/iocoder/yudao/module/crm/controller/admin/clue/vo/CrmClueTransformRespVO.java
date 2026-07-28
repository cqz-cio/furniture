package cn.iocoder.yudao.module.crm.controller.admin.clue.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "管理后台 - 询盘生成客户档案 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrmClueTransformRespVO {

    @Schema(description = "客户编号")
    private Long customerId;

    @Schema(description = "联系人编号")
    private Long contactId;

    @Schema(description = "是否新建客户")
    private Boolean customerCreated;

    @Schema(description = "是否新建联系人")
    private Boolean contactCreated;

}
