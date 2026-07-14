package cn.iocoder.yudao.module.member.controller.app.address.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Schema(description = "User App - address verification response")
@Data
public class AppAddressVerifyRespVO {

    @Schema(description = "Verification source")
    private String source;

    @Schema(description = "Verification status: verified, suggested, or unverified")
    private String status;

    @Schema(description = "Verification reason")
    private String reason;

    @Schema(description = "Whether checkout should ask the user to confirm this address")
    private Boolean requiresConfirmation;

    @Schema(description = "Original address used for audit display")
    private Object originalAddress;

    @Schema(description = "Suggested standardized address")
    private AppAddressVerifyReqVO.Address suggestedAddress;

    @Schema(description = "True only when a trusted external provider confirms deliverability")
    private Boolean deliverable;

    @Schema(description = "Provider status such as fallback when remote verification is unavailable")
    private String providerStatus;

    @Schema(description = "Provider response id for support and audit lookup")
    private String providerResponseId;

    @Schema(description = "Provider metadata")
    private Map<String, Object> metadata;

}
