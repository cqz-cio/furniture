package cn.iocoder.yudao.module.seo.controller.admin.code.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WebsiteCodeRestoreReqVO extends WebsiteCodeVersionReqVO {
    @NotNull @Min(1) private Long historyId;
}
