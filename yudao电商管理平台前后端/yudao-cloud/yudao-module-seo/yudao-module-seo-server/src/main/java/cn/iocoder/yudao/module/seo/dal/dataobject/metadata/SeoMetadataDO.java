package cn.iocoder.yudao.module.seo.dal.dataobject.metadata;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;

@TableName(value = "seo_metadata", autoResultMap = true)
@KeySequence("seo_metadata_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SeoMetadataDO extends TenantBaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long siteId;
    private String entityType;
    private Long entityId;
    private String locale;
    private String seoTitle;
    private String metaDescription;
    private String focusKeyphrase;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> relatedKeyphrases;

    private String canonicalUrl;
    private Boolean robotsIndex;
    private Boolean robotsFollow;
    private String ogTitle;
    private String ogDescription;
    private String ogImage;
    private String schemaType;
    private String publishStatus;
    private Integer version;
    private LocalDateTime publishedTime;
    private Long latestAnalysisId;

}
