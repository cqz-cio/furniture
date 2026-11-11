package cn.iocoder.yudao.module.seo.dal.dataobject.operations;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

@TableName("website_document")
@Data @EqualsAndHashCode(callSuper = true)
public class WebsiteDocumentDO extends TenantBaseDO {
    @TableId(type = IdType.AUTO) private Long id;
    private Long siteId;
    private Integer version;
    private String draftJson;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String publishedJson;
}
