package cn.iocoder.yudao.module.seo.dal.dataobject.config;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@TableName("seo_site_config")
@KeySequence("seo_site_config_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SeoSiteConfigDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long siteId;
    private String siteName;
    private String siteUrl;
    private String defaultTitleSuffix;
    private String defaultDescription;
    private String defaultRobots;
    private String defaultOgImage;
    private String defaultLocale;

}
