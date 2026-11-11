package cn.iocoder.yudao.module.seo.dal.dataobject.operations;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

/** Individually versioned operational records. Never a shared cross-tenant JSON store. */
@TableName("website_operation_record")
@Data @EqualsAndHashCode(callSuper = true)
public class WebsiteOperationRecordDO extends TenantBaseDO {
    @TableId(type = IdType.AUTO) private Long id;
    private Long siteId;
    private String kind;
    private String targetKind;
    private String targetKey;
    private Integer contentVersion;
    private String status;
    private Integer version;
    private String payloadJson;
}
