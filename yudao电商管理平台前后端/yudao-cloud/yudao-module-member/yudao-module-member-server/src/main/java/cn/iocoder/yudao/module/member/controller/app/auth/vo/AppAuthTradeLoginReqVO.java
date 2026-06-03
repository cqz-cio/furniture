package cn.iocoder.yudao.module.member.controller.app.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotEmpty;

@Schema(description = "用户 APP - Trade Program 登录 Request VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppAuthTradeLoginReqVO {

    @Schema(description = "Trade ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "RH-TRADE-10086")
    @NotEmpty(message = "Trade ID 不能为空")
    @Length(max = 64, message = "Trade ID 长度不能超过 64 位")
    private String tradeId;

    @Schema(description = "邮箱", requiredMode = Schema.RequiredMode.REQUIRED, example = "designer@example.com")
    @NotEmpty(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

}
