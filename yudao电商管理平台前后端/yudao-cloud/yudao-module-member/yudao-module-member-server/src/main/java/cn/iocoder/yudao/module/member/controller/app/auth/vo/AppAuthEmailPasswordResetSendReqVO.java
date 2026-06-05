package cn.iocoder.yudao.module.member.controller.app.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

@Schema(description = "用户 APP - 发送邮箱重置密码邮件 Request VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppAuthEmailPasswordResetSendReqVO {

    @Schema(description = "邮箱", requiredMode = Schema.RequiredMode.REQUIRED, example = "user@example.com")
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Length(max = 255, message = "邮箱长度不能超过 255 个字符")
    private String email;

}
