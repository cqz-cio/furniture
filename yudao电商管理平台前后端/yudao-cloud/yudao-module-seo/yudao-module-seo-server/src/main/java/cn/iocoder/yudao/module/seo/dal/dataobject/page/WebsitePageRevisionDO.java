package cn.iocoder.yudao.module.seo.dal.dataobject.page;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

@TableName("website_page_revision")
@Data
@EqualsAndHashCode(callSuper = true)
public class WebsitePageRevisionDO extends TenantBaseDO {
    @TableId(type = IdType.AUTO) private Long id;
    private Long pageId;
    private Integer revision;
    private Integer schemaVersion;
    private String contentJson;
}
