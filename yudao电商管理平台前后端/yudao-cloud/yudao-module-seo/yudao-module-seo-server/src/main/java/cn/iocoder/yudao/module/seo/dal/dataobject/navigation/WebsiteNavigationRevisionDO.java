package cn.iocoder.yudao.module.seo.dal.dataobject.navigation;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@TableName("website_navigation_revision")
@KeySequence("website_navigation_revision_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class WebsiteNavigationRevisionDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long siteId;
    private String locale;
    private Integer revisionNo;
    private String status;
    private Integer version;
    private LocalDateTime publishedTime;
    private String publishedBy;

}
