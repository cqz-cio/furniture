package cn.iocoder.yudao.module.trade.framework.fulfillment.core.impl;

import cn.iocoder.yudao.module.trade.framework.fulfillment.core.LogisticsProviderClient;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.ProviderCapability;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.dto.ProviderTrackingEvent;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.dto.TrackingQuery;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.dto.TrackingRegistrationCommand;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.dto.TrackingRegistrationResult;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.dto.TrackingSnapshot;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class MockLogisticsProviderClient implements LogisticsProviderClient {

    private static final String PROVIDER_CODE = "mock";
    private static final Set<ProviderCapability> CAPABILITIES = Set.of(ProviderCapability.TRACKING_QUERY);

    private final Map<TrackingKey, List<ProviderTrackingEvent>> eventsByTracking;

    public MockLogisticsProviderClient() {
        this(Map.of());
    }

    public MockLogisticsProviderClient(Map<TrackingQuery, List<ProviderTrackingEvent>> fixtures) {
        this.eventsByTracking = fixtures.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                entry -> TrackingKey.from(entry.getKey()),
                entry -> List.copyOf(entry.getValue())
        ));
    }

    @Override
    public String getProviderCode() {
        return PROVIDER_CODE;
    }

    @Override
    public Set<ProviderCapability> getCapabilities() {
        return CAPABILITIES;
    }

    @Override
    public TrackingRegistrationResult registerTracking(TrackingRegistrationCommand command) {
        Objects.requireNonNull(command, "command");
        return new TrackingRegistrationResult()
                .setCarrierCode(command.getCarrierCode())
                .setTrackingNumber(command.getTrackingNumber())
                .setRegistered(true);
    }

    @Override
    public TrackingSnapshot queryTracking(TrackingQuery query) {
        Objects.requireNonNull(query, "query");
        List<ProviderTrackingEvent> events = eventsByTracking.getOrDefault(TrackingKey.from(query), List.of());
        return new TrackingSnapshot()
                .setCarrierCode(query.getCarrierCode())
                .setTrackingNumber(query.getTrackingNumber())
                .setEvents(events);
    }

    private record TrackingKey(String carrierCode, String trackingNumber) {

        private static TrackingKey from(TrackingQuery query) {
            return new TrackingKey(query.getCarrierCode(), query.getTrackingNumber());
        }

    }

}
