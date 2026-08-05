package cn.iocoder.yudao.module.seo.controller.admin.config.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - SEO 站点配置保存 Request VO")
@Data
public class SeoSiteConfigSaveReqVO {

    @Schema(description = "站点编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull
    private Long siteId;

    @Schema(description = "站点名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "官方商城")
    @NotBlank
    @Size(max = 128)
    private String siteName;

    @Schema(description = "站点地址", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://shop.example.com")
    @NotBlank
    @Size(max = 512)
    private String siteUrl;

    @Schema(description = "默认标题后缀")
    @Size(max = 128)
    private String defaultTitleSuffix;

    @Schema(description = "默认描述")
    @Size(max = 500)
    private String defaultDescription;

    @Schema(description = "默认 Robots 指令", example = "index,follow")
    @Size(max = 64)
    private String defaultRobots;

    @Schema(description = "默认 Open Graph 图片")
    @Size(max = 1024)
    private String defaultOgImage;

    @Schema(description = "默认语言", example = "zh-CN")
    @Size(max = 32)
    private String defaultLocale;

    @Schema(description = "导航模板：VANZ_B2B 或 OAKVED_B2C")
    @Size(max = 32)
    private String navigationTemplate;

}
