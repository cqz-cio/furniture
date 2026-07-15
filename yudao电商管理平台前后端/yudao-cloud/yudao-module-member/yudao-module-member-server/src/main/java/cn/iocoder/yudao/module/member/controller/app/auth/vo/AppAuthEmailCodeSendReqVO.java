package cn.iocoder.yudao.module.member.controller.app.auth.vo;

import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.member.enums.auth.MemberEmailAuthSceneEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

@Schema(description = "用户 APP - 发送邮箱验证码 Request VO")
@Data
@NoArgsConstructor
public class AppAuthEmailCodeSendReqVO {

    @Schema(description = "发送场景，对应 MemberEmailAuthSceneEnum 枚举", requiredMode = Schema.RequiredMode.REQUIRED, example = "4")
    @NotNull(message = "发送场景不能为空")
    @InEnum(MemberEmailAuthSceneEnum.class)
    private Integer scene;

    @Schema(description = "邮箱；登录态场景可不传，后端会使用当前用户邮箱", example = "user@example.com")
    @Email(message = "邮箱格式不正确")
    @Length(max = 255, message = "邮箱长度不能超过 255 个字符")
    private String email;

    @Schema(description = "图形验证码二次校验凭证；触发频控后必传", example = "captcha-token")
    private String captchaVerification;

    public AppAuthEmailCodeSendReqVO(Integer scene, String email) {
        this.scene = scene;
        this.email = email;
    }

    public AppAuthEmailCodeSendReqVO(Integer scene, String email, String captchaVerification) {
        this.scene = scene;
        this.email = email;
        this.captchaVerification = captchaVerification;
    }

}
