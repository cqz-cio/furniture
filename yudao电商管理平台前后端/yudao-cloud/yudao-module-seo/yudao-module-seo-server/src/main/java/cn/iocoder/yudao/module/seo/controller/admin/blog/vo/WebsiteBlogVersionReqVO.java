package cn.iocoder.yudao.module.seo.controller.admin.blog.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WebsiteBlogVersionReqVO {

    @NotNull
    private Long id;

    @NotNull
    private Integer version;

}
