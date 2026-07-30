package cn.iocoder.yudao.module.crm.controller.admin.clue.vo;

import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.crm.enums.clue.CrmInquiryProcessStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 询盘处理状态更新 Request VO")
@Data
public class CrmInquiryProcessStatusUpdateReqVO {

    @Schema(description = "询盘编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "询盘编号不能为空")
    private Long id;

    @Schema(description = "处理状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @NotNull(message = "处理状态不能为空")
    @InEnum(CrmInquiryProcessStatusEnum.class)
    private Integer processStatus;

    @Schema(description = "处理备注", example = "客户信息有效，等待业务人员联系")
    @Size(max = 500, message = "处理备注不能超过 500 个字符")
    private String remark;

}
