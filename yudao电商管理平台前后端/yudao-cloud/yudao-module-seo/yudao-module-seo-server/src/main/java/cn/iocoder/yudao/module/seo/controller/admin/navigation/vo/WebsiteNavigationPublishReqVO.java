package cn.iocoder.yudao.module.seo.controller.admin.navigation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 官网导航发布 Request VO")
@Data
public class WebsiteNavigationPublishReqVO {

    @NotNull
    private Long revisionId;

    @NotNull
    private Integer version;

}
