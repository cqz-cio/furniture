package cn.iocoder.yudao.module.seo.controller.admin.analysis.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Schema(description = "管理后台 - 上传文档并运行 SEO 分析 Request VO")
@Data
public class SeoDocumentAnalysisRunReqVO {

    @NotNull
    private Long siteId;

    @NotBlank
    private String entityType;

    @NotNull
    private Long entityId;

    @NotBlank
    private String locale;

    @NotBlank
    @Size(max = 255)
    private String focusKeyphrase;

    @Size(max = 50)
    private List<@NotBlank @Size(max = 255) String> relatedKeyphrases;

    @NotBlank
    @Size(max = 128)
    private String idempotencyKey;

    @NotNull
    private MultipartFile file;

}
