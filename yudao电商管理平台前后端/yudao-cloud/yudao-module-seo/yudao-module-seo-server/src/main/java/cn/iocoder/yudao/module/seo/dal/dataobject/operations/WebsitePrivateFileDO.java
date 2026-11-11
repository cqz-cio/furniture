package cn.iocoder.yudao.module.seo.dal.dataobject.operations;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

/** Private PDF bytes: intentionally not registered with publicly downloadable infra files. */
@TableName("website_private_file")
@Data @EqualsAndHashCode(callSuper = true)
public class WebsitePrivateFileDO extends TenantBaseDO {
    @TableId(type = IdType.AUTO) private Long id;
    private Long siteId;
    private String name;
    private Long size;
    private String sha256;
    @TableField(select = false) private byte[] content;
}
