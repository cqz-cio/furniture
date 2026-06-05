package cn.iocoder.yudao.module.member.controller.app.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "User App - Email auth image captcha challenge response")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppAuthEmailCaptchaChallengeRespVO {

    @Schema(description = "Challenge id", requiredMode = Schema.RequiredMode.REQUIRED)
    private String challengeId;

    @Schema(description = "Instruction text", requiredMode = Schema.RequiredMode.REQUIRED)
    private String instruction;

    @Schema(description = "Captcha image data URL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String imageBase64;

    @Schema(description = "Captcha type: LINE/CIRCLE/SHEAR/MATH", requiredMode = Schema.RequiredMode.REQUIRED)
    private String captchaType;

}
