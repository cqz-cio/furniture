package cn.iocoder.yudao.module.seo.controller.admin.navigation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 官网导航项 Response VO")
@Data
public class WebsiteNavigationItemRespVO {

    private String itemKey;
    private String itemType;
    private String pageKey;
    private Long categoryId;
    private String label;
    private Integer sort;
    private Boolean visible;
    private Boolean available;
    private Long publishedProductCount;

}
