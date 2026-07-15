package cn.iocoder.yudao.module.trade.framework.fulfillment.core.dto;

import java.time.Instant;

public record ProviderTrackingEvent(
        String externalEventId,
        String providerStatus,
        Instant occurredAt,
        String occurredTimezone,
        String location,
        String description,
        String rawPayloadRef) {

    @Override
    public String toString() {
        return "ProviderTrackingEvent[externalEventId=<redacted>, providerStatus=<redacted>, occurredAt="
                + occurredAt + ", occurredTimezone=" + occurredTimezone
                + ", location=<redacted>, description=<redacted>, rawPayloadRef=<redacted>]";
    }

}
