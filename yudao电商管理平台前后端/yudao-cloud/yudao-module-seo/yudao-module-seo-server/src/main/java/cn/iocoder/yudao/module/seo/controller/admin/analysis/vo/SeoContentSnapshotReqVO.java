package cn.iocoder.yudao.module.seo.controller.admin.analysis.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - SEO 手工内容快照 Request VO")
@Data
public class SeoContentSnapshotReqVO {

    @Size(max = 255)
    private String seoTitle;

    @Size(max = 500)
    private String h1;

    @Size(max = 4000)
    private String introduction;

    @Size(max = 500)
    private String metaDescription;

    @Size(max = 1024)
    private String slug;

    @Size(max = 200000)
    private String body;

    @Size(max = 100)
    private List<@Size(max = 500) String> headings;

    @Size(max = 1000)
    private List<@Size(max = 10000) String> paragraphs;

    @Size(max = 200)
    private Map<@Size(max = 255) String, @Size(max = 4000) String> attributes;

    @Size(max = 200)
    private List<@Size(max = 500) String> imageAlts;

}
