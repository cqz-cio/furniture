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

import java.math.BigDecimal;
import java.util.Map;

@TableName(value = "seo_keyword_analysis_item", autoResultMap = true)
@KeySequence("seo_keyword_analysis_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SeoKeywordAnalysisItemDO extends TenantBaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long keywordAnalysisId;
    private String ruleCode;
    private String dimension;
    private String severity;
    private String status;
    private BigDecimal score;
    private BigDecimal maxScore;
    private String contentLocation;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> evidence;

    private String reason;
    private String recommendation;
    private BigDecimal recoverableScore;
    private Integer sort;

}
