package cn.iocoder.yudao.module.trade.framework.fulfillment.core.dto;

import java.time.LocalDateTime;

public record ProviderTrackingEvent(
        String providerStatus,
        LocalDateTime occurredAt,
        String location,
        String description) {
}
