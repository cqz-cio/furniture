package cn.iocoder.yudao.module.seo.controller.admin.analysis.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Schema(description = "管理后台 - SEO 关键词规则证据 Response VO")
@Data
public class SeoKeywordRuleRespVO {

    private Long id;
    private String ruleCode;
    private String dimension;
    private String severity;
    private String status;
    private BigDecimal score;
    private BigDecimal maxScore;
    private String contentLocation;
    private Map<String, Object> evidence;
    private String reason;
    private String recommendation;
    private BigDecimal recoverableScore;
    private Integer sort;

}
