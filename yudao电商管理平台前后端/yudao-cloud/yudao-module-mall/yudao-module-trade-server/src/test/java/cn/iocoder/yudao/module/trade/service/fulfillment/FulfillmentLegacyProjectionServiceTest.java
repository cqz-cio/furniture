package cn.iocoder.yudao.module.trade.service.fulfillment;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentLegDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentPackageDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.TrackingEventDO;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentLegMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentPackageMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.TrackingEventMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.module.trade.service.fulfillment.FulfillmentLegacyProjectionResult.Mode.AUTHORITATIVE_EMPTY;
import static cn.iocoder.yudao.module.trade.service.fulfillment.FulfillmentLegacyProjectionResult.Mode.AUTHORITATIVE_EVENTS;
import static cn.iocoder.yudao.module.trade.service.fulfillment.FulfillmentLegacyProjectionResult.Mode.FALLBACK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FulfillmentLegacyProjectionServiceTest extends BaseMockitoUnitTest {

    private static final Long TENANT_ID = 121L;
    private static final Long ORDER_ID = 501L;
    private static final LocalDateTime EARLY = LocalDateTime.of(2026, 7, 16, 8, 0);
    private static final LocalDateTime LATE = EARLY.plusHours(1);

    @InjectMocks
    private FulfillmentLegacyProjectionServiceImpl service;

    @Mock
    private ShipmentMapper shipmentMapper;
    @Mock
    private ShipmentPackageMapper packageMapper;
    @Mock
    private ShipmentLegMapper legMapper;
    @Mock
    private TrackingEventMapper eventMapper;

    @Test
    void project_returnsFallbackWhenOrderHasNoShipment() {
        when(shipmentMapper.selectListByOrderId(TENANT_ID, ORDER_ID)).thenReturn(List.of());

        FulfillmentLegacyProjectionResult result = service.project(TENANT_ID, ORDER_ID);

        assertEquals(FALLBACK, result.mode());
        assertEquals(List.of(), result.events());
        verify(packageMapper, never()).selectListByShipmentId(TENANT_ID, 1L);
    }

    @Test
    void project_returnsFallbackWhenShipmentHasNoEvents() {
        ShipmentDO shipment = shipment(10L, "IN_TRANSIT");
        ShipmentPackageDO shipmentPackage = shipmentPackage(20L, "IN_TRANSIT");
        when(shipmentMapper.selectListByOrderId(TENANT_ID, ORDER_ID)).thenReturn(List.of(shipment));
        when(packageMapper.selectListByShipmentId(TENANT_ID, 10L)).thenReturn(List.of(shipmentPackage));
        when(eventMapper.selectLegacySubjectEvents(TENANT_ID, 10L, 20L, null)).thenReturn(List.of());
        when(legMapper.selectListByShipmentId(TENANT_ID, 10L)).thenReturn(List.of());

        FulfillmentLegacyProjectionResult result = service.project(TENANT_ID, ORDER_ID);

        assertEquals(FALLBACK, result.mode());
    }

    @Test
    void project_doesNotBlendLaterSiblingShipmentIntoLegacyStream() {
        ShipmentDO first = shipment(10L, "IN_TRANSIT");
        ShipmentDO later = shipment(11L, "IN_TRANSIT");
        ShipmentPackageDO firstPackage = shipmentPackage(20L, "IN_TRANSIT");
        when(shipmentMapper.selectListByOrderId(TENANT_ID, ORDER_ID)).thenReturn(List.of(later, first));
        when(packageMapper.selectListByShipmentId(TENANT_ID, 10L)).thenReturn(List.of(firstPackage));
        when(eventMapper.selectLegacySubjectEvents(TENANT_ID, 10L, 20L, null)).thenReturn(List.of());
        when(legMapper.selectListByShipmentId(TENANT_ID, 10L)).thenReturn(List.of());

        FulfillmentLegacyProjectionResult result = service.project(TENANT_ID, ORDER_ID);

        assertEquals(FALLBACK, result.mode());
        verify(packageMapper, never()).selectListByShipmentId(TENANT_ID, 11L);
    }

    @Test
    void project_returnsAuthoritativeEmptyWhenExistingEventsAreUnsafe() {
        ShipmentDO shipment = shipment(10L, "IN_TRANSIT");
        ShipmentPackageDO shipmentPackage = shipmentPackage(20L, "IN_TRANSIT");
        TrackingEventDO unsafe = event(30L, EARLY, "NOT_A_STANDARD_STATUS")
                .setDescription("RAW_DESCRIPTION_CANARY")
                .setLocation("PRIVATE_LOCATION_CANARY")
                .setManualReason("MANUAL_REASON_CANARY")
                .setRequestTraceId("TRACE_CANARY");
        when(shipmentMapper.selectListByOrderId(TENANT_ID, ORDER_ID)).thenReturn(List.of(shipment));
        when(packageMapper.selectListByShipmentId(TENANT_ID, 10L)).thenReturn(List.of(shipmentPackage));
        when(eventMapper.selectLegacySubjectEvents(TENANT_ID, 10L, 20L, null)).thenReturn(List.of(unsafe));

        FulfillmentLegacyProjectionResult result = service.project(TENANT_ID, ORDER_ID);

        assertEquals(AUTHORITATIVE_EMPTY, result.mode());
        assertEquals(List.of(), result.events());
    }

    @Test
    void project_selectsFirstSubjectWithEventsAndExposesOnlySafeOrderedFields() {
        ShipmentDO canceled = shipment(5L, "CANCELED");
        ShipmentDO shipment = shipment(10L, "IN_TRANSIT");
        ShipmentPackageDO noEvents = shipmentPackage(20L, "IN_TRANSIT");
        ShipmentPackageDO subject = shipmentPackage(21L, "IN_TRANSIT");
        TrackingEventDO later = event(41L, LATE, "DELIVERED")
                .setProviderStatus("RAW_PROVIDER_CANARY")
                .setRawPayloadRef("RAW_PAYLOAD_CANARY");
        TrackingEventDO equalTimeHigherId = event(42L, EARLY, "OUT_FOR_DELIVERY");
        TrackingEventDO equalTimeLowerId = event(40L, EARLY, "IN_TRANSIT")
                .setDescription("RAW_DESCRIPTION_CANARY");
        when(shipmentMapper.selectListByOrderId(TENANT_ID, ORDER_ID)).thenReturn(List.of(canceled, shipment));
        when(packageMapper.selectListByShipmentId(TENANT_ID, 10L)).thenReturn(List.of(noEvents, subject));
        when(eventMapper.selectLegacySubjectEvents(TENANT_ID, 10L, 20L, null)).thenReturn(List.of());
        when(eventMapper.selectLegacySubjectEvents(TENANT_ID, 10L, 21L, null))
                .thenReturn(List.of(later, equalTimeHigherId, equalTimeLowerId));

        FulfillmentLegacyProjectionResult result = service.project(TENANT_ID, ORDER_ID);

        assertEquals(AUTHORITATIVE_EVENTS, result.mode());
        assertEquals(List.of("IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED"),
                result.events().stream().map(event -> event.getContent()).toList());
        assertEquals(List.of(EARLY, EARLY, LATE),
                result.events().stream().map(event -> event.getTime()).toList());
        String exposed = result.events().toString();
        assertFalse(exposed.contains("CANARY"));
        verify(packageMapper, never()).selectListByShipmentId(TENANT_ID, 5L);
    }

    @Test
    void project_supportsPackageLessLegAndKeepsTenantPredicate() {
        ShipmentDO shipment = shipment(10L, "IN_TRANSIT");
        ShipmentLegDO canceled = leg(50L, 1, "CANCELED");
        ShipmentLegDO active = leg(51L, 2, "IN_TRANSIT");
        TrackingEventDO event = event(60L, EARLY, "AT_LOCAL_TERMINAL");
        when(shipmentMapper.selectListByOrderId(TENANT_ID, ORDER_ID)).thenReturn(List.of(shipment));
        when(packageMapper.selectListByShipmentId(TENANT_ID, 10L)).thenReturn(List.of());
        when(legMapper.selectListByShipmentId(TENANT_ID, 10L)).thenReturn(List.of(canceled, active));
        when(eventMapper.selectLegacySubjectEvents(TENANT_ID, 10L, null, 51L)).thenReturn(List.of(event));

        FulfillmentLegacyProjectionResult result = service.project(TENANT_ID, ORDER_ID);

        assertEquals(AUTHORITATIVE_EVENTS, result.mode());
        assertEquals("AT_LOCAL_TERMINAL", result.events().get(0).getContent());
        verify(eventMapper).selectLegacySubjectEvents(TENANT_ID, 10L, null, 51L);
    }

    private static ShipmentDO shipment(Long id, String status) {
        return new ShipmentDO().setId(id).setTenantId(TENANT_ID).setOrderId(ORDER_ID).setStatus(status);
    }

    private static ShipmentPackageDO shipmentPackage(Long id, String status) {
        return new ShipmentPackageDO().setId(id).setTenantId(TENANT_ID).setShipmentId(10L).setStatus(status);
    }

    private static ShipmentLegDO leg(Long id, int sequence, String status) {
        return new ShipmentLegDO().setId(id).setTenantId(TENANT_ID).setShipmentId(10L)
                .setSequenceNo(sequence).setStatus(status);
    }

    private static TrackingEventDO event(Long id, LocalDateTime occurredAt, String status) {
        return new TrackingEventDO().setId(id).setTenantId(TENANT_ID).setShipmentId(10L)
                .setOccurredAt(occurredAt).setStandardStatus(status);
    }

}
