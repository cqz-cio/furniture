package cn.iocoder.yudao.module.seo.controller.admin.navigation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 官网导航草稿保存 Request VO")
@Data
public class WebsiteNavigationDraftSaveReqVO {

    @NotNull
    private Long revisionId;

    @NotNull
    private Long siteId;

    @NotNull
    private Integer version;

    @Size(max = 32)
    private String locale;

    @Valid
    @NotEmpty
    private List<WebsiteNavigationItemSaveReqVO> items;

}
