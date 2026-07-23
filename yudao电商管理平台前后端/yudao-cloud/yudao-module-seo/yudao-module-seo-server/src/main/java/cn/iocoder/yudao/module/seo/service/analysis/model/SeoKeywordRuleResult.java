package cn.iocoder.yudao.module.seo.service.analysis.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.Map;

@Value
@Builder
public class SeoKeywordRuleResult {

    String ruleCode;
    String dimension;
    String severity;
    String status;
    BigDecimal score;
    BigDecimal maxScore;
    String contentLocation;
    Map<String, Object> evidence;
    String reason;
    String recommendation;
    BigDecimal recoverableScore;
    int sort;

}
