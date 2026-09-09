package cn.iocoder.yudao.module.seo.controller.admin.code.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class WebsiteCodeRespVO {
    private Long siteId;
    private String siteName;
    private String siteUrl;
    private Integer version;
    private Integer publishedVersion;
    private LocalDateTime updateTime;
    private LocalDateTime publishedTime;
    private WebsiteCodeContent content;
}
