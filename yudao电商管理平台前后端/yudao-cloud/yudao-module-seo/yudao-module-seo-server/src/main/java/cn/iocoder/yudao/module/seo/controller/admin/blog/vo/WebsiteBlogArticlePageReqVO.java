package cn.iocoder.yudao.module.seo.controller.admin.blog.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 企业日志分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class WebsiteBlogArticlePageReqVO extends PageParam {

    @NotNull
    private Long siteId;

    @Size(max = 32)
    private String locale = "en";

    private String status;

    @Size(max = 160)
    private String keyword;

}
