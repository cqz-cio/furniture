package cn.iocoder.yudao.module.seo.controller.admin.analysis.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - SEO 文档解析预览 Response VO")
@Data
public class SeoParsedDocumentRespVO {

    private String filename;
    private String extension;
    private String contentType;
    private Long fileSize;
    private Integer extractedCharacters;
    private Boolean truncated;
    private String content;

}
