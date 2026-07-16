package cn.iocoder.yudao.module.trade.service.fulfillment;

import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.TrackingStatusMappingDO;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.TrackingStatusMappingMapper;
import cn.iocoder.yudao.module.trade.enums.fulfillment.OrderFulfillmentStatusEnum;
import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.ApplyManualTrackingEventCommand;
import cn.iocoder.yudao.module.trade.service.fulfillment.domain.OrderFulfillmentSummaryCalculator;
import cn.iocoder.yudao.module.trade.service.fulfillment.support.FulfillmentHashing;
import cn.iocoder.yudao.module.trade.service.fulfillment.support.TrackingEventCanonicalizer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class FulfillmentTrackingServiceImplTest {

    @Test
    void stableHashCanonicalizesUnicodeWhitespaceCaseAndMicroseconds() {
        Instant first = Instant.parse("2026-07-15T02:03:04.123456789Z");
        Instant second = Instant.parse("2026-07-15T02:03:04.123456001Z");

        String left = TrackingEventCanonicalizer.stableHash(" ups ", " 1Z  999 ", " in   transit ", first,
                " Ｔoronto  ON ", "Arrived   at hub");
        String right = TrackingEventCanonicalizer.stableHash("UPS", "1Z 999", "IN TRANSIT", second,
                "Toronto ON", "Arrived at hub");

        assertEquals(left, right);
        assertNotEquals(left, TrackingEventCanonicalizer.stableHash("UPS", "1Z 999", "IN TRANSIT", second,
                "Toronto ON", "ARRIVED AT HUB"));
    }

    @Test
    void versionedMapperUsesReceivedTimeForActiveMapping() {
        TrackingStatusMappingMapper mapper = mock(TrackingStatusMappingMapper.class);
        VersionedTrackingStatusMapper resolver = new VersionedTrackingStatusMapper(mapper);
        LocalDateTime receivedAt = LocalDateTime.of(2026, 7, 15, 2, 3, 4, 123_456_000);
        TrackingStatusMappingDO row = new TrackingStatusMappingDO().setStandardStatus("OUT_FOR_DELIVERY")
                .setMappingVersion("2026-07-v2").setStatusPriority(80).setEffectiveAt(receivedAt.minusDays(1));
        when(mapper.selectActive(121L, "provider-a", "UPS", "OUT FOR DELIVERY", receivedAt)).thenReturn(row);

        VersionedTrackingStatusMapper.Resolution result = resolver.resolve(121L, "provider-a", "UPS",
                " out   for delivery ", receivedAt, null);

        assertTrue(result.known());
        assertEquals(ShipmentStatusEnum.OUT_FOR_DELIVERY, result.candidateStatus());
        assertEquals("2026-07-v2", result.mappingVersion());
        assertEquals(80, result.statusPriority());
        verify(mapper).selectActive(121L, "provider-a", "UPS", "OUT FOR DELIVERY", receivedAt);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void versionedMapperReplaysExactVersionAndUnknownIsConservative() {
        TrackingStatusMappingMapper mapper = mock(TrackingStatusMappingMapper.class);
        VersionedTrackingStatusMapper resolver = new VersionedTrackingStatusMapper(mapper);
        LocalDateTime receivedAt = LocalDateTime.of(2026, 7, 15, 2, 3);
        TrackingStatusMappingDO old = new TrackingStatusMappingDO().setStandardStatus("IN_TRANSIT")
                .setMappingVersion("v1").setStatusPriority(30).setEffectiveAt(receivedAt.minusMonths(1));
        when(mapper.selectAtVersion(121L, "provider-a", "UPS", "MOVING", "v1")).thenReturn(old);

        VersionedTrackingStatusMapper.Resolution replay = resolver.resolve(121L, "provider-a", "UPS",
                "moving", receivedAt, "v1");
        VersionedTrackingStatusMapper.Resolution unknown = resolver.resolve(121L, "provider-a", "UPS",
                "not mapped", receivedAt, null);

        assertEquals(ShipmentStatusEnum.IN_TRANSIT, replay.candidateStatus());
        assertEquals("v1", replay.mappingVersion());
        assertEquals(30, replay.statusPriority());
        assertFalse(unknown.known());
        assertEquals(ShipmentStatusEnum.IN_TRANSIT, unknown.candidateStatus());
        assertNull(unknown.mappingVersion());
    }

    @Test
    void summaryCalculatorUsesExactPrecedenceAndCounts() {
        OrderFulfillmentSummaryCalculator calculator = new OrderFulfillmentSummaryCalculator();

        assertEquals(OrderFulfillmentStatusEnum.NOT_SHIPPED,
                calculator.calculate(List.of(shipment("DRAFT"))).status());
        assertEquals(OrderFulfillmentStatusEnum.PARTIALLY_SHIPPED,
                calculator.calculate(List.of(shipment("IN_TRANSIT"), shipment("READY_TO_SHIP"))).status());
        assertEquals(OrderFulfillmentStatusEnum.PARTIALLY_DELIVERED,
                calculator.calculate(List.of(shipment("DELIVERED"), shipment("IN_TRANSIT"))).status());
        assertEquals(OrderFulfillmentStatusEnum.DELIVERY_EXCEPTION,
                calculator.calculate(List.of(shipment("DELIVERED"), shipment("DELIVERY_EXCEPTION"))).status());
        assertEquals(OrderFulfillmentStatusEnum.RETURNING,
                calculator.calculate(List.of(shipment("RETURNED"), shipment("DELIVERED"))).status());
        OrderFulfillmentSummaryCalculator.Calculation returned = calculator.calculate(
                List.of(shipment("RETURNED"), shipment("RETURNED"), shipment("CANCELED")));
        assertEquals(OrderFulfillmentStatusEnum.RETURNED, returned.status());
        assertEquals(2, returned.shipmentCount());
        assertEquals(0, returned.deliveredShipmentCount());
    }

    @Test
    void carrierAndTrackingToStringRedactsTrackingNumber() throws Exception {
        Class<?> factsType = Arrays.stream(FulfillmentTrackingServiceImpl.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("CarrierAndTracking"))
                .findFirst().orElseThrow();
        Constructor<?> constructor = factsType.getDeclaredConstructor(String.class, String.class);
        constructor.setAccessible(true);

        String rendered = constructor.newInstance("UPS", "1Z-PRIVATE-TRACKING").toString();

        assertTrue(rendered.contains("UPS"));
        assertTrue(rendered.contains("REDACTED"));
        assertFalse(rendered.contains("1Z-PRIVATE-TRACKING"));
    }

    @Test
    void manualCommandAndTrackingEventToStringExcludeFreeTextAndTrace() {
        ApplyManualTrackingEventCommand command = new ApplyManualTrackingEventCommand()
                .setTenantId(121L).setShipmentId(1L).setShipmentLegId(2L)
                .setRequestedStatus(ShipmentStatusEnum.IN_TRANSIT)
                .setReason("private manual explanation").setRequestTraceId("private-trace-id");
        cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.TrackingEventDO event =
                new cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.TrackingEventDO()
                        .setManualReason("private manual explanation").setRequestTraceId("private-trace-id");

        assertFalse(command.toString().contains("private manual explanation"));
        assertFalse(command.toString().contains("private-trace-id"));
        assertFalse(event.toString().contains("private manual explanation"));
        assertFalse(event.toString().contains("private-trace-id"));
    }

    @Test
    void manualRequestHashCanonicalizesMicrosecondsAndTrimmedReason() {
        String left = FulfillmentHashing.sha256ManualTracking(121L, 1L, 2L, 3L, "IN_TRANSIT",
                Instant.parse("2026-07-16T01:02:03.123456789Z"), 7, 110L, "\u3000Correct scan\u3000");
        String right = FulfillmentHashing.sha256ManualTracking(121L, 1L, 2L, 3L, "IN_TRANSIT",
                Instant.parse("2026-07-16T01:02:03.123456001Z"), 7, 110L, "Correct scan");

        assertEquals(left, right);
        assertNotEquals(left, FulfillmentHashing.sha256ManualTracking(121L, 1L, 2L, 3L, "DELIVERED",
                Instant.parse("2026-07-16T01:02:03.123456001Z"), 7, 110L, "Correct scan"));
    }

    @Test
    void manualUnicodeLengthCountsCodePointsRatherThanUtf16Units() {
        String emoji = "\uD83D\uDE9A";

        assertEquals(500, FulfillmentTrackingServiceImpl.codePointLength(emoji.repeat(500)));
        assertEquals(501, FulfillmentTrackingServiceImpl.codePointLength(emoji.repeat(501)));
        assertEquals(64, FulfillmentTrackingServiceImpl.codePointLength(emoji.repeat(64)));
        assertEquals(65, FulfillmentTrackingServiceImpl.codePointLength(emoji.repeat(65)));
    }

    private static ShipmentDO shipment(String status) {
        return new ShipmentDO().setStatus(status);
    }
}
