package cn.iocoder.yudao.module.trade.service.fulfillment.command;

import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.time.Instant;

@Data
@Accessors(chain = true)
public class ApplyManualTrackingEventCommand {

    private Long tenantId;
    private Long shipmentId;
    private Long packageId;
    private Long shipmentLegId;
    private ShipmentStatusEnum requestedStatus;
    private Instant occurredAt;
    private Integer expectedShipmentVersion;
    private Long operatorId;
    @ToString.Exclude
    private String reason;
    @ToString.Exclude
    private String requestTraceId;
}
