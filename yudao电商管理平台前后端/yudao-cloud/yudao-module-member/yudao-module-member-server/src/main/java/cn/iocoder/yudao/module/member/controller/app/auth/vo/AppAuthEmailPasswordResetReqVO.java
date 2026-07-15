package cn.iocoder.yudao.module.member.controller.app.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

import jakarta.validation.constraints.NotBlank;

@Schema(description = "用户 APP - 邮箱重置密码 Request VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppAuthEmailPasswordResetReqVO {

    @Schema(description = "邮箱重置密码 token", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "token 不能为空")
    @Length(max = 128, message = "token 长度不能超过 128 个字符")
    private String token;

    @Schema(description = "新密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "admin123")
    @NotBlank(message = "新密码不能为空")
    @Length(min = 4, max = 16, message = "密码长度为 4-16 位")
    private String password;

}
