package cn.iocoder.yudao.module.seo.service.analysis.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class SeoKeywordEvaluation {

    String keyword;
    String normalizedKeyword;
    String keywordType;
    int sort;
    Integer keyPositionPercent;
    Integer lexicalMatchPercent;
    Integer semanticPercent;
    Integer distributionPercent;
    Integer intentCoveragePercent;
    Integer relevancePercent;
    int confidencePercent;
    String grade;
    String analysisStatus;
    int exactMatchCount;
    int variantMatchCount;
    List<String> matchedLocations;
    String dictionaryVersion;
    String semanticModelVersion;
    List<SeoKeywordRuleResult> items;

}
