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

@TableName(value = "seo_analysis_item", autoResultMap = true)
@KeySequence("seo_analysis_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SeoAnalysisItemDO extends TenantBaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long analysisId;
    private String ruleCode;
    private String category;
    private String status;
    private BigDecimal score;
    private BigDecimal maxScore;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> evidence;

    private String message;
    private String recommendation;
    private Integer sort;

}
