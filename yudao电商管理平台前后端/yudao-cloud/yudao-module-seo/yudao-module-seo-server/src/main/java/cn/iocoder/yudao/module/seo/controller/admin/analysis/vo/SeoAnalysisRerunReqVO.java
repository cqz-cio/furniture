package cn.iocoder.yudao.module.seo.controller.admin.analysis.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 重新运行 SEO 分析 Request VO")
@Data
public class SeoAnalysisRerunReqVO {

    @NotBlank
    @Size(max = 128)
    private String idempotencyKey;

}
