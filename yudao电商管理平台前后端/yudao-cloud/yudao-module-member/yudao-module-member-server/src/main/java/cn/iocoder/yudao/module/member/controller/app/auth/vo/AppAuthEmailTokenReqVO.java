package cn.iocoder.yudao.module.member.controller.app.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotBlank;

@Schema(description = "用户 APP - 邮箱一次性 token Request VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppAuthEmailTokenReqVO {

    @Schema(description = "邮箱一次性 token", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "token 不能为空")
    @Length(max = 128, message = "token 长度不能超过 128 个字符")
    private String token;

}
