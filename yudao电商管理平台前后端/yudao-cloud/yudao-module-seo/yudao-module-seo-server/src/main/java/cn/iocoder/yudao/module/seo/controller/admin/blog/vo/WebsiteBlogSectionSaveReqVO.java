package cn.iocoder.yudao.module.seo.controller.admin.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 企业日志正文段落 Request VO")
@Data
public class WebsiteBlogSectionSaveReqVO {

    @Size(max = 120)
    private String id;

    @Size(max = 160)
    private String title;

    @Size(max = 50)
    private List<@Size(max = 4000) String> paragraphs;

}
