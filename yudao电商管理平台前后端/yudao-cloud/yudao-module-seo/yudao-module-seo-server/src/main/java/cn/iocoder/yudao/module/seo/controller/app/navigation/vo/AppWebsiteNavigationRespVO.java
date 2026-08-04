package cn.iocoder.yudao.module.seo.controller.app.navigation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "用户 App - 官网已发布导航 Response VO")
@Data
public class AppWebsiteNavigationRespVO {

    private Long siteId;
    private String locale;
    private Long revisionId;
    private Integer version;
    private List<AppWebsiteNavigationItemRespVO> items;

}
