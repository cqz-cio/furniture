package cn.iocoder.yudao.module.seo.controller.app.navigation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "用户 App - 官网导航项 Response VO")
@Data
public class AppWebsiteNavigationItemRespVO {

    private String key;
    private String label;
    private String href;
    private String itemType;
    private Long categoryId;
    private Long publishedProductCount;
    private List<AppWebsiteNavigationItemRespVO> children;

}
