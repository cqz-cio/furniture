package cn.iocoder.yudao.module.seo.dal.dataobject.blog;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@TableName("website_blog_publish_record")
@KeySequence("website_blog_publish_record_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class WebsiteBlogPublishRecordDO extends TenantBaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleId;
    private Integer publishedVersion;
    private String slug;
    private String title;
    private LocalDateTime publishedAt;
    private String publishedBy;
    private String snapshotJson;

}
