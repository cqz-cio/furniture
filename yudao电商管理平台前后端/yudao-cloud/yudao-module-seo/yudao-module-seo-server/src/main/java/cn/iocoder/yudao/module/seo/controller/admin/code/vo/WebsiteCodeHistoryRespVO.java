package cn.iocoder.yudao.module.seo.controller.admin.code.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class WebsiteCodeHistoryRespVO {
    private Long id;
    private Integer version;
    private String action;
    private String creator;
    private LocalDateTime createTime;
    private WebsiteCodeContent content;
}
