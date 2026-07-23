package cn.iocoder.yudao.module.seo.controller.admin.analysis.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - SEO 分析对比 Response VO")
@Data
public class SeoAnalysisCompareRespVO {

    private Long previousAnalysisId;
    private Long currentAnalysisId;
    private List<KeywordComparison> keywords;

    @Data
    public static class KeywordComparison {
        private String keywordType;
        private String keyword;
        private String normalizedKeyword;
        private Integer previousPercent;
        private Integer currentPercent;
        private Integer deltaPercent;
        private String changeType;
        private List<String> resolvedRuleCodes;
        private List<String> newRuleCodes;
    }

}
