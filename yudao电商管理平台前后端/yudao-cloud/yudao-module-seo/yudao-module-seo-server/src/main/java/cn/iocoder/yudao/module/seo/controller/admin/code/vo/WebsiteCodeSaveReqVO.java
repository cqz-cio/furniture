package cn.iocoder.yudao.module.seo.controller.admin.code.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WebsiteCodeSaveReqVO extends WebsiteCodeVersionReqVO {
    @Valid @NotNull private WebsiteCodeContent content;
}
