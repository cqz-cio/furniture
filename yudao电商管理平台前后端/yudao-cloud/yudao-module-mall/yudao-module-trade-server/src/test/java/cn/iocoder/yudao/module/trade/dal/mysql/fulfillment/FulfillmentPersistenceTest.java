package cn.iocoder.yudao.module.trade.dal.mysql.fulfillment;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.FulfillmentIdempotencyDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.FulfillmentOutboxEventDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.OrderFulfillmentSummaryDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentItemDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentPackageDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.TrackingEventDO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum.DELIVERED;
import static cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum.IN_TRANSIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FulfillmentPersistenceTest extends BaseDbUnitTest {

    private static final Long TENANT_ID = 121L;
    private static final LocalDateTime EVENT_TIME = LocalDateTime.of(2026, 7, 15, 10, 0);

    @Resource
    private ShipmentMapper shipmentMapper;
    @Resource
    private ShipmentItemMapper shipmentItemMapper;
    @Resource
    private ShipmentPackageMapper shipmentPackageMapper;
    @Resource
    private TrackingEventMapper trackingEventMapper;
    @Resource
    private OrderFulfillmentSummaryMapper orderFulfillmentSummaryMapper;
    @Resource
    private FulfillmentIdempotencyMapper fulfillmentIdempotencyMapper;
    @Resource
    private FulfillmentOutboxEventMapper fulfillmentOutboxEventMapper;

    @Test
    void shipmentQueriesAndOptimisticUpdatesAreTenantScoped() {
        ShipmentDO shipment = createShipment(TENANT_ID, 100L, "SHP-001");
        shipmentMapper.insert(shipment);
        ShipmentDO deletedShipment = createShipment(TENANT_ID, 100L, "SHP-DELETED");
        deletedShipment.setDeleted(true);
        shipmentMapper.insert(deletedShipment);

        assertEquals(1, shipmentMapper.selectListByOrderId(121L, 100L).size());
        assertTrue(shipmentMapper.selectListByOrderId(122L, 100L).isEmpty());
        assertEquals(1, shipmentMapper.updateStatusByIdAndVersion(
                121L, shipment.getId(), 0, IN_TRANSIT.name(), EVENT_TIME));
        assertEquals(0, shipmentMapper.updateStatusByIdAndVersion(
                121L, shipment.getId(), 0, DELIVERED.name(), EVENT_TIME.plusHours(1)));

        ShipmentDO updated = shipmentMapper.selectById(shipment.getId());
        assertEquals(IN_TRANSIT.name(), updated.getStatus());
        assertEquals(EVENT_TIME, updated.getLastEventOccurredAt());
        assertEquals(1, updated.getVersion());
    }

    @Test
    void timelineReadsAndQuantitySumsAreTenantScopedAndIgnoreDeletedRows() {
        TrackingEventDO later = createTrackingEvent(TENANT_ID, 7L, "event-later", EVENT_TIME.plusHours(1));
        trackingEventMapper.insert(later);
        TrackingEventDO earlier = createTrackingEvent(TENANT_ID, 7L, "event-earlier", EVENT_TIME);
        trackingEventMapper.insert(earlier);
        TrackingEventDO sameTime = createTrackingEvent(TENANT_ID, 7L, "event-same", EVENT_TIME);
        trackingEventMapper.insert(sameTime);
        TrackingEventDO otherTenant = createTrackingEvent(122L, 7L, "event-other", EVENT_TIME.minusHours(1));
        trackingEventMapper.insert(otherTenant);
        TrackingEventDO deleted = createTrackingEvent(TENANT_ID, 7L, "event-deleted", EVENT_TIME.minusHours(2));
        deleted.setDeleted(true);
        trackingEventMapper.insert(deleted);

        List<TrackingEventDO> timeline = trackingEventMapper.selectListByShipmentId(TENANT_ID, 7L);
        assertEquals(List.of(earlier.getId(), sameTime.getId(), later.getId()),
                timeline.stream().map(TrackingEventDO::getId).toList());

        shipmentItemMapper.insert(createShipmentItem(TENANT_ID, 1L, 88L, "1.250000"));
        shipmentItemMapper.insert(createShipmentItem(TENANT_ID, 2L, 88L, "2.500000"));
        shipmentItemMapper.insert(createShipmentItem(122L, 3L, 88L, "9.000000"));
        ShipmentItemDO deletedItem = createShipmentItem(TENANT_ID, 4L, 88L, "4.000000");
        deletedItem.setDeleted(true);
        shipmentItemMapper.insert(deletedItem);

        assertEquals(0, new BigDecimal("3.750000").compareTo(
                shipmentItemMapper.sumQuantityByOrderItemId(TENANT_ID, 88L)));
    }

    @Test
    void summaryUpdateRequiresMatchingTenantAndVersion() {
        OrderFulfillmentSummaryDO summary = new OrderFulfillmentSummaryDO();
        summary.setTenantId(TENANT_ID);
        summary.setOrderId(100L);
        summary.setStatus("NOT_SHIPPED");
        summary.setShipmentCount(0);
        summary.setDeliveredShipmentCount(0);
        summary.setVersion(0);
        setAuditFields(summary);
        orderFulfillmentSummaryMapper.insert(summary);

        assertEquals(1, orderFulfillmentSummaryMapper.updateCountsAndStatusByIdAndVersion(
                TENANT_ID, summary.getId(), 0, "PARTIALLY_SHIPPED", 2, 1));
        assertEquals(0, orderFulfillmentSummaryMapper.updateCountsAndStatusByIdAndVersion(
                TENANT_ID, summary.getId(), 0, "SHIPPED", 2, 2));
        assertEquals(0, orderFulfillmentSummaryMapper.updateCountsAndStatusByIdAndVersion(
                122L, summary.getId(), 1, "SHIPPED", 2, 2));

        OrderFulfillmentSummaryDO updated = orderFulfillmentSummaryMapper.selectById(summary.getId());
        assertEquals("PARTIALLY_SHIPPED", updated.getStatus());
        assertEquals(2, updated.getShipmentCount());
        assertEquals(1, updated.getDeliveredShipmentCount());
        assertEquals(1, updated.getVersion());
    }

    @Test
    void duplicateTrackingNumbersAreRejectedByDatabaseConstraint() {
        shipmentPackageMapper.insert(createPackage(TENANT_ID, 1L, "PKG-1", "TRACK-1"));

        assertThrows(DataIntegrityViolationException.class,
                () -> shipmentPackageMapper.insert(createPackage(TENANT_ID, 2L, "PKG-2", "TRACK-1")));
    }

    @Test
    void duplicateExternalEventIdsAreRejectedByDatabaseConstraint() {
        trackingEventMapper.insert(createTrackingEvent(TENANT_ID, 1L, "external-1", EVENT_TIME));

        assertThrows(DataIntegrityViolationException.class,
                () -> trackingEventMapper.insert(createTrackingEvent(TENANT_ID, 2L, "external-1", EVENT_TIME)));
    }

    @Test
    void duplicateIdempotencyHashesAreRejectedByDatabaseConstraint() {
        fulfillmentIdempotencyMapper.insert(createIdempotency("key-hash"));

        assertThrows(DataIntegrityViolationException.class,
                () -> fulfillmentIdempotencyMapper.insert(createIdempotency("key-hash")));
    }

    @Test
    void outboxJsonPayloadRoundTripsThroughJacksonTypeHandler() {
        FulfillmentOutboxEventDO outbox = new FulfillmentOutboxEventDO();
        outbox.setTenantId(TENANT_ID);
        outbox.setEventId("123e4567-e89b-12d3-a456-426614174000");
        outbox.setAggregateType("SHIPMENT");
        outbox.setAggregateId(99L);
        outbox.setEventType("SHIPMENT_CREATED");
        outbox.setPayload(Map.of("shipmentId", 99L, "status", "DRAFT"));
        outbox.setStatus("PENDING");
        outbox.setAttemptCount(0);
        outbox.setNextAttemptAt(EVENT_TIME);
        setAuditFields(outbox);
        fulfillmentOutboxEventMapper.insert(outbox);

        Map<String, Object> payload = fulfillmentOutboxEventMapper.selectById(outbox.getId()).getPayload();
        assertEquals("DRAFT", payload.get("status"));
        assertEquals(99L, ((Number) payload.get("shipmentId")).longValue());
    }

    private static ShipmentDO createShipment(Long tenantId, Long orderId, String shipmentNo) {
        ShipmentDO shipment = new ShipmentDO();
        shipment.setTenantId(tenantId);
        shipment.setOrderId(orderId);
        shipment.setShipmentNo(shipmentNo);
        shipment.setShipmentType("PARCEL");
        shipment.setStatus("DRAFT");
        shipment.setOriginCountry("US");
        shipment.setDestinationCountry("US");
        shipment.setOriginTimezone("America/New_York");
        shipment.setDestinationTimezone("America/Los_Angeles");
        shipment.setWarehouseId(10L);
        shipment.setVersion(0);
        setAuditFields(shipment);
        return shipment;
    }

    private static ShipmentItemDO createShipmentItem(Long tenantId, Long shipmentId, Long orderItemId,
                                                       String quantity) {
        ShipmentItemDO item = new ShipmentItemDO();
        item.setTenantId(tenantId);
        item.setShipmentId(shipmentId);
        item.setOrderItemId(orderItemId);
        item.setSkuId(900L + shipmentId);
        item.setQuantity(new BigDecimal(quantity));
        setAuditFields(item);
        return item;
    }

    private static ShipmentPackageDO createPackage(Long tenantId, Long shipmentId, String packageNo,
                                                     String trackingNumber) {
        ShipmentPackageDO shipmentPackage = new ShipmentPackageDO();
        shipmentPackage.setTenantId(tenantId);
        shipmentPackage.setShipmentId(shipmentId);
        shipmentPackage.setPackageNo(packageNo);
        shipmentPackage.setPackageType("PARCEL");
        shipmentPackage.setCarrierId(5L);
        shipmentPackage.setTrackingNumber(trackingNumber);
        shipmentPackage.setStatus("CREATED");
        shipmentPackage.setVersion(0);
        setAuditFields(shipmentPackage);
        return shipmentPackage;
    }

    private static TrackingEventDO createTrackingEvent(Long tenantId, Long shipmentId, String externalEventId,
                                                        LocalDateTime occurredAt) {
        TrackingEventDO event = new TrackingEventDO();
        event.setTenantId(tenantId);
        event.setShipmentId(shipmentId);
        event.setProviderId(9L);
        event.setExternalEventId(externalEventId);
        event.setStandardStatus(IN_TRANSIT.name());
        event.setProviderStatus("in transit");
        event.setOccurredAt(occurredAt);
        event.setOccurredTimezone("America/Chicago");
        event.setReceivedAt(occurredAt.plusMinutes(1));
        event.setSource("POLL");
        setAuditFields(event);
        return event;
    }

    private static FulfillmentIdempotencyDO createIdempotency(String idempotencyKeyHash) {
        FulfillmentIdempotencyDO idempotency = new FulfillmentIdempotencyDO();
        idempotency.setTenantId(TENANT_ID);
        idempotency.setOperation("CREATE_SHIPMENT");
        idempotency.setIdempotencyKeyHash(idempotencyKeyHash);
        idempotency.setRequestHash("request-hash");
        idempotency.setResourceType("SHIPMENT");
        idempotency.setStatus("PROCESSING");
        idempotency.setExpiresAt(EVENT_TIME.plusDays(1));
        setAuditFields(idempotency);
        return idempotency;
    }

    private static void setAuditFields(BaseDO dataObject) {
        dataObject.setCreator("");
        dataObject.setUpdater("");
    }

}
