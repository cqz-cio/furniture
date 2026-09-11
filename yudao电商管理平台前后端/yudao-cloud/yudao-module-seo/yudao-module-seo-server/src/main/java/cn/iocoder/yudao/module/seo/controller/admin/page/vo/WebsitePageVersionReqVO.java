package cn.iocoder.yudao.module.seo.controller.admin.page.vo;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WebsitePageVersionReqVO extends WebsitePageKeyReqVO {
    @NotNull @Min(1) private Integer expectedVersion;
}
