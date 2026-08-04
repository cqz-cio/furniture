package cn.iocoder.yudao.module.seo.controller.admin.navigation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "管理后台 - 官网导航预览凭证 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebsiteNavigationPreviewTicketRespVO {

    private String previewUrl;
    private Integer expiresInSeconds;

}
