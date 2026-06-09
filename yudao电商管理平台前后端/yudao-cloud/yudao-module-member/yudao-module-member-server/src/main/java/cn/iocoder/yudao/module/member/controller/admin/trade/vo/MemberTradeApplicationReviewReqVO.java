package cn.iocoder.yudao.module.member.controller.admin.trade.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotNull;

@Schema(description = "Admin - Trade application review request")
@Data
@Accessors(chain = true)
public class MemberTradeApplicationReviewReqVO {

    @Schema(description = "Application id", requiredMode = Schema.RequiredMode.REQUIRED, example = "88")
    @NotNull(message = "Application id cannot be empty")
    private Long id;

    @Schema(description = "Approved Trade ID", example = "RH-TRADE-10086")
    @Length(max = 64, message = "Trade ID length cannot exceed 64")
    private String tradeId;

    @Schema(description = "Review reason", example = "Approved")
    @Length(max = 512, message = "Review reason length cannot exceed 512")
    private String reviewReason;

}
