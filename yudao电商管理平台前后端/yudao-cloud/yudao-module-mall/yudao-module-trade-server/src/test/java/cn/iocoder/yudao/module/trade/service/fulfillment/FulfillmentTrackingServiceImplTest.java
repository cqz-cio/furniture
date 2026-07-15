package cn.iocoder.yudao.module.trade.service.fulfillment;

import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.TrackingStatusMappingDO;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.TrackingStatusMappingMapper;
import cn.iocoder.yudao.module.trade.enums.fulfillment.OrderFulfillmentStatusEnum;
import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum;
import cn.iocoder.yudao.module.trade.service.fulfillment.domain.OrderFulfillmentSummaryCalculator;
import cn.iocoder.yudao.module.trade.service.fulfillment.support.TrackingEventCanonicalizer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
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

    private static ShipmentDO shipment(String status) {
        return new ShipmentDO().setStatus(status);
    }
}
