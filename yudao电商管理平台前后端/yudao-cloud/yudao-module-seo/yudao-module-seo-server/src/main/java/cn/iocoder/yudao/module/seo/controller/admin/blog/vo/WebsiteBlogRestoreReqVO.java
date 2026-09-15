package cn.iocoder.yudao.module.seo.controller.admin.blog.vo;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true)
public class WebsiteBlogRestoreReqVO extends WebsiteBlogVersionReqVO {
    @NotNull @Positive private Long recordId;
}
