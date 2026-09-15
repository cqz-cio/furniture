package cn.iocoder.yudao.module.seo.controller.admin.media.vo;
import java.time.LocalDateTime;
import lombok.Data;
@Data
public class WebsiteMediaRespVO {
    private Long id;
    private String name;
    private String alt;
    private String kind;
    private String mimeType;
    private Long size;
    private String url;
    private Integer width;
    private Integer height;
    private Boolean archived;
    private LocalDateTime createTime;
}
