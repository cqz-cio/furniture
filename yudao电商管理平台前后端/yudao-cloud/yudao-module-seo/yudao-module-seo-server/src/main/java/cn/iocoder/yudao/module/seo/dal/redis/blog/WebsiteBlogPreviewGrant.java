package cn.iocoder.yudao.module.seo.dal.redis.blog;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebsiteBlogPreviewGrant {

    private Long tenantId;
    private Long siteId;
    private String locale;
    private Long articleId;
    private Integer version;
    private String previewOrigin;

}
