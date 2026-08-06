package cn.iocoder.yudao.module.seo.controller.app.blog.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppWebsiteBlogPreviewSessionRespVO {

    private String session;
    private Integer expiresInSeconds;

}
