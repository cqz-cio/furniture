package cn.iocoder.yudao.module.seo.controller.app.navigation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "用户 App - 官网导航预览会话 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppWebsiteNavigationPreviewSessionRespVO {

    private String session;
    private Integer expiresInSeconds;

}
