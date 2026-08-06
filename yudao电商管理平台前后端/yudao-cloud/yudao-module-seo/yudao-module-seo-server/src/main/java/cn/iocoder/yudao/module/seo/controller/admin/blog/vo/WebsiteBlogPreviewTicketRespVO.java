package cn.iocoder.yudao.module.seo.controller.admin.blog.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebsiteBlogPreviewTicketRespVO {

    private String previewUrl;
    private Integer expiresInSeconds;

}
