package cn.iocoder.yudao.module.member.controller.app.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "用户 APP - 邮箱验证码风控图形挑战校验 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppAuthEmailCaptchaVerifyRespVO {

    @Schema(description = "图形验证码二次校验凭证", requiredMode = Schema.RequiredMode.REQUIRED)
    private String captchaVerification;

}
