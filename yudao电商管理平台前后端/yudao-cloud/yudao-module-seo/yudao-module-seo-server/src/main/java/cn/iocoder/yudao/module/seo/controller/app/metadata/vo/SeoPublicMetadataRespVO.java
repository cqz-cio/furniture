package cn.iocoder.yudao.module.seo.controller.app.metadata.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "用户 App - SEO 公开元数据 Response VO")
@Data
public class SeoPublicMetadataRespVO {

    private String title;
    private String description;
    private String canonicalUrl;
    private Boolean robotsIndex;
    private Boolean robotsFollow;
    private String ogTitle;
    private String ogDescription;
    private String ogImage;
    private String schemaType;
    private String locale;
    private Integer version;

}
