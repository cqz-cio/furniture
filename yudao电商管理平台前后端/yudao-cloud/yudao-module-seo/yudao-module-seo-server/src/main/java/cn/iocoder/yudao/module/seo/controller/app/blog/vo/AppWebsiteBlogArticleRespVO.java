package cn.iocoder.yudao.module.seo.controller.app.blog.vo;

import lombok.Data;

import java.util.List;

@Data
public class AppWebsiteBlogArticleRespVO {

    private Long id;
    private String slug;
    private String path;
    private String title;
    private List<String> titleLines;
    private String category;
    private String label;
    private String summary;
    private AppWebsiteBlogCoverImageRespVO coverImage;
    private String heroImage;
    private String publishedAt;
    private String displayDate;
    private String readTime;
    private Integer sortOrder;
    private List<AppWebsiteBlogSectionRespVO> sections;
    private String seoTitle;
    private String seoDescription;

}
