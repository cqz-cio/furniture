package cn.iocoder.yudao.module.member.controller.admin.trade.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "Admin - Trade application page request")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MemberTradeApplicationPageReqVO extends PageParam {

    @Schema(description = "Application status", example = "0")
    private Integer status;

    @Schema(description = "Primary email", example = "designer@example.com")
    private String primaryEmail;

    @Schema(description = "Business name", example = "AD Studio")
    private String businessName;

}
