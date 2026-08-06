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

@TableName("website_blog_article")
@KeySequence("website_blog_article_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class WebsiteBlogArticleDO extends TenantBaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long siteId;
    private String locale;
    private String slug;
    private String legacyPath;
    private String title;
    private String titleLinesJson;
    private String category;
    private String label;
    private String summary;
    private String coverImageUrl;
    private String coverImageAlt;
    private String heroImageUrl;
    private String sectionsJson;
    private String status;
    private Boolean visible;
    private LocalDateTime publishedAt;
    private Integer sortOrder;
    private String seoTitle;
    private String seoDescription;
    private Integer version;
    private Integer publishedVersion;
    private String publishedSlug;
    private String publishedPayloadJson;
    private LocalDateTime lastPublishedTime;
    private String publishedBy;

}
