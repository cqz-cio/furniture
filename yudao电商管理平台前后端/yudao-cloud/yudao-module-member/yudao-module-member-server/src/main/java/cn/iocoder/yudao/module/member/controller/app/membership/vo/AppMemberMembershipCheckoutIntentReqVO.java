package cn.iocoder.yudao.module.member.controller.app.membership.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Schema(description = "User App - Membership checkout intent request")
@Data
public class AppMemberMembershipCheckoutIntentReqVO {

    @Schema(description = "Membership plan code", requiredMode = Schema.RequiredMode.REQUIRED, example = "annual_membership")
    @NotBlank(message = "Membership plan code is required")
    private String planCode;

}
