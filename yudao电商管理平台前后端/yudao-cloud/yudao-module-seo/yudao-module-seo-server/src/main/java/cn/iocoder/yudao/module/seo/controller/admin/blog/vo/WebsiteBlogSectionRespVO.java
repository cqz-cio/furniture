package cn.iocoder.yudao.module.seo.controller.admin.blog.vo;

import lombok.Data;

import java.util.List;

@Data
public class WebsiteBlogSectionRespVO {

    private String id;
    private String number;
    private String title;
    private List<String> paragraphs;

}
