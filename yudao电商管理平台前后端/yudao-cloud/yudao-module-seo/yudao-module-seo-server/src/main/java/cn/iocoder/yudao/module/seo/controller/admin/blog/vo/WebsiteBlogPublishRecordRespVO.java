package cn.iocoder.yudao.module.seo.controller.admin.blog.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WebsiteBlogPublishRecordRespVO {

    private Long id;
    private Integer publishedVersion;
    private String slug;
    private String title;
    private LocalDateTime publishedAt;
    private String publishedBy;
    private LocalDateTime createTime;

}
