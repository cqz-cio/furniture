package cn.iocoder.yudao.module.member.controller.app.membership.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "User App - Membership checkout intent response")
@Data
public class AppMemberMembershipCheckoutIntentRespVO {

    @Schema(description = "Membership plan code", example = "annual_membership")
    private String planCode;

    @Schema(description = "Storefront checkout path", example = "/checkout/auth?intent=membership")
    private String checkoutPath;

    @Schema(description = "Whether payment is required before benefits activate", example = "true")
    private Boolean requiresPayment;

}
