package cn.iocoder.yudao.module.trade.dal.mysql.fulfillment;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentLegDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentPackageDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.TrackingEventDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.TrackingStatusMappingDO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrackingStatusMappingPersistenceTest extends BaseDbUnitTest {

    private static final Long TENANT_ID = 121L;
    private static final LocalDateTime EFFECTIVE_AT = LocalDateTime.of(2026, 7, 15, 10, 0, 0, 123_456_000);

    @Resource
    private TrackingStatusMappingMapper mappingMapper;
    @Resource
    private TrackingEventMapper trackingEventMapper;
    @Resource
    private ShipmentPackageMapper shipmentPackageMapper;
    @Resource
    private ShipmentLegMapper shipmentLegMapper;

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
    void selectActiveIsTenantScopedExactCarrierLogicalDeleteAwareAndDeterministic() {
        TrackingStatusMappingDO first = mapping(TENANT_ID, "MOCK", "UPS", "in_transit",
                "IN_TRANSIT", "v1", EFFECTIVE_AT);
        mappingMapper.insert(first);
        TrackingStatusMappingDO latest = mapping(TENANT_ID, "MOCK", "UPS", "in_transit",
                "OUT_FOR_DELIVERY", "v2", EFFECTIVE_AT.plusHours(1));
        mappingMapper.insert(latest);
        TrackingStatusMappingDO stableTieWinner = mapping(TENANT_ID, "MOCK", "UPS", "in_transit",
                "DELIVERY_EXCEPTION", "v3", EFFECTIVE_AT.plusHours(1));
        mappingMapper.insert(stableTieWinner);
        mappingMapper.insert(mapping(TENANT_ID, "MOCK", "FEDEX", "in_transit",
                "DELIVERED", "v1", EFFECTIVE_AT.minusHours(1)));
        mappingMapper.insert(mapping(122L, "MOCK", "UPS", "in_transit",
                "DELIVERED", "v1", EFFECTIVE_AT.minusHours(1)));
        mappingMapper.insert(mapping(TENANT_ID, "MOCK", "UPS", "in_transit",
                "DELIVERED", "future", EFFECTIVE_AT.plusDays(1)));

        TrackingStatusMappingDO selected = mappingMapper.selectActive(
                TENANT_ID, "MOCK", "UPS", "in_transit", EFFECTIVE_AT.plusHours(2));
        assertEquals(stableTieWinner.getId(), selected.getId());
        assertNull(mappingMapper.selectActive(TENANT_ID, "MOCK", "USPS", "in_transit", EFFECTIVE_AT.plusHours(2)));
        assertNull(mappingMapper.selectActive(123L, "MOCK", "UPS", "in_transit", EFFECTIVE_AT.plusHours(2)));

        mappingMapper.deleteById(stableTieWinner.getId());
        assertEquals(latest.getId(), mappingMapper.selectActive(
                TENANT_ID, "MOCK", "UPS", "in_transit", EFFECTIVE_AT.plusHours(2)).getId());
    }

    @Test
    void selectAtVersionReplaysTheExactVersionAndRejectsDuplicateVersions() {
        TrackingStatusMappingDO version = mapping(TENANT_ID, "MOCK", "UPS", "delivered",
                "DELIVERED", "2026-07-15", EFFECTIVE_AT);
        mappingMapper.insert(version);

        assertEquals(version.getId(), mappingMapper.selectAtVersion(
                TENANT_ID, "MOCK", "UPS", "delivered", "2026-07-15").getId());
        assertNull(mappingMapper.selectAtVersion(
                TENANT_ID, "MOCK", "FEDEX", "delivered", "2026-07-15"));
        assertNull(mappingMapper.selectAtVersion(
                122L, "MOCK", "UPS", "delivered", "2026-07-15"));
        assertThrows(DataIntegrityViolationException.class, () -> mappingMapper.insert(mapping(
                TENANT_ID, "MOCK", "UPS", "delivered", "IN_TRANSIT", "2026-07-15", EFFECTIVE_AT.plusDays(1))));
        mappingMapper.deleteById(version.getId());
        assertNull(mappingMapper.selectAtVersion(
                TENANT_ID, "MOCK", "UPS", "delivered", "2026-07-15"));
    }

    @Test
    void microsecondEventAndWatermarkFieldsRoundTripWithoutPrecisionLoss() {
        TrackingStatusMappingDO mapping = mapping(TENANT_ID, "MOCK", "UPS", "in_transit",
                "IN_TRANSIT", "v1", EFFECTIVE_AT);
        mappingMapper.insert(mapping);
        assertEquals(EFFECTIVE_AT, mappingMapper.selectById(mapping.getId()).getEffectiveAt());

        TrackingEventDO event = trackingEvent();
        trackingEventMapper.insert(event);
        TrackingEventDO persistedEvent = trackingEventMapper.selectById(event.getId());
        assertEquals(EFFECTIVE_AT, persistedEvent.getOccurredAt());
        assertEquals(EFFECTIVE_AT.plusNanos(111_111_000), persistedEvent.getReceivedAt());
        assertEquals("in_transit", persistedEvent.getProviderStatusNormalized());
        assertEquals("v1", persistedEvent.getMappingVersion());
        assertEquals(EFFECTIVE_AT, persistedEvent.getMappingEffectiveAt());
        assertEquals(true, persistedEvent.getMappingKnown());
        assertEquals("APPLY", persistedEvent.getTransitionDecision());
        assertEquals("HANDED_TO_CARRIER", persistedEvent.getPreviousStatus());
        assertEquals("IN_TRANSIT", persistedEvent.getResultStatus());

        ShipmentPackageDO shipmentPackage = shipmentPackage();
        shipmentPackageMapper.insert(shipmentPackage);
        assertEquals(EFFECTIVE_AT, shipmentPackageMapper.selectById(shipmentPackage.getId()).getLastEventOccurredAt());

        ShipmentLegDO leg = shipmentLeg();
        shipmentLegMapper.insert(leg);
        assertEquals(EFFECTIVE_AT, shipmentLegMapper.selectById(leg.getId()).getLastEventOccurredAt());
    }

    private static TrackingStatusMappingDO mapping(Long tenantId, String providerCode, String carrierCode,
                                                    String providerStatus, String standardStatus,
                                                    String mappingVersion, LocalDateTime effectiveAt) {
        TrackingStatusMappingDO mapping = new TrackingStatusMappingDO();
        mapping.setTenantId(tenantId);
        mapping.setProviderCode(providerCode);
        mapping.setCarrierCode(carrierCode);
        mapping.setProviderStatusNormalized(providerStatus);
        mapping.setStandardStatus(standardStatus);
        mapping.setMappingVersion(mappingVersion);
        mapping.setEffectiveAt(effectiveAt);
        setAuditFields(mapping);
        return mapping;
    }

    private static TrackingEventDO trackingEvent() {
        TrackingEventDO event = new TrackingEventDO();
        event.setTenantId(TENANT_ID);
        event.setShipmentId(1L);
        event.setPackageId(2L);
        event.setShipmentLegId(3L);
        event.setProviderId(4L);
        event.setExternalEventId("external-event-secret");
        event.setStandardStatus("IN_TRANSIT");
        event.setProviderStatus("provider-status-secret");
        event.setProviderStatusNormalized("in_transit");
        event.setMappingVersion("v1");
        event.setMappingEffectiveAt(EFFECTIVE_AT);
        event.setMappingKnown(true);
        event.setTransitionDecision("APPLY");
        event.setPreviousStatus("HANDED_TO_CARRIER");
        event.setResultStatus("IN_TRANSIT");
        event.setOccurredAt(EFFECTIVE_AT);
        event.setOccurredTimezone("America/Chicago");
        event.setReceivedAt(EFFECTIVE_AT.plusNanos(111_111_000));
        event.setSource("POLLING");
        setAuditFields(event);
        return event;
    }

    private static ShipmentPackageDO shipmentPackage() {
        ShipmentPackageDO shipmentPackage = new ShipmentPackageDO();
        shipmentPackage.setTenantId(TENANT_ID);
        shipmentPackage.setShipmentId(1L);
        shipmentPackage.setPackageNo("PKG-MICROSECOND");
        shipmentPackage.setPackageType("PARCEL");
        shipmentPackage.setCarrierId(5L);
        shipmentPackage.setTrackingNumber("TRACK-MICROSECOND");
        shipmentPackage.setStatus("IN_TRANSIT");
        shipmentPackage.setLastEventOccurredAt(EFFECTIVE_AT);
        shipmentPackage.setVersion(0);
        setAuditFields(shipmentPackage);
        return shipmentPackage;
    }

    private static ShipmentLegDO shipmentLeg() {
        ShipmentLegDO leg = new ShipmentLegDO();
        leg.setTenantId(TENANT_ID);
        leg.setShipmentId(1L);
        leg.setPackageId(2L);
        leg.setSequenceNo(1);
        leg.setLegType("PARCEL");
        leg.setCarrierId(5L);
        leg.setProviderId(4L);
        leg.setStatus("IN_TRANSIT");
        leg.setLastEventOccurredAt(EFFECTIVE_AT);
        leg.setVersion(0);
        setAuditFields(leg);
        return leg;
    }

    private static void setAuditFields(BaseDO dataObject) {
        dataObject.setCreator("");
        dataObject.setUpdater("");
    }

}
