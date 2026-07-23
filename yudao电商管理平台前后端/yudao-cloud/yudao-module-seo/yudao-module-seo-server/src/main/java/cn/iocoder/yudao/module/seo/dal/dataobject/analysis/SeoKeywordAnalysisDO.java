package cn.iocoder.yudao.module.seo.dal.dataobject.analysis;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;

@TableName(value = "seo_keyword_analysis", autoResultMap = true)
@KeySequence("seo_keyword_analysis_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SeoKeywordAnalysisDO extends TenantBaseDO {

    @TableId(type = IdType.AUTO)
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

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> matchedLocations;

    private String dictionaryVersion;
    private String semanticModelVersion;

}
