package cn.iocoder.yudao.module.seo.controller.admin.navigation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 官网导航项 Response VO")
@Data
public class WebsiteNavigationItemRespVO {

    private String itemKey;
    private String parentItemKey;
    private String itemType;
    private String pageKey;
    private String targetKey;
    private Long categoryId;
    private String label;
    private Integer sort;
    private Boolean visible;
    private String openMode;
    private String styleVariant;
    private Boolean available;
    private Long publishedProductCount;

}
