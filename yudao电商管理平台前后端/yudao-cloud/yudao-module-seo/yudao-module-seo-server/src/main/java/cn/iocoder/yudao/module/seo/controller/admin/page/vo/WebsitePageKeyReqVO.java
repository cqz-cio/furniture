package cn.iocoder.yudao.module.seo.controller.admin.page.vo;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class WebsitePageKeyReqVO {
    @NotNull @Positive private Long siteId;
    @NotBlank @Pattern(regexp = "home") private String pageKey;
    @NotBlank @Pattern(regexp = "zh-CN|en") private String locale;
}
