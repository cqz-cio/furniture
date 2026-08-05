package cn.iocoder.yudao.module.seo.controller.admin.navigation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 官网导航草稿 Response VO")
@Data
public class WebsiteNavigationDraftRespVO {

    private Long revisionId;
    private Long siteId;
    private String locale;
    private String navigationTemplate;
    private Integer revisionNo;
    private Integer version;
    private String status;
    private Integer publishedVersion;
    private Integer publishedRevisionNo;
    private LocalDateTime lastPublishedTime;
    private String lastPublishedBy;
    private List<WebsiteNavigationItemRespVO> items;
    private List<WebsiteNavigationItemRespVO> publishedItems;
    private List<WebsiteNavigationCategoryOptionRespVO> categoryOptions;
    private List<WebsiteNavigationTargetOptionRespVO> targetOptions;

}
