package cn.iocoder.yudao.module.trade.service.fulfillment;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.module.trade.service.fulfillment.FulfillmentLegacyProjectionResult.Mode.AUTHORITATIVE_EMPTY;
import static cn.iocoder.yudao.module.trade.service.fulfillment.FulfillmentLegacyProjectionResult.Mode.AUTHORITATIVE_EVENTS;
import static cn.iocoder.yudao.module.trade.service.fulfillment.FulfillmentLegacyProjectionResult.Mode.FALLBACK;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Import(FulfillmentLegacyProjectionServiceImpl.class)
class FulfillmentLegacyProjectionMapperIntegrationTest extends BaseDbUnitTest {

    private static final long TENANT_ID = 121L;
    private static final long OTHER_TENANT_ID = 122L;
    private static final long ORDER_ID = 87001L;
    private static final long SHIPMENT_ID = 87100L;
    private static final long PACKAGE_ID = 87200L;
    private static final LocalDateTime EARLY = LocalDateTime.of(2026, 7, 16, 8, 0);
    private static final LocalDateTime LATE = EARLY.plusHours(1);

    @Resource
    private FulfillmentLegacyProjectionService projectionService;
    @Resource
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void realMappersEnforceSubjectBoundariesAndStableTimelineOrdering() {
        long foreignShipmentId = SHIPMENT_ID - 100;
        long foreignPackageId = PACKAGE_ID - 100;
        seedShipment(TENANT_ID, foreignShipmentId, ORDER_ID + 1, "IN_TRANSIT");
        seedPackage(TENANT_ID, foreignPackageId, foreignShipmentId, "PKG-FOREIGN-ORDER");
        seedEvent(87390L, TENANT_ID, foreignShipmentId, foreignPackageId, null, "EXCEPTION", EARLY);

        seedShipment(TENANT_ID, SHIPMENT_ID, ORDER_ID, "IN_TRANSIT");
        seedPackage(TENANT_ID, PACKAGE_ID, SHIPMENT_ID, "PKG-TARGET");
        seedPackage(TENANT_ID, PACKAGE_ID + 1, SHIPMENT_ID, "PKG-SIBLING");
        seedLeg(TENANT_ID, 87300L, SHIPMENT_ID, PACKAGE_ID);

        // Nullable package/leg means shipment-level and is intentionally visible to the selected subject.
        seedEvent(87400L, TENANT_ID, SHIPMENT_ID, null, null, "IN_TRANSIT", EARLY);
        seedEvent(87402L, TENANT_ID, SHIPMENT_ID, PACKAGE_ID, 87300L, "DELIVERED", LATE);
        seedEvent(87401L, TENANT_ID, SHIPMENT_ID, PACKAGE_ID, 87300L, "OUT_FOR_DELIVERY", EARLY);

        // These rows deliberately reuse plausible subject identifiers but must never cross a boundary.
        seedEvent(87410L, TENANT_ID, SHIPMENT_ID, PACKAGE_ID + 1, null, "EXCEPTION", EARLY);
        seedEvent(87411L, TENANT_ID, SHIPMENT_ID + 1, PACKAGE_ID, null, "EXCEPTION", EARLY);
        seedEvent(87412L, OTHER_TENANT_ID, SHIPMENT_ID, PACKAGE_ID, null, "EXCEPTION", EARLY);

        FulfillmentLegacyProjectionResult result = projectionService.project(TENANT_ID, ORDER_ID);

        assertEquals(AUTHORITATIVE_EVENTS, result.mode());
        assertEquals(List.of("IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED"),
                result.events().stream().map(event -> event.getContent()).toList());
        assertEquals(List.of(EARLY, EARLY, LATE),
                result.events().stream().map(event -> event.getTime()).toList());
    }

    @Test
    void realMappersSelectPackageLessLtlLegWithoutCrossingTenantShipmentOrLeg() {
        long targetLegId = 87500L;
        long crossTenantLegId = targetLegId - 2;
        long crossShipmentLegId = targetLegId - 1;
        long wrongLegId = targetLegId + 1;
        long otherShipmentId = SHIPMENT_ID + 1;

        seedShipment(TENANT_ID, SHIPMENT_ID, ORDER_ID, "IN_TRANSIT", "LTL");
        seedShipment(TENANT_ID, otherShipmentId, ORDER_ID + 1, "IN_TRANSIT", "LTL");
        seedLeg(OTHER_TENANT_ID, crossTenantLegId, SHIPMENT_ID, null);
        seedLeg(TENANT_ID, crossShipmentLegId, otherShipmentId, null);
        seedLeg(TENANT_ID, targetLegId, SHIPMENT_ID, null);

        seedEvent(87600L, TENANT_ID, SHIPMENT_ID, null, targetLegId, "IN_TRANSIT", EARLY);
        seedEvent(87601L, TENANT_ID, SHIPMENT_ID, null, targetLegId, "OUT_FOR_DELIVERY", LATE);

        // Each adversary is valid-looking in isolation, but differs on one required subject boundary.
        seedEvent(87610L, OTHER_TENANT_ID, SHIPMENT_ID, null, crossTenantLegId, "EXCEPTION", EARLY);
        seedEvent(87611L, TENANT_ID, otherShipmentId, null, crossShipmentLegId, "EXCEPTION", EARLY);
        seedEvent(87612L, TENANT_ID, SHIPMENT_ID, null, wrongLegId, "EXCEPTION", EARLY);

        FulfillmentLegacyProjectionResult result = projectionService.project(TENANT_ID, ORDER_ID);

        assertEquals(AUTHORITATIVE_EVENTS, result.mode());
        assertEquals(List.of("IN_TRANSIT", "OUT_FOR_DELIVERY"),
                result.events().stream().map(event -> event.getContent()).toList());
        assertEquals(List.of(EARLY, LATE),
                result.events().stream().map(event -> event.getTime()).toList());
    }

    @Test
    void realMappersReturnFallbackWhenShipmentOrSubjectEventsAreAbsent() {
        assertEquals(FALLBACK, projectionService.project(TENANT_ID, ORDER_ID).mode());

        seedShipment(TENANT_ID, SHIPMENT_ID, ORDER_ID, "IN_TRANSIT");
        seedPackage(TENANT_ID, PACKAGE_ID, SHIPMENT_ID, "PKG-TARGET");
        seedLeg(TENANT_ID, 87300L, SHIPMENT_ID, PACKAGE_ID);

        assertEquals(FALLBACK, projectionService.project(TENANT_ID, ORDER_ID).mode());
    }

    @Test
    void realMappersMakeUnsafeExistingEventsAuthoritativeEmpty() {
        seedShipment(TENANT_ID, SHIPMENT_ID, ORDER_ID, "IN_TRANSIT");
        seedPackage(TENANT_ID, PACKAGE_ID, SHIPMENT_ID, "PKG-TARGET");
        seedLeg(TENANT_ID, 87300L, SHIPMENT_ID, PACKAGE_ID);
        seedEvent(87400L, TENANT_ID, SHIPMENT_ID, PACKAGE_ID, 87300L,
                "PROVIDER_PRIVATE_STATUS", EARLY);

        FulfillmentLegacyProjectionResult result = projectionService.project(TENANT_ID, ORDER_ID);

        assertEquals(AUTHORITATIVE_EMPTY, result.mode());
        assertEquals(List.of(), result.events());
    }

    private void seedShipment(long tenantId, long shipmentId, long orderId, String status) {
        seedShipment(tenantId, shipmentId, orderId, status, "PARCEL");
    }

    private void seedShipment(long tenantId, long shipmentId, long orderId, String status, String shipmentType) {
        jdbc.update("INSERT INTO trade_shipment (id, tenant_id, order_id, shipment_no, shipment_type, status, "
                        + "origin_country, destination_country, origin_timezone, destination_timezone, warehouse_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'CA', 'CA', 'America/Toronto', "
                        + "'America/Toronto', ?)",
                shipmentId, tenantId, orderId, "S-" + tenantId + "-" + shipmentId, shipmentType, status,
                shipmentId + 9000);
    }

    private void seedPackage(long tenantId, long packageId, long shipmentId, String packageNo) {
        jdbc.update("INSERT INTO trade_shipment_package (id, tenant_id, shipment_id, package_no, package_type, status) "
                        + "VALUES (?, ?, ?, ?, 'BOX', 'IN_TRANSIT')",
                packageId, tenantId, shipmentId, packageNo);
    }

    private void seedLeg(long tenantId, long legId, long shipmentId, Long packageId) {
        jdbc.update("INSERT INTO trade_shipment_leg (id, tenant_id, shipment_id, package_id, sequence_no, leg_type, "
                        + "carrier_id, provider_id, status) VALUES (?, ?, ?, ?, 1, 'LAST_MILE', 1, 1, 'IN_TRANSIT')",
                legId, tenantId, shipmentId, packageId);
    }

    private void seedEvent(long id, long tenantId, long shipmentId, Long packageId, Long legId,
                           String status, LocalDateTime occurredAt) {
        jdbc.update("INSERT INTO trade_tracking_event (id, tenant_id, shipment_id, package_id, shipment_leg_id, "
                        + "provider_id, external_event_id, standard_status, provider_status, occurred_at, received_at, "
                        + "source) VALUES (?, ?, ?, ?, ?, 1, ?, ?, 'SAFE_PROVIDER_STATUS', ?, ?, 'POLL')",
                id, tenantId, shipmentId, packageId, legId, "EVENT-" + tenantId + "-" + id,
                status, occurredAt, occurredAt.plusMinutes(1));
    }

}
