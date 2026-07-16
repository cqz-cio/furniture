package cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo;

import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 脱敏物流轨迹事件 Response VO")
@Data
public class TrackingEventRespVO {

    private Long id;
    private Long packageId;
    private Long shipmentLegId;
    private ShipmentStatusEnum standardStatus;
    private String providerStatusNormalized;
    private String mappingVersion;
    private String transitionDecision;
    private String previousStatus;
    private String resultStatus;
    private String occurredTimezone;
    private String source;
    private Boolean mappingKnown;
    private LocalDateTime occurredAt;
    private LocalDateTime receivedAt;

}
