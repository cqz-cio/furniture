package cn.iocoder.yudao.module.seo.controller.admin.page.vo;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true)
public class WebsitePageRestoreReqVO extends WebsitePageVersionReqVO {
    @NotNull @Positive private Long revisionId;
}
