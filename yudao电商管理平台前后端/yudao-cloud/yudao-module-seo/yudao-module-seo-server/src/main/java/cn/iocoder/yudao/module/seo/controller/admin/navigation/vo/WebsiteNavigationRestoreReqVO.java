package cn.iocoder.yudao.module.seo.controller.admin.navigation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 从历史版本恢复官网导航草稿 Request VO")
@Data
public class WebsiteNavigationRestoreReqVO {

    @NotNull(message = "草稿编号不能为空")
    private Long draftRevisionId;

    @NotNull(message = "草稿版本不能为空")
    private Integer draftVersion;

    @NotNull(message = "历史版本编号不能为空")
    private Long sourceRevisionId;

}
