package cn.iocoder.yudao.module.seo.controller.admin.analysis.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - SEO 逐关键词分析 Response VO")
@Data
public class SeoKeywordAnalysisRespVO {

    private Long id;
    private Long analysisId;
    private String keywordType;
    private String keyword;
    private String normalizedKeyword;
    private Integer sort;
    private Integer keyPositionPercent;
    private Integer lexicalMatchPercent;
    private Integer semanticPercent;
    private Integer distributionPercent;
    private Integer intentCoveragePercent;
    private Integer relevancePercent;
    private Integer confidencePercent;
    private String grade;
    private String analysisStatus;
    private Integer exactMatchCount;
    private Integer variantMatchCount;
    private List<String> matchedLocations;
    private String dictionaryVersion;
    private String semanticModelVersion;
    private Integer suggestionCount;
    private List<SeoKeywordRuleRespVO> items;

}
