package cn.iocoder.yudao.module.seo.controller.admin.metadata.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - SEO 元数据 Response VO")
@Data
public class SeoMetadataRespVO {

    private Long id;
    private Long siteId;
    private String entityType;
    private Long entityId;
    private String locale;
    private String seoTitle;
    private String metaDescription;
    private String focusKeyphrase;
    private List<String> relatedKeyphrases;
    private String canonicalUrl;
    private Boolean robotsIndex;
    private Boolean robotsFollow;
    private String ogTitle;
    private String ogDescription;
    private String ogImage;
    private String schemaType;
    private String publishStatus;
    private Integer version;
    private LocalDateTime publishedTime;
    private Long latestAnalysisId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
