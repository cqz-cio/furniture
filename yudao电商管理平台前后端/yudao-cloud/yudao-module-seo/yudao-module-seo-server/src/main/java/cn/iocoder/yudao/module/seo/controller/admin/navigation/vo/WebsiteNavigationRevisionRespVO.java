package cn.iocoder.yudao.module.seo.controller.admin.navigation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 官网导航发布历史 Response VO")
@Data
public class WebsiteNavigationRevisionRespVO {

    private Long revisionId;
    private Integer revisionNo;
    private Integer version;
    private String status;
    private LocalDateTime publishedTime;
    private String publishedBy;
    private LocalDateTime updateTime;

}
