package cn.iocoder.yudao.module.seo.controller.admin.code.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WebsiteCodeVersionReqVO {
    @NotNull @Min(1) private Long siteId;
    @NotNull @Min(0) private Integer version;
}
