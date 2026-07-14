package cn.iocoder.yudao.module.member.controller.app.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;

@Schema(description = "User App - Trade Program login code send request")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppAuthTradeLoginCodeSendReqVO {

    @Schema(description = "Trade ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "RH-TRADE-10086")
    @NotEmpty(message = "Trade ID cannot be empty")
    @Length(max = 64, message = "Trade ID length cannot exceed 64")
    private String tradeId;

    @Schema(description = "Email", requiredMode = Schema.RequiredMode.REQUIRED, example = "designer@example.com")
    @NotEmpty(message = "Email cannot be empty")
    @Email(message = "Email format is invalid")
    @Length(max = 255, message = "Email length cannot exceed 255")
    private String email;

    @Schema(description = "Captcha verification token", example = "captcha-token")
    private String captchaVerification;

}
