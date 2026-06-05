package cn.iocoder.yudao.module.member.controller.app.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;

@Schema(description = "User App - Email auth image captcha verification request")
@Data
public class AppAuthEmailCaptchaVerifyReqVO {

    @Schema(description = "Challenge id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "Challenge id cannot be empty")
    private String challengeId;

    @Schema(description = "Captcha answer", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "Captcha answer cannot be empty")
    private String code;

}
