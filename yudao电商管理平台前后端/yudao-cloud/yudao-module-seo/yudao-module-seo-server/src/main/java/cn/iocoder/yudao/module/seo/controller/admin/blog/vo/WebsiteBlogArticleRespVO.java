package cn.iocoder.yudao.module.seo.controller.admin.blog.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class WebsiteBlogArticleRespVO {

    private Long id;
    private Long siteId;
    private String locale;
    private String slug;
    private String legacyPath;
    private String title;
    private List<String> titleLines;
    private String category;
    private String label;
    private String summary;
    private String coverImageUrl;
    private String coverImageAlt;
    private String heroImageUrl;
    private List<WebsiteBlogSectionRespVO> sections;
    private String status;
    private Boolean visible;
    private LocalDateTime publishedAt;
    private Integer sortOrder;
    private String seoTitle;
    private String seoDescription;
    private Integer version;
    private Integer publishedVersion;
    private Boolean hasUnpublishedChanges;
    private String readTime;
    private LocalDateTime lastPublishedTime;
    private String publishedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
