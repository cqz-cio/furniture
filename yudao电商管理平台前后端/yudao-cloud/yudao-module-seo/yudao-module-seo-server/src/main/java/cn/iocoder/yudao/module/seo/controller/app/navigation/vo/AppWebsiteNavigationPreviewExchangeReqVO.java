package cn.iocoder.yudao.module.seo.controller.app.navigation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Schema(description = "用户 App - 官网导航预览凭证兑换 Request VO")
@Data
public class AppWebsiteNavigationPreviewExchangeReqVO {

    @NotBlank
    @Pattern(regexp = "^pv_[A-Za-z0-9_-]{43}$")
    private String ticket;

}
