package cn.iocoder.yudao.module.statistics.controller.app.dashboard.vo;
import lombok.Data;
import jakarta.validation.constraints.*;
@Data
public class AppBehaviorEventTrackReqVO {
    @NotBlank @Size(max=64) private String eventId;
    @NotNull private Integer eventType;
    private Long spuId;
    private Long skuId;
    @Min(1) @Max(999) private Integer quantity;
    @NotBlank @Size(max=255) private String pagePath;
    @Size(max=255) private String referrerHost;
    private Integer deviceType;
    @Size(max=32) private String channel;
    @Size(max=100) private String utmSource;
    @Size(max=100) private String utmMedium;
    @Size(max=100) private String utmCampaign;
}
