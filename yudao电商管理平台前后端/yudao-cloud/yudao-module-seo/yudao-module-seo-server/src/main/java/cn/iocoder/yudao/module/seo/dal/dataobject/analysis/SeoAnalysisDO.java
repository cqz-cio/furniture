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

import java.util.Map;

@TableName(value = "seo_analysis", autoResultMap = true)
@KeySequence("seo_analysis_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SeoAnalysisDO extends TenantBaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long siteId;
    private String sourceType;
    private Long sourceId;
    private String entityType;
    private Long entityId;
    private String locale;
    private String focusKeyphrase;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> inputSnapshot;

    private String contentHash;
    private String idempotencyKey;
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

}
