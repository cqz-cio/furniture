package cn.iocoder.yudao.module.statistics.controller.app.dashboard.vo;
import lombok.Data;
import javax.validation.constraints.*;
@Data
public class AppBehaviorEventTrackReqVO {
    @NotBlank @Size(max=64) private String eventId;
    @NotNull private Integer eventType;
    private Long spuId;
    private Long skuId;
    @NotBlank @Size(max=255) private String pagePath;
    @Size(max=255) private String referrerHost;
    private Integer deviceType;
    @NotNull private Boolean consentGranted;
}
