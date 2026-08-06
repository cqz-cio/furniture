package cn.iocoder.yudao.module.seo.controller.app.blog.vo;

import lombok.Data;

import java.util.List;

@Data
public class AppWebsiteBlogSectionRespVO {

    private String id;
    private String number;
    private String title;
    private List<String> paragraphs;

}
