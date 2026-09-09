package cn.iocoder.yudao.module.seo.dal.dataobject.code;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@TableName("website_code_config")
@KeySequence("website_code_config_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class WebsiteCodeConfigDO extends TenantBaseDO {
    @TableId private Long id;
    private Long siteId;
    private Integer version;
    private String draftJson;
    private String publishedJson;
    private Integer publishedVersion;
    private LocalDateTime publishedTime;
}
