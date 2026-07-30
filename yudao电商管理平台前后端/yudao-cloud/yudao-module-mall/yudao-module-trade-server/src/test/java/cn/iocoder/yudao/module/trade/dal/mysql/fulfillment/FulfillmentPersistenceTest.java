package cn.iocoder.yudao.module.trade.dal.mysql.fulfillment;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.CarrierDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.FulfillmentIdempotencyDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.FulfillmentOutboxEventDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.LogisticsProviderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.OrderFulfillmentSummaryDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentItemDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentLegDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentPackageDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.TrackingEventDO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum.DELIVERED;
import static cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum.IN_TRANSIT;
import static cn.iocoder.yudao.module.trade.enums.fulfillment.TrackingEventSourceEnum.POLLING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FulfillmentPersistenceTest extends BaseDbUnitTest {

    private static final Long TENANT_ID = 121L;
    private static final LocalDateTime EVENT_TIME = LocalDateTime.of(2026, 7, 15, 10, 0);

    @Resource
    private CarrierMapper carrierMapper;
    @Resource
    private LogisticsProviderMapper logisticsProviderMapper;
    @Resource
    private ShipmentMapper shipmentMapper;
    @Resource
    private ShipmentItemMapper shipmentItemMapper;
    @Resource
    private ShipmentPackageMapper shipmentPackageMapper;
    @Resource
    private ShipmentLegMapper shipmentLegMapper;
    @Resource
    private TrackingEventMapper trackingEventMapper;
    @Resource
    private OrderFulfillmentSummaryMapper orderFulfillmentSummaryMapper;
    @Resource
    private FulfillmentIdempotencyMapper fulfillmentIdempotencyMapper;
    @Resource
    private FulfillmentOutboxEventMapper fulfillmentOutboxEventMapper;

    @BeforeEach
    void setUpAuditContext() {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(110L);
        loginUser.setTenantId(TENANT_ID);
        loginUser.setUserType(1);
        SecurityFrameworkUtils.setLoginUser(loginUser, new MockHttpServletRequest());
    }

    @AfterEach
    void clearAuditContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shipmentQueriesAndOptimisticUpdatesAreTenantScoped() {
        ShipmentDO shipment = createShipment(TENANT_ID, 100L, "SHP-001");
        shipmentMapper.insert(shipment);
        ShipmentDO deletedShipment = createShipment(TENANT_ID, 100L, "SHP-DELETED");
        shipmentMapper.insert(deletedShipment);
        assertEquals(1, shipmentMapper.deleteById(deletedShipment.getId()));

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
        trackingEventMapper.insert(deleted);
        assertEquals(1, trackingEventMapper.deleteById(deleted.getId()));

        List<TrackingEventDO> timeline = trackingEventMapper.selectListByShipmentId(TENANT_ID, 7L);
        assertEquals(List.of(earlier.getId(), sameTime.getId(), later.getId()),
                timeline.stream().map(TrackingEventDO::getId).toList());

        shipmentItemMapper.insert(createShipmentItem(TENANT_ID, 1L, 88L, "1.250000"));
        shipmentItemMapper.insert(createShipmentItem(TENANT_ID, 2L, 88L, "2.500000"));
        shipmentItemMapper.insert(createShipmentItem(122L, 3L, 88L, "9.000000"));
        ShipmentItemDO deletedItem = createShipmentItem(TENANT_ID, 4L, 88L, "4.000000");
        shipmentItemMapper.insert(deletedItem);
        assertEquals(1, shipmentItemMapper.deleteById(deletedItem.getId()));

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
    void activeRecordUniquenessAllowsRepeatedSoftDeleteAndRecreate() {
        CarrierDO first = createCarrier();
        carrierMapper.insert(first);
        assertEquals(1, carrierMapper.deleteById(first.getId()));

        CarrierDO second = createCarrier();
        carrierMapper.insert(second);
        assertEquals(1, carrierMapper.deleteById(second.getId()));

        CarrierDO third = createCarrier();
        carrierMapper.insert(third);
        assertThrows(DataIntegrityViolationException.class, () -> carrierMapper.insert(createCarrier()));
    }

    @Test
    void outboxJsonPayloadRoundTripsThroughJacksonTypeHandler() {
        FulfillmentOutboxEventDO outbox = createOutboxEvent();
        fulfillmentOutboxEventMapper.insert(outbox);

        Map<String, Object> payload = fulfillmentOutboxEventMapper.selectById(outbox.getId()).getPayload();
        assertEquals("DRAFT", payload.get("status"));
        assertEquals(99L, ((Number) payload.get("shipmentId")).longValue());
    }

    @Test
    void carrierMapperSupportsCrudAndLogicalDelete() {
        assertCrud(carrierMapper, createCarrier(), CarrierDO::getId,
                carrier -> carrier.setName("Updated Carrier"),
                carrier -> assertEquals("Updated Carrier", carrier.getName()));
    }

    @Test
    void logisticsProviderMapperSupportsCrudAndLogicalDelete() {
        assertCrud(logisticsProviderMapper, createLogisticsProvider(), LogisticsProviderDO::getId,
                provider -> provider.setCapabilities("TRACKING_QUERY,WEBHOOK"),
                provider -> assertEquals("TRACKING_QUERY,WEBHOOK", provider.getCapabilities()));
    }

    @Test
    void shipmentMapperSupportsCrudAndLogicalDelete() {
        assertCrud(shipmentMapper, createShipment(TENANT_ID, 100L, "SHP-CRUD"), ShipmentDO::getId,
                shipment -> shipment.setStatus(IN_TRANSIT.name()),
                shipment -> assertEquals(IN_TRANSIT.name(), shipment.getStatus()));
    }

    @Test
    void shipmentItemMapperSupportsCrudAndLogicalDelete() {
        assertCrud(shipmentItemMapper, createShipmentItem(TENANT_ID, 10L, 20L, "1.000000"),
                ShipmentItemDO::getId,
                item -> item.setQuantity(new BigDecimal("2.000000")),
                item -> assertEquals(0, new BigDecimal("2.000000").compareTo(item.getQuantity())));
    }

    @Test
    void shipmentPackageMapperSupportsCrudAndLogicalDelete() {
        assertCrud(shipmentPackageMapper, createPackage(TENANT_ID, 10L, "PKG-CRUD", "TRACK-CRUD"),
                ShipmentPackageDO::getId,
                shipmentPackage -> shipmentPackage.setStatus("IN_TRANSIT"),
                shipmentPackage -> assertEquals("IN_TRANSIT", shipmentPackage.getStatus()));
    }

    @Test
    void shipmentLegMapperSupportsCrudAndLogicalDelete() {
        assertCrud(shipmentLegMapper, createShipmentLeg(), ShipmentLegDO::getId,
                leg -> leg.setStatus("COMPLETED"),
                leg -> assertEquals("COMPLETED", leg.getStatus()));
    }

    @Test
    void trackingEventMapperSupportsCrudAndLogicalDelete() {
        assertCrud(trackingEventMapper, createTrackingEvent(TENANT_ID, 10L, "event-crud", EVENT_TIME),
                TrackingEventDO::getId,
                event -> event.setDescription("Updated description"),
                event -> assertEquals("Updated description", event.getDescription()));
    }

    @Test
    void orderFulfillmentSummaryMapperSupportsCrudAndLogicalDelete() {
        assertCrud(orderFulfillmentSummaryMapper, createSummary(), OrderFulfillmentSummaryDO::getId,
                summary -> summary.setStatus("SHIPPED"),
                summary -> assertEquals("SHIPPED", summary.getStatus()));
    }

    @Test
    void fulfillmentIdempotencyMapperSupportsCrudAndLogicalDelete() {
        assertCrud(fulfillmentIdempotencyMapper, createIdempotency("crud-key-hash"),
                FulfillmentIdempotencyDO::getId,
                idempotency -> idempotency.setStatus("COMPLETED"),
                idempotency -> assertEquals("COMPLETED", idempotency.getStatus()));
    }

    @Test
    void fulfillmentOutboxEventMapperSupportsCrudAndLogicalDelete() {
        assertCrud(fulfillmentOutboxEventMapper, createOutboxEvent(), FulfillmentOutboxEventDO::getId,
                outbox -> outbox.setStatus("PUBLISHED"),
                outbox -> assertEquals("PUBLISHED", outbox.getStatus()));
    }

    @Test
    void sensitiveFulfillmentFieldsAreExcludedFromToString() {
        ShipmentPackageDO shipmentPackage = createPackage(TENANT_ID, 1L, "PKG-SECRET", "package-tracking-secret");
        ShipmentLegDO leg = createShipmentLeg();
        TrackingEventDO event = createTrackingEvent(TENANT_ID, 1L, "external-event-secret", EVENT_TIME);
        event.setEventHash("event-hash-secret");
        event.setProviderStatus("provider-status-secret");
        event.setProviderStatusNormalized("provider-status-normalized-secret");
        event.setDescription("description-secret");
        event.setLocation("location-secret");
        event.setRawPayloadRef("payload-ref-secret");
        FulfillmentIdempotencyDO idempotency = createIdempotency("idempotency-key-secret");
        idempotency.setRequestHash("request-hash-secret");
        FulfillmentOutboxEventDO outbox = createOutboxEvent();
        outbox.setPayload(Map.of("secret", "payload-secret"));

        assertFalse(shipmentPackage.toString().contains("package-tracking-secret"));
        assertFalse(leg.toString().contains("leg-tracking-secret"));
        assertFalse(leg.toString().contains("pro-number-secret"));
        assertFalse(leg.toString().contains("bol-number-secret"));
        assertFalse(leg.toString().contains("origin-location-secret"));
        assertFalse(leg.toString().contains("destination-location-secret"));
        assertFalse(event.toString().contains("external-event-secret"));
        assertFalse(event.toString().contains("event-hash-secret"));
        assertFalse(event.toString().contains("provider-status-secret"));
        assertFalse(event.toString().contains("provider-status-normalized-secret"));
        assertFalse(event.toString().contains("description-secret"));
        assertFalse(event.toString().contains("location-secret"));
        assertFalse(event.toString().contains("payload-ref-secret"));
        assertFalse(idempotency.toString().contains("idempotency-key-secret"));
        assertFalse(idempotency.toString().contains("request-hash-secret"));
        assertFalse(outbox.toString().contains("payload-secret"));
    }

    private static CarrierDO createCarrier() {
        CarrierDO carrier = new CarrierDO();
        carrier.setTenantId(TENANT_ID);
        carrier.setCode("UPS");
        carrier.setName("United Parcel Service");
        carrier.setCountryCodes("US,CA");
        carrier.setLegacyExpressId(42L);
        carrier.setStatus(0);
        setAuditFields(carrier);
        return carrier;
    }

    private static LogisticsProviderDO createLogisticsProvider() {
        LogisticsProviderDO provider = new LogisticsProviderDO();
        provider.setTenantId(TENANT_ID);
        provider.setCode("MOCK");
        provider.setName("Mock Provider");
        provider.setCapabilities("TRACKING_QUERY");
        provider.setStatus(0);
        setAuditFields(provider);
        return provider;
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

    private static ShipmentLegDO createShipmentLeg() {
        ShipmentLegDO leg = new ShipmentLegDO();
        leg.setTenantId(TENANT_ID);
        leg.setShipmentId(10L);
        leg.setPackageId(20L);
        leg.setSequenceNo(1);
        leg.setLegType("PARCEL");
        leg.setCarrierId(30L);
        leg.setProviderId(40L);
        leg.setServiceLevel("GROUND");
        leg.setTrackingNumber("leg-tracking-secret");
        leg.setProNumber("pro-number-secret");
        leg.setBolNumber("bol-number-secret");
        leg.setOriginLocation("origin-location-secret");
        leg.setDestinationLocation("destination-location-secret");
        leg.setStatus("CREATED");
        leg.setStartedAt(EVENT_TIME);
        leg.setCompletedAt(EVENT_TIME.plusHours(1));
        leg.setVersion(0);
        setAuditFields(leg);
        return leg;
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
        event.setSource(POLLING.name());
        setAuditFields(event);
        return event;
    }

    private static OrderFulfillmentSummaryDO createSummary() {
        OrderFulfillmentSummaryDO summary = new OrderFulfillmentSummaryDO();
        summary.setTenantId(TENANT_ID);
        summary.setOrderId(100L);
        summary.setStatus("NOT_SHIPPED");
        summary.setShipmentCount(0);
        summary.setDeliveredShipmentCount(0);
        summary.setVersion(0);
        setAuditFields(summary);
        return summary;
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

    private static FulfillmentOutboxEventDO createOutboxEvent() {
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
        return outbox;
    }

    private static <T extends BaseDO> void assertCrud(BaseMapperX<T> mapper, T dataObject,
                                                       Function<T, Long> idGetter, Consumer<T> mutator,
                                                       Consumer<T> updatedVerifier) {
        assertEquals(1, mapper.insert(dataObject));
        Long id = idGetter.apply(dataObject);
        assertNotNull(id);
        assertNotNull(mapper.selectById(id));

        mutator.accept(dataObject);
        assertEquals(1, mapper.updateById(dataObject));
        updatedVerifier.accept(mapper.selectById(id));

        assertEquals(1, mapper.deleteById(id));
        assertNull(mapper.selectById(id));
    }

    private static void setAuditFields(BaseDO dataObject) {
        dataObject.setCreator("");
        dataObject.setUpdater("");
    }

}
