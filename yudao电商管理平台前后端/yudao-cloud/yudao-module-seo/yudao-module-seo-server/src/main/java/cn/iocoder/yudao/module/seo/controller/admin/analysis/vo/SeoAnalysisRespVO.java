package cn.iocoder.yudao.module.seo.controller.admin.analysis.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - SEO 分析详情 Response VO")
@Data
public class SeoAnalysisRespVO {

    private Long id;
    private Long siteId;
    private String sourceType;
    private Long sourceId;
    private String entityType;
    private Long entityId;
    private String locale;
    private String focusKeyphrase;
    private Long previousAnalysisId;
    private Integer overallRelevancePercent;
    private Integer confidencePercent;
    private Integer totalScore;
    private String engineVersion;
    private String ruleProfileVersion;
    private String dictionaryVersion;
    private String semanticModelVersion;
    private String analysisStatus;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<SeoKeywordAnalysisRespVO> keywords;

}
