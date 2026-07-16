package cn.iocoder.yudao.module.seo.controller.admin.metadata.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Schema(description = "管理后台 - SEO 元数据保存 Request VO")
@Data
@Accessors(chain = true)
public class SeoMetadataSaveReqVO {

    private Long id;

    @NotNull
    private Long siteId;

    @NotBlank
    private String entityType;

    @NotNull
    private Long entityId;

    @NotBlank
    private String locale;

    @Size(max = 255)
    private String seoTitle;

    @Size(max = 500)
    private String metaDescription;

    @Size(max = 255)
    private String focusKeyphrase;

    private List<@Size(max = 255) String> relatedKeyphrases;

    @Size(max = 1024)
    private String canonicalUrl;

    private Boolean robotsIndex;
    private Boolean robotsFollow;

    @Size(max = 255)
    private String ogTitle;

    @Size(max = 500)
    private String ogDescription;

    @Size(max = 1024)
    private String ogImage;

    @Size(max = 64)
    private String schemaType;

    private Integer version;

}
