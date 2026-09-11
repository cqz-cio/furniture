package cn.iocoder.yudao.module.seo.dal.dataobject.page;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

@TableName("website_page")
@Data
@EqualsAndHashCode(callSuper = true)
public class WebsitePageDO extends TenantBaseDO {
    @TableId(type = IdType.AUTO) private Long id;
    private Long siteId;
    private String pageKey;
    private String locale;
    private Integer schemaVersion;
    private String draftJson;
    private Integer draftVersion;
    private Long publishedRevisionId;
}
