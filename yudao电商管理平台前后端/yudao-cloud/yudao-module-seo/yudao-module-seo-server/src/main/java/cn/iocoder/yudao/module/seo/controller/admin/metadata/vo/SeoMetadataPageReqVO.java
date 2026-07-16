package cn.iocoder.yudao.module.seo.controller.admin.metadata.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Schema(description = "管理后台 - SEO 元数据分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class SeoMetadataPageReqVO extends PageParam {

    private Long siteId;
    private String entityType;
    private Long entityId;
    private String locale;
    private String publishStatus;
    private String keyword;

}
