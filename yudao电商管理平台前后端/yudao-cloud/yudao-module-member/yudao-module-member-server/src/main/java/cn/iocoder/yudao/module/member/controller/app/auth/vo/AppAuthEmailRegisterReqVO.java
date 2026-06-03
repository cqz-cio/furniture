package cn.iocoder.yudao.module.member.controller.app.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@Schema(description = "用户 APP - 邮箱注册 Request VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppAuthEmailRegisterReqVO {

    @Schema(description = "名", requiredMode = Schema.RequiredMode.REQUIRED, example = "Black")
    @NotEmpty(message = "名不能为空")
    @Length(max = 30, message = "名长度不能超过 30 位")
    private String firstName;

    @Schema(description = "姓", requiredMode = Schema.RequiredMode.REQUIRED, example = "Furniture")
    @NotEmpty(message = "姓不能为空")
    @Length(max = 30, message = "姓长度不能超过 30 位")
    private String lastName;

    @Schema(description = "邮箱", requiredMode = Schema.RequiredMode.REQUIRED, example = "designer@example.com")
    @NotEmpty(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "admin123")
    @NotEmpty(message = "密码不能为空")
    @Length(min = 4, max = 16, message = "密码长度为 4-16 位")
    private String password;

    @Schema(description = "是否订阅邮件", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean emailOptIn;

    @Schema(description = "是否同意隐私条款", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    @NotNull(message = "请先阅读并同意隐私条款")
    @AssertTrue(message = "请先阅读并同意隐私条款")
    private Boolean privacyAccepted;

}
