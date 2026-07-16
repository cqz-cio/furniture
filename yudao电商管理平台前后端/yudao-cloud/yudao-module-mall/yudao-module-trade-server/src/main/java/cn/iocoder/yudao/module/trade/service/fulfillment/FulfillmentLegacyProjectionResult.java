package cn.iocoder.yudao.module.trade.service.fulfillment;

import cn.iocoder.yudao.module.trade.framework.delivery.core.client.dto.ExpressTrackRespDTO;

import java.util.List;
import java.util.Objects;

public record FulfillmentLegacyProjectionResult(Mode mode, List<ExpressTrackRespDTO> events) {

    public enum Mode {
        FALLBACK,
        AUTHORITATIVE_EMPTY,
        AUTHORITATIVE_EVENTS
    }

    public FulfillmentLegacyProjectionResult {
        Objects.requireNonNull(mode, "mode");
        events = List.copyOf(Objects.requireNonNull(events, "events"));
    }

    public static FulfillmentLegacyProjectionResult fallback() {
        return new FulfillmentLegacyProjectionResult(Mode.FALLBACK, List.of());
    }

    public static FulfillmentLegacyProjectionResult authoritative(List<ExpressTrackRespDTO> events) {
        List<ExpressTrackRespDTO> copy = List.copyOf(Objects.requireNonNull(events, "events"));
        return new FulfillmentLegacyProjectionResult(copy.isEmpty()
                ? Mode.AUTHORITATIVE_EMPTY : Mode.AUTHORITATIVE_EVENTS, copy);
    }

}
