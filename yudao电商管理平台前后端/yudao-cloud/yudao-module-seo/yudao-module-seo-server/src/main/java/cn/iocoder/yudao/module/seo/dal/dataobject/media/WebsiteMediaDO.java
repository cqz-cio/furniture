package cn.iocoder.yudao.module.seo.dal.dataobject.media;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
@TableName("website_media") @Data @EqualsAndHashCode(callSuper = true)
public class WebsiteMediaDO extends TenantBaseDO {
    @TableId(type = IdType.AUTO) private Long id;
    private String name;
    private String alt;
    private String kind;
    private String mimeType;
    private Long size;
    private String url;
    private Integer width;
    private Integer height;
    private Boolean archived;
}
