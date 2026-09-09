package cn.iocoder.yudao.module.seo.dal.dataobject.code;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("website_code_history")
@KeySequence("website_code_history_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class WebsiteCodeHistoryDO extends TenantBaseDO {
    @TableId private Long id;
    private Long siteId;
    private Integer version;
    private String action;
    private String snapshotJson;
}
