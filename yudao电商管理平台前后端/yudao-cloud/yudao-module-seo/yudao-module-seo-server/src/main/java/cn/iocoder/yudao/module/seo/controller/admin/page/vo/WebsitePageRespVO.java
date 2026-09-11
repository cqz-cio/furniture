package cn.iocoder.yudao.module.seo.controller.admin.page.vo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class WebsitePageRespVO {
    private Long siteId;
    private String pageKey;
    private String locale;
    private Integer version;
    private Integer publishedVersion;
    private JsonNode content;
}
