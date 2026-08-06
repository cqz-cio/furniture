package cn.iocoder.yudao.module.seo.controller.app.blog.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AppWebsiteBlogPreviewExchangeReqVO {

    @NotBlank
    @Pattern(regexp = "^bpv_[A-Za-z0-9_-]{43}$")
    private String ticket;

}
