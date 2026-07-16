package cn.iocoder.yudao.module.trade.service.fulfillment.command;

import cn.iocoder.yudao.module.trade.enums.fulfillment.TrackingEventSourceEnum;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.dto.ProviderTrackingEvent;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;

@Data
@Accessors(chain = true)
public class ApplyTrackingEventCommand {

    private Long tenantId;
    private Long shipmentId;
    private Long packageId;
    private Long shipmentLegId;
    private Long providerId;
    private ProviderTrackingEvent providerEvent;
    private Instant receivedAt;
    private TrackingEventSourceEnum source;
    private String replayMappingVersion;
}
