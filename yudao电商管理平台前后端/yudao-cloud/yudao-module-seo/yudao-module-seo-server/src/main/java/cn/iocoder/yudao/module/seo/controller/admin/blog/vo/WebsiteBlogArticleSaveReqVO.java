package cn.iocoder.yudao.module.seo.controller.admin.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 企业日志保存 Request VO")
@Data
public class WebsiteBlogArticleSaveReqVO {

    private Long id;
    private Integer version;

    @NotNull
    private Long siteId;

    @Size(max = 32)
    private String locale = "en";

    @NotBlank
    @Size(max = 140)
    @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$")
    private String slug;

    @Size(max = 220)
    private String legacyPath;

    @NotBlank
    @Size(max = 180)
    private String title;

    @Size(max = 3)
    private List<@Size(max = 100) String> titleLines;

    @NotBlank
    @Size(max = 80)
    private String category;

    @NotBlank
    @Size(max = 80)
    private String label;

    @NotBlank
    @Size(max = 600)
    private String summary;

    @NotBlank
    @Size(max = 500)
    private String coverImageUrl;

    @NotBlank
    @Size(max = 240)
    private String coverImageAlt;

    @Size(max = 500)
    private String heroImageUrl;

    @Valid
    @Size(max = 20)
    private List<WebsiteBlogSectionSaveReqVO> sections;

    private Boolean visible = true;
    private LocalDateTime publishedAt;
    private Integer sortOrder = 0;

    @Size(max = 180)
    private String seoTitle;

    @Size(max = 320)
    private String seoDescription;

}
