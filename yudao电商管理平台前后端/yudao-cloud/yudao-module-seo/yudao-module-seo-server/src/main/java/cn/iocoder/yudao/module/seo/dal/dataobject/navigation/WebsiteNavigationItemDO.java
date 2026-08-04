package cn.iocoder.yudao.module.seo.dal.dataobject.navigation;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@TableName("website_navigation_item")
@KeySequence("website_navigation_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class WebsiteNavigationItemDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long revisionId;
    private String itemKey;
    private String parentItemKey;
    private String itemType;
    private String label;
    private String pageKey;
    private Long categoryId;
    private Integer sort;
    private Boolean visible;
    private String openMode;

}
