package cn.iocoder.yudao.module.seo.controller.admin.navigation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 官网导航商品分类选项 Response VO")
@Data
public class WebsiteNavigationCategoryOptionRespVO {

    private Long id;
    private String name;
    private Integer sort;
    private Long publishedProductCount;
    private Boolean selected;

}
