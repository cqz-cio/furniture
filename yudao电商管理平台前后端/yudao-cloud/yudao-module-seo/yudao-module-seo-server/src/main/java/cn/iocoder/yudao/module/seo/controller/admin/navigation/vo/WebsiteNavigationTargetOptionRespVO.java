package cn.iocoder.yudao.module.seo.controller.admin.navigation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "管理后台 - 官网导航安全目标选项 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebsiteNavigationTargetOptionRespVO {

    private String targetKey;
    private String itemType;
    private String label;
    private String href;

}
