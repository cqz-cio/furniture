package cn.iocoder.yudao.module.member.controller.app.address.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "User App - address verification provider status response")
@Data
public class AppAddressVerificationStatusRespVO {

    @Schema(description = "Current verification mode", example = "fallback")
    private String mode;

    @Schema(description = "Whether checkout verification is currently using a fallback provider")
    private Boolean fallbackActive;

    @Schema(description = "Configured provider chain in execution order")
    private List<ProviderStatus> providers = new ArrayList<>();

    @Schema(description = "Address verification provider status")
    @Data
    public static class ProviderStatus {

        @Schema(description = "Provider source code", example = "google-address-validation")
        private String source;

        @Schema(description = "Provider display name", example = "Google Address Validation")
        private String name;

        @Schema(description = "Whether this provider can be used with the current configuration")
        private Boolean enabled;

        @Schema(description = "Whether this provider is a fallback provider")
        private Boolean fallback;

        @Schema(description = "Configuration or status reason", example = "missing-api-key")
        private String reason;

        @Schema(description = "Whether USPS CASS is requested when the provider supports it")
        private Boolean uspsCassEnabled;

    }

}
