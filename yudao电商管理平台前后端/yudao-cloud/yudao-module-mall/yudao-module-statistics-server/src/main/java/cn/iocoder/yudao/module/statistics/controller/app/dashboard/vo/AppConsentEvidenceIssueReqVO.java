package cn.iocoder.yudao.module.statistics.controller.app.dashboard.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AppConsentEvidenceIssueReqVO {
    @NotBlank
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
    private String consentId;

    @NotBlank
    @Size(max = 32)
    private String policyVersion;

    @NotNull
    private Boolean preferences;

    @NotNull
    private Boolean analytics;

    @NotNull
    private Boolean marketing;
}
