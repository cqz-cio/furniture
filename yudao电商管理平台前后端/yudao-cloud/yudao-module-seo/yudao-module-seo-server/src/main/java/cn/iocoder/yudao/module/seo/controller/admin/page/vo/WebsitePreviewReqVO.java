package cn.iocoder.yudao.module.seo.controller.admin.page.vo;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true)
public class WebsitePreviewReqVO extends WebsitePageKeyReqVO {
    @Positive private Integer pageVersion;
    @Positive private Integer navigationVersion;
    @Positive private Long articleId;
    @Positive private Integer articleVersion;
}
