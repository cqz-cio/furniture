package cn.iocoder.yudao.module.seo.controller.admin.config.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - SEO 站点配置 Response VO")
@Data
public class SeoSiteConfigRespVO {

    @Schema(description = "配置编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(description = "站点编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long siteId;
    @Schema(description = "站点名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String siteName;
    @Schema(description = "站点地址", requiredMode = Schema.RequiredMode.REQUIRED)
    private String siteUrl;
    @Schema(description = "默认标题后缀", requiredMode = Schema.RequiredMode.REQUIRED)
    private String defaultTitleSuffix;
    @Schema(description = "默认描述", requiredMode = Schema.RequiredMode.REQUIRED)
    private String defaultDescription;
    @Schema(description = "默认 Robots 指令", requiredMode = Schema.RequiredMode.REQUIRED)
    private String defaultRobots;
    @Schema(description = "默认 Open Graph 图片", requiredMode = Schema.RequiredMode.REQUIRED)
    private String defaultOgImage;
    @Schema(description = "默认语言", requiredMode = Schema.RequiredMode.REQUIRED)
    private String defaultLocale;
    @Schema(description = "导航模板", requiredMode = Schema.RequiredMode.REQUIRED)
    private String navigationTemplate;
    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;
    @Schema(description = "更新时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime updateTime;

}
