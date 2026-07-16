package cn.iocoder.yudao.module.trade.service.fulfillment;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.FulfillmentOutboxEventDO;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.FulfillmentOutboxEventMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentPackageMapper;
import cn.iocoder.yudao.module.trade.enums.fulfillment.TrackingEventSourceEnum;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.dto.ProviderTrackingEvent;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.ApplyTrackingEventCommand;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Import({FulfillmentTrackingServiceImpl.class, VersionedTrackingStatusMapper.class,
        FulfillmentTrackingTransactionTest.TenantDbTestConfiguration.class})
class FulfillmentTrackingTransactionTest extends BaseDbUnitTest {

    private static final Long TENANT_ID = 121L;
    private static final Long OTHER_TENANT_ID = 122L;
    private static final Long ORDER_ID = 100L;
    private static final Long SHIPMENT_ID = 70001L;
    private static final Long PACKAGE_ID = 71001L;
    private static final Long LEG_ID = 72001L;
    private static final Long PROVIDER_ID = 83L;

    @Resource private FulfillmentTrackingService service;
    @Resource private DataSource dataSource;
    @SpyBean private FulfillmentOutboxEventMapper outboxMapper;
    @SpyBean private ShipmentPackageMapper packageMapper;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        LoginUser loginUser = new LoginUser().setId(110L).setTenantId(TENANT_ID).setUserType(1);
        SecurityFrameworkUtils.setLoginUser(loginUser, new MockHttpServletRequest());
        TenantContextHolder.setTenantId(TENANT_ID);
        seedAggregate("HANDED_TO_CARRIER", 1);
    }

    @Test
    void ingestionEstablishesCommandTenantWhenAmbientTenantIsAbsentAndRestoresIt() {
        mapping("MOVING", "IN_TRANSIT", "v1", 30, "2026-01-01 00:00:00.000000");
        TenantContextHolder.clear();

        TrackingApplyResult result = service.applyEvent(
                command("tenant-absent", "MOVING", Instant.parse("2026-07-15T01:00:00Z")));

        assertTrue(result.inserted());
        assertEquals("IN_TRANSIT", result.currentStatus());
        assertNull(TenantContextHolder.getTenantId());
    }

    @Test
    void ingestionOverridesMismatchedAmbientTenantAndRestoresIt() {
        mapping("MOVING", "IN_TRANSIT", "v1", 30, "2026-01-01 00:00:00.000000");
        TenantContextHolder.setTenantId(OTHER_TENANT_ID);

        TrackingApplyResult result = service.applyEvent(
                command("tenant-mismatch", "MOVING", Instant.parse("2026-07-15T01:05:00Z")));

        assertTrue(result.inserted());
        assertEquals("IN_TRANSIT", result.currentStatus());
        assertEquals(OTHER_TENANT_ID, TenantContextHolder.getTenantId());
    }

    @Test
    void rejectsSensitiveTrackingTextBeforeTimelinePersistence() {
        mapping("MOVING", "IN_TRANSIT", "v1", 30, "2026-01-01 00:00:00.000000");
        List<ProviderTrackingEvent> forbidden = List.of(
                providerEvent("pii-phone", "+1 (416) 555-0123", "Arrived at hub", "blob:evt-1"),
                providerEvent("pii-phone-prefix", "tel:4165550123", "Arrived at hub", "blob:evt-phone"),
                providerEvent("pii-email", "Toronto, ON", "Contact dispatch@example.com", "blob:evt-2"),
                providerEvent("pii-address", "123 Main Street, Toronto, ON M5V 2T6", "Arrived", "blob:evt-3"),
                providerEvent("pii-token", "Toronto, ON", "Authorization: Bearer abc.def.ghi", "blob:evt-4"),
                providerEvent("pii-json", "Toronto, ON", "Arrived", "{\"raw\":\"payload\"}"),
                providerEvent("pii-form", "Toronto, ON", "Arrived", "event=MOVING&location=Toronto"),
                providerEvent("pii-csv", "Toronto, ON", "Arrived", "event,location\nMOVING,Toronto"),
                providerEvent("pii-url-encoded", "Toronto, ON", "Arrived",
                        "%7B%22event%22%3A%22MOVING%22%7D"),
                providerEvent("pii-base64", "Toronto, ON", "Arrived", "eyJldmVudCI6Ik1PVklORyJ9"),
                providerEvent("pii-base64-form", "Toronto, ON", "Arrived",
                        "ZXZlbnQ9TU9WSU5HJmxvY2F0aW9uPVRvcm9udG8"),
                providerEvent("pii-url", "Toronto, ON", "Arrived",
                        "https://operator:password@example.com/payload"));

        for (ProviderTrackingEvent event : forbidden) {
            ApplyTrackingEventCommand command = command(event.externalEventId(), "MOVING", event.occurredAt())
                    .setProviderEvent(event);
            ServiceException failure = assertThrows(ServiceException.class, () -> service.applyEvent(command));
            assertEquals(1_011_009_012, failure.getCode());
            assertFalse(failure.getMessage().contains(event.externalEventId()));
            assertFalse(failure.getMessage().contains(event.rawPayloadRef()));
        }
        assertEquals(0, count("trade_tracking_event"));
    }

    @Test
    void acceptsDeidentifiedTrackingTextAndOpaqueReference() {
        mapping("MOVING", "IN_TRANSIT", "v1", 30, "2026-01-01 00:00:00.000000");
        Instant occurredAt = Instant.parse("2026-07-15T01:10:00Z");
        ApplyTrackingEventCommand command = command("safe-text", "MOVING", occurredAt)
                .setProviderEvent(providerEvent("safe-text", "Toronto, ON",
                        "Arrived at regional hub", "blob:evt-20260715-001"));

        TrackingApplyResult result = service.applyEvent(command);

        assertTrue(result.inserted());
        assertEquals("Toronto, ON", value("SELECT location FROM trade_tracking_event WHERE external_event_id = "
                + "'safe-text'", String.class));
        assertEquals("Arrived at regional hub", value("SELECT description FROM trade_tracking_event WHERE "
                + "external_event_id = 'safe-text'", String.class));
        assertEquals("blob:evt-20260715-001", value("SELECT raw_payload_ref FROM trade_tracking_event WHERE "
                + "external_event_id = 'safe-text'", String.class));
        ApplyTrackingEventCommand numericCommand = command("safe-numeric-reference", "MOVING",
                Instant.parse("2026-07-15T01:11:00Z"))
                .setProviderEvent(providerEvent("safe-numeric-reference", "Toronto, ON",
                        "Arrived at regional hub", "1234567890"));

        TrackingApplyResult numericResult = service.applyEvent(numericCommand);

        assertTrue(numericResult.inserted());
        assertEquals("1234567890", value("SELECT raw_payload_ref FROM trade_tracking_event WHERE "
                + "external_event_id = 'safe-numeric-reference'", String.class));

        ApplyTrackingEventCommand uuidCommand = command("safe-uuid-reference", "MOVING",
                Instant.parse("2026-07-15T01:12:00Z"))
                .setProviderEvent(providerEvent("safe-uuid-reference", "Toronto, ON",
                        "Arrived at regional hub", "123e4567-e89b-12d3-a456-426614174000"));

        TrackingApplyResult uuidResult = service.applyEvent(uuidCommand);

        assertTrue(uuidResult.inserted());
        assertEquals("123e4567-e89b-12d3-a456-426614174000", value("SELECT raw_payload_ref "
                + "FROM trade_tracking_event WHERE external_event_id = 'safe-uuid-reference'", String.class));
    }

    @AfterEach
    void clearContext() {
        reset(outboxMapper);
        reset(packageMapper);
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void knownMappingPersistsMicrosecondsWatermarksSummaryAndOutboxAtomically() {
        mapping("MOVING", "IN_TRANSIT", "v1", 30, "2026-01-01 00:00:00.000000");
        Instant occurredAt = Instant.parse("2026-07-15T02:03:04.123456789Z");

        TrackingApplyResult result = service.applyEvent(command("known-1", " moving ", occurredAt));

        assertTrue(result.inserted());
        assertTrue(result.stateChanged());
        assertEquals("HANDED_TO_CARRIER", result.previousStatus());
        assertEquals("IN_TRANSIT", result.currentStatus());
        assertEquals("IN_TRANSIT", value("SELECT status FROM trade_shipment WHERE id = " + SHIPMENT_ID, String.class));
        assertEquals(30, value("SELECT last_event_status_priority FROM trade_shipment WHERE id = " + SHIPMENT_ID,
                Integer.class));
        Long eventId = value("SELECT id FROM trade_tracking_event WHERE external_event_id = 'known-1'", Long.class);
        assertEquals(eventId, value("SELECT last_event_id FROM trade_shipment_package WHERE id = " + PACKAGE_ID,
                Long.class));
        assertEquals("2026-07-15 02:03:04.123456", value(
                "SELECT FORMATDATETIME(occurred_at, 'yyyy-MM-dd HH:mm:ss.SSSSSS') FROM trade_tracking_event WHERE id = "
                        + eventId, String.class));
        assertEquals("v1", value("SELECT mapping_version FROM trade_tracking_event WHERE id = " + eventId,
                String.class));
        assertEquals(1, count("trade_fulfillment_outbox_event"));
    }

    @Test
    void externalIdAndCanonicalHashUseMutuallyExclusiveIdentities() {
        mapping("MOVING", "IN_TRANSIT", "v1", 30, "2026-01-01 00:00:00.000000");
        Instant occurredAt = Instant.parse("2026-07-15T02:03:04.123456789Z");
        service.applyEvent(command("duplicate-1", "moving", occurredAt));
        int outboxCount = count("trade_fulfillment_outbox_event");

        TrackingApplyResult externalDuplicate = service.applyEvent(command(" duplicate-1 ", " MOVING ", occurredAt));
        TrackingApplyResult firstHashIdentity = service.applyEvent(command(null, "moving", occurredAt));
        TrackingApplyResult hashDuplicateNormalized = service.applyEvent(command(null, "  MOVING  ",
                Instant.parse("2026-07-15T02:03:04.123456001Z")));

        assertFalse(externalDuplicate.inserted());
        assertTrue(firstHashIdentity.inserted());
        assertFalse(hashDuplicateNormalized.inserted());
        assertEquals(firstHashIdentity.currentStatus(), hashDuplicateNormalized.currentStatus());
        assertEquals(2, count("trade_tracking_event"));
        assertEquals(outboxCount + 1, count("trade_fulfillment_outbox_event"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM trade_tracking_event "
                + "WHERE external_event_id = 'duplicate-1' AND event_hash IS NULL", Integer.class));
    }

    @Test
    void externalEventIdsAreCaseSensitiveAndDifferentIdsMayDescribeTheSameFact() {
        mapping("MOVING", "IN_TRANSIT", "v1", 30, "2026-01-01 00:00:00.000000");
        Instant occurredAt = Instant.parse("2026-07-15T02:30:00Z");

        assertTrue(service.applyEvent(command("Evt-A", "MOVING", occurredAt)).inserted());
        assertTrue(service.applyEvent(command("evt-a", "MOVING", occurredAt)).inserted());
        assertTrue(service.applyEvent(command("Evt-B", "MOVING", occurredAt)).inserted());

        assertEquals(3, count("trade_tracking_event"));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM trade_tracking_event WHERE event_hash IS NULL",
                Integer.class));
    }

    @Test
    void unknownMappingIsConservativeAndEmitsOnlySafeAlertPayload() {
        TrackingApplyResult result = service.applyEvent(command("unknown-1", "PRIVATE DELIVERED FLAG",
                Instant.parse("2026-07-15T03:00:00Z")));

        assertEquals("IN_TRANSIT", result.currentStatus());
        assertEquals(Boolean.FALSE, value("SELECT mapping_known FROM trade_tracking_event WHERE external_event_id = "
                + "'unknown-1'", Boolean.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM trade_fulfillment_outbox_event "
                + "WHERE event_type = 'TRACKING_STATUS_MAPPING_UNKNOWN'", Integer.class));
        String payload = value("SELECT payload FROM trade_fulfillment_outbox_event "
                + "WHERE event_type = 'TRACKING_STATUS_MAPPING_UNKNOWN'", String.class);
        assertFalse(payload.contains("PRIVATE"));
        assertFalse(payload.contains("private-tracking"));
        assertFalse(payload.contains("description"));
        assertFalse(payload.contains("location"));
    }

    @Test
    void lateAndLowerPrioritySameTimeEventsStayInTimelineWithoutRegression() {
        mapping("MOVING", "IN_TRANSIT", "v1", 30, "2026-01-01 00:00:00.000000");
        mapping("MOVING LOW", "IN_TRANSIT", "v1", 10, "2026-01-01 00:00:00.000000");
        mapping("EXCEPTION", "DELIVERY_EXCEPTION", "v1", 60, "2026-01-01 00:00:00.000000");
        Instant time = Instant.parse("2026-07-15T04:00:00Z");
        service.applyEvent(command("same-1", "MOVING", time));
        service.applyEvent(command("same-2", "EXCEPTION", time));
        TrackingApplyResult lower = service.applyEvent(command("same-3", "MOVING LOW", time));
        TrackingApplyResult late = service.applyEvent(command("late-1", "MOVING", time.minusSeconds(60)));

        assertEquals("DELIVERY_EXCEPTION", lower.currentStatus());
        assertFalse(lower.stateChanged());
        assertFalse(late.stateChanged());
        assertEquals("DELIVERY_EXCEPTION", value("SELECT status FROM trade_shipment WHERE id = " + SHIPMENT_ID,
                String.class));
        assertEquals(4, count("trade_tracking_event"));
    }

    @Test
    void replayUsesExactHistoricalMappingVersionAndMissingVersionFailsClosed() {
        mapping("MOVING", "IN_TRANSIT", "v1", 30, "2026-01-01 00:00:00.000000");
        mapping("MOVING", "DELIVERY_EXCEPTION", "v2", 60, "2026-07-01 00:00:00.000000");
        ApplyTrackingEventCommand replay = command("replay-1", "MOVING", Instant.parse("2026-07-15T05:00:00Z"))
                .setReplayMappingVersion("v1");

        TrackingApplyResult result = service.applyEvent(replay);

        assertEquals("IN_TRANSIT", result.currentStatus());
        assertEquals("v1", value("SELECT mapping_version FROM trade_tracking_event WHERE external_event_id = "
                + "'replay-1'", String.class));
        assertEquals(30, value("SELECT last_event_status_priority FROM trade_shipment_package WHERE id = "
                + PACKAGE_ID, Integer.class));
        assertThrows(IllegalArgumentException.class, () -> service.applyEvent(
                command("replay-missing", "MOVING", Instant.parse("2026-07-15T06:00:00Z"))
                        .setReplayMappingVersion("missing")));
        assertEquals(1, count("trade_tracking_event"));
    }

    @Test
    void exceptionCanRecoverOnNewerEventAndTerminalStateIsProtected() {
        mapping("MOVING", "IN_TRANSIT", "v1", 30, "2026-01-01 00:00:00.000000");
        mapping("EXCEPTION", "DELIVERY_EXCEPTION", "v1", 60, "2026-01-01 00:00:00.000000");
        service.applyEvent(command("exception-1", "EXCEPTION", Instant.parse("2026-07-15T06:30:00Z")));

        TrackingApplyResult recovered = service.applyEvent(
                command("recovery-1", "MOVING", Instant.parse("2026-07-15T06:31:00Z")));

        assertTrue(recovered.stateChanged());
        assertEquals("IN_TRANSIT", recovered.currentStatus());
        jdbc.update("UPDATE trade_shipment SET status = 'DELIVERED', version = version + 1 WHERE id = ?", SHIPMENT_ID);
        jdbc.update("UPDATE trade_shipment_package SET status = 'DELIVERED', version = version + 1 WHERE id = ?", PACKAGE_ID);
        jdbc.update("UPDATE trade_shipment_leg SET status = 'DELIVERED', version = version + 1 WHERE id = ?", LEG_ID);
        TrackingApplyResult terminal = service.applyEvent(
                command("terminal-1", "EXCEPTION", Instant.parse("2026-07-15T06:32:00Z")));
        assertFalse(terminal.stateChanged());
        assertEquals("DELIVERED", value("SELECT status FROM trade_shipment WHERE id = " + SHIPMENT_ID, String.class));
    }

    @Test
    void optimisticConflictRetriesCompleteTransactionAtMostThreeTimes() {
        mapping("MOVING", "IN_TRANSIT", "v1", 30, "2026-01-01 00:00:00.000000");
        doReturn(0, 0).doCallRealMethod().when(packageMapper).updateTrackingStateByIdAndVersion(
                anyLong(), anyLong(), anyInt(), anyString(), any(), anyInt(), anyLong());

        TrackingApplyResult result = service.applyEvent(
                command("retry-1", "MOVING", Instant.parse("2026-07-15T06:45:00Z")));

        assertTrue(result.inserted());
        assertEquals(1, count("trade_tracking_event"));
        verify(packageMapper, times(3)).updateTrackingStateByIdAndVersion(
                anyLong(), anyLong(), anyInt(), anyString(), any(), anyInt(), anyLong());
    }

    @Test
    void sameTimeSamePriorityUsesInternalEventIdAsStableWatermarkTieBreaker() {
        mapping("MOVING", "IN_TRANSIT", "v1", 30, "2026-01-01 00:00:00.000000");
        mapping("SAME RANK EXCEPTION", "DELIVERY_EXCEPTION", "v1", 30, "2026-01-01 00:00:00.000000");
        Instant occurredAt = Instant.parse("2026-07-15T06:50:00Z");
        service.applyEvent(command("tie-1", "MOVING", occurredAt));

        TrackingApplyResult second = service.applyEvent(command("tie-2", "SAME RANK EXCEPTION", occurredAt));

        Long secondEventId = value("SELECT id FROM trade_tracking_event WHERE external_event_id = 'tie-2'", Long.class);
        assertTrue(second.stateChanged());
        assertEquals("DELIVERY_EXCEPTION", second.currentStatus());
        assertEquals(secondEventId, value("SELECT last_event_id FROM trade_shipment_package WHERE id = "
                + PACKAGE_ID, Long.class));
        assertEquals(secondEventId, value("SELECT last_event_id FROM trade_shipment_leg WHERE id = "
                + LEG_ID, Long.class));
        assertEquals(secondEventId, value("SELECT last_event_id FROM trade_shipment WHERE id = "
                + SHIPMENT_ID, Long.class));
    }

    @Test
    void legCarrierAndTrackingTakePrecedenceOverPackageFacts() {
        jdbc.update("INSERT INTO trade_carrier (id, tenant_id, code, name, country_codes, status) "
                + "VALUES (74, ?, 'FEDEX', 'Second Carrier', 'US,CA', 0)", TENANT_ID);
        jdbc.update("UPDATE trade_shipment_leg SET carrier_id = 74, tracking_number = 'leg-private-789' WHERE id = ?",
                LEG_ID);
        jdbc.update("INSERT INTO trade_tracking_status_mapping (tenant_id, provider_code, carrier_code, "
                        + "provider_status_normalized, standard_status, mapping_version, status_priority, effective_at) "
                        + "VALUES (?, 'provider-a', 'FEDEX', 'MOVING', 'IN_TRANSIT', 'v1', 30, "
                        + "'2026-01-01 00:00:00.000000')", TENANT_ID);

        TrackingApplyResult result = service.applyEvent(
                command("leg-carrier-1", "MOVING", Instant.parse("2026-07-15T06:55:00Z")));

        assertTrue(result.stateChanged());
        assertEquals("IN_TRANSIT", result.currentStatus());
        assertEquals(1, count("trade_tracking_event"));
    }

    @Test
    void allPackagesDeliveredControlsShipmentAndOrderAggregation() {
        jdbc.update("UPDATE trade_shipment SET status = 'OUT_FOR_DELIVERY', version = 2 WHERE id = ?", SHIPMENT_ID);
        jdbc.update("UPDATE trade_shipment_package SET status = 'OUT_FOR_DELIVERY', version = 2 WHERE id = ?", PACKAGE_ID);
        jdbc.update("UPDATE trade_shipment_leg SET status = 'OUT_FOR_DELIVERY', version = 2 WHERE id = ?", LEG_ID);
        jdbc.update("INSERT INTO trade_shipment_package (id, tenant_id, shipment_id, package_no, package_type, "
                + "carrier_id, tracking_number, status, version) VALUES "
                + "(71002, ?, ?, 'PKG-2', 'PARCEL', 73, 'private-tracking-456', 'OUT_FOR_DELIVERY', 2)",
                TENANT_ID, SHIPMENT_ID);
        jdbc.update("INSERT INTO trade_shipment_leg (id, tenant_id, shipment_id, package_id, sequence_no, leg_type, "
                + "carrier_id, provider_id, tracking_number, status, version) VALUES "
                + "(72002, ?, ?, 71002, 2, 'LAST_MILE', 73, 83, 'private-tracking-456', 'OUT_FOR_DELIVERY', 2)",
                TENANT_ID, SHIPMENT_ID);
        mapping("DONE", "DELIVERED", "v1", 90, "2026-01-01 00:00:00.000000");

        service.applyEvent(command("done-1", "DONE", Instant.parse("2026-07-15T07:00:00Z")));
        assertEquals("OUT_FOR_DELIVERY", value("SELECT status FROM trade_shipment WHERE id = " + SHIPMENT_ID,
                String.class));
        ApplyTrackingEventCommand second = command("done-2", "DONE", Instant.parse("2026-07-15T07:01:00Z"))
                .setPackageId(71002L).setShipmentLegId(72002L);
        service.applyEvent(second);

        assertEquals("DELIVERED", value("SELECT status FROM trade_shipment WHERE id = " + SHIPMENT_ID, String.class));
        assertEquals("DELIVERED", value("SELECT status FROM trade_order_fulfillment_summary WHERE order_id = "
                + ORDER_ID, String.class));
        assertEquals(1, value("SELECT delivered_shipment_count FROM trade_order_fulfillment_summary WHERE order_id = "
                + ORDER_ID, Integer.class));
    }

    @Test
    void sameTimeDeliveredEventsOnlyEmitShipmentDeliveredWhenLastPackageTransitions() {
        jdbc.update("UPDATE trade_shipment SET status = 'OUT_FOR_DELIVERY', version = 2 WHERE id = ?", SHIPMENT_ID);
        jdbc.update("UPDATE trade_shipment_package SET status = 'OUT_FOR_DELIVERY', version = 2 WHERE id = ?", PACKAGE_ID);
        jdbc.update("UPDATE trade_shipment_leg SET status = 'OUT_FOR_DELIVERY', version = 2 WHERE id = ?", LEG_ID);
        jdbc.update("INSERT INTO trade_shipment_package (id, tenant_id, shipment_id, package_no, package_type, "
                + "carrier_id, tracking_number, status, version) VALUES "
                + "(71002, ?, ?, 'PKG-2', 'PARCEL', 73, 'private-tracking-456', 'OUT_FOR_DELIVERY', 2)",
                TENANT_ID, SHIPMENT_ID);
        jdbc.update("INSERT INTO trade_shipment_leg (id, tenant_id, shipment_id, package_id, sequence_no, leg_type, "
                + "carrier_id, provider_id, tracking_number, status, version) VALUES "
                + "(72002, ?, ?, 71002, 2, 'LAST_MILE', 73, 83, 'private-tracking-456', 'OUT_FOR_DELIVERY', 2)",
                TENANT_ID, SHIPMENT_ID);
        mapping("DONE", "DELIVERED", "v1", 90, "2026-01-01 00:00:00.000000");
        Instant sameTime = Instant.parse("2026-07-15T07:10:00Z");

        service.applyEvent(command("same-done-1", "DONE", sameTime));

        assertEquals("OUT_FOR_DELIVERY", value("SELECT status FROM trade_shipment WHERE id = " + SHIPMENT_ID,
                String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM trade_fulfillment_outbox_event "
                + "WHERE event_type = 'DELIVERED'", Integer.class));

        service.applyEvent(command("same-done-2", "DONE", sameTime)
                .setPackageId(71002L).setShipmentLegId(72002L));

        assertEquals("DELIVERED", value("SELECT status FROM trade_shipment WHERE id = " + SHIPMENT_ID, String.class));
        assertEquals("DELIVERED", value("SELECT status FROM trade_order_fulfillment_summary WHERE order_id = "
                + ORDER_ID, String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM trade_fulfillment_outbox_event "
                + "WHERE event_type = 'DELIVERED'", Integer.class));
    }

    @Test
    void returnedOutboxAlsoWaitsForWholeShipmentTransition() {
        jdbc.update("UPDATE trade_shipment SET status = 'RETURNING', version = 2 WHERE id = ?", SHIPMENT_ID);
        jdbc.update("UPDATE trade_shipment_package SET status = 'RETURNING', version = 2 WHERE id = ?", PACKAGE_ID);
        jdbc.update("UPDATE trade_shipment_leg SET status = 'RETURNING', version = 2 WHERE id = ?", LEG_ID);
        jdbc.update("INSERT INTO trade_shipment_package (id, tenant_id, shipment_id, package_no, package_type, "
                + "carrier_id, tracking_number, status, version) VALUES "
                + "(71002, ?, ?, 'PKG-2', 'PARCEL', 73, 'private-tracking-456', 'RETURNING', 2)",
                TENANT_ID, SHIPMENT_ID);
        jdbc.update("INSERT INTO trade_shipment_leg (id, tenant_id, shipment_id, package_id, sequence_no, leg_type, "
                + "carrier_id, provider_id, tracking_number, status, version) VALUES "
                + "(72002, ?, ?, 71002, 2, 'LAST_MILE', 73, 83, 'private-tracking-456', 'RETURNING', 2)",
                TENANT_ID, SHIPMENT_ID);
        mapping("RETURNED", "RETURNED", "v1", 100, "2026-01-01 00:00:00.000000");
        Instant sameTime = Instant.parse("2026-07-15T07:20:00Z");

        service.applyEvent(command("same-return-1", "RETURNED", sameTime));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM trade_fulfillment_outbox_event "
                + "WHERE event_type = 'RETURNED'", Integer.class));

        service.applyEvent(command("same-return-2", "RETURNED", sameTime)
                .setPackageId(71002L).setShipmentLegId(72002L));

        assertEquals("RETURNED", value("SELECT status FROM trade_shipment WHERE id = " + SHIPMENT_ID, String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM trade_fulfillment_outbox_event "
                + "WHERE event_type = 'RETURNED'", Integer.class));
    }

    @Test
    void shipmentLevelLegRequiresNoPackageAndOnlyUpdatesLegAndShipment() {
        mapping("MOVING", "IN_TRANSIT", "v1", 30, "2026-01-01 00:00:00.000000");
        jdbc.update("UPDATE trade_shipment_leg SET package_id = NULL WHERE id = ?", LEG_ID);

        TrackingApplyResult result = service.applyEvent(command("shipment-leg-1", "MOVING",
                Instant.parse("2026-07-15T07:30:00Z")).setPackageId(null));

        assertTrue(result.stateChanged());
        assertEquals("IN_TRANSIT", value("SELECT status FROM trade_shipment_leg WHERE id = " + LEG_ID, String.class));
        assertEquals("IN_TRANSIT", value("SELECT status FROM trade_shipment WHERE id = " + SHIPMENT_ID, String.class));
        assertEquals("HANDED_TO_CARRIER", value("SELECT status FROM trade_shipment_package WHERE id = " + PACKAGE_ID,
                String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM trade_tracking_event WHERE package_id IS NOT NULL",
                Integer.class));
    }

    @Test
    void boundLegDerivesPackageAndRejectsMismatchedCommandPackage() {
        mapping("MOVING", "IN_TRANSIT", "v1", 30, "2026-01-01 00:00:00.000000");

        TrackingApplyResult derived = service.applyEvent(command("derived-package-1", "MOVING",
                Instant.parse("2026-07-15T07:40:00Z")).setPackageId(null));

        assertTrue(derived.stateChanged());
        assertEquals(PACKAGE_ID, value("SELECT package_id FROM trade_tracking_event "
                + "WHERE external_event_id = 'derived-package-1'", Long.class));
        assertThrows(RuntimeException.class, () -> service.applyEvent(command("wrong-package-1", "MOVING",
                Instant.parse("2026-07-15T07:41:00Z")).setPackageId(999999L)));
        assertEquals(1, count("trade_tracking_event"));
    }

    @Test
    void packageOnlyEventRequiresShipmentProviderToMatchCommand() {
        mapping("MOVING", "IN_TRANSIT", "v1", 30, "2026-01-01 00:00:00.000000");
        jdbc.update("UPDATE trade_shipment SET provider_id = NULL WHERE id = ?", SHIPMENT_ID);

        assertThrows(RuntimeException.class, () -> service.applyEvent(command("no-provider-1", "MOVING",
                Instant.parse("2026-07-15T07:50:00Z")).setShipmentLegId(null)));

        jdbc.update("UPDATE trade_shipment SET provider_id = 999 WHERE id = ?", SHIPMENT_ID);
        assertThrows(RuntimeException.class, () -> service.applyEvent(command("wrong-provider-1", "MOVING",
                Instant.parse("2026-07-15T07:51:00Z")).setShipmentLegId(null)));
        assertEquals(0, count("trade_tracking_event"));
    }

    @Test
    void rejectedTerminalPackageEventCannotPolluteShipmentAggregation() {
        jdbc.update("UPDATE trade_shipment SET status = 'IN_TRANSIT', version = 2 WHERE id = ?", SHIPMENT_ID);
        jdbc.update("UPDATE trade_shipment_package SET status = 'DELIVERED', version = 2 WHERE id = ?", PACKAGE_ID);
        jdbc.update("UPDATE trade_shipment_leg SET status = 'DELIVERED', version = 2 WHERE id = ?", LEG_ID);
        jdbc.update("INSERT INTO trade_shipment_package (id, tenant_id, shipment_id, package_no, package_type, "
                + "carrier_id, tracking_number, status, version) VALUES "
                + "(71002, ?, ?, 'PKG-2', 'PARCEL', 73, 'private-tracking-456', 'IN_TRANSIT', 2)",
                TENANT_ID, SHIPMENT_ID);
        mapping("EXCEPTION", "DELIVERY_EXCEPTION", "v1", 60, "2026-01-01 00:00:00.000000");

        TrackingApplyResult result = service.applyEvent(command("delivered-package-exception", "EXCEPTION",
                Instant.parse("2026-07-15T07:55:00Z")));

        assertFalse(result.stateChanged());
        assertEquals("IN_TRANSIT", value("SELECT status FROM trade_shipment WHERE id = " + SHIPMENT_ID,
                String.class));
        assertEquals("SHIPPED", value("SELECT status FROM trade_order_fulfillment_summary WHERE order_id = "
                + ORDER_ID, String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM trade_fulfillment_outbox_event "
                + "WHERE event_type = 'DELIVERY_EXCEPTION'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM trade_fulfillment_outbox_event "
                + "WHERE event_type = 'TRACKING_UPDATED'", Integer.class));
    }

    @Test
    void canceledTargetCannotBorrowRawCandidateToAdvanceShipment() {
        jdbc.update("UPDATE trade_shipment SET status = 'HANDED_TO_CARRIER', version = 2 WHERE id = ?", SHIPMENT_ID);
        jdbc.update("UPDATE trade_shipment_package SET status = 'CANCELED', version = 2 WHERE id = ?", PACKAGE_ID);
        jdbc.update("UPDATE trade_shipment_leg SET status = 'CANCELED', version = 2 WHERE id = ?", LEG_ID);
        jdbc.update("INSERT INTO trade_shipment_package (id, tenant_id, shipment_id, package_no, package_type, "
                + "carrier_id, tracking_number, status, version) VALUES "
                + "(71002, ?, ?, 'PKG-2', 'PARCEL', 73, 'private-tracking-456', 'IN_TRANSIT', 2)",
                TENANT_ID, SHIPMENT_ID);
        mapping("MOVING", "IN_TRANSIT", "v1", 30, "2026-01-01 00:00:00.000000");
        mapping("EXCEPTION", "DELIVERY_EXCEPTION", "v1", 60, "2026-01-01 00:00:00.000000");

        TrackingApplyResult moving = service.applyEvent(command("canceled-moving", "MOVING",
                Instant.parse("2026-07-15T08:05:00Z")));
        TrackingApplyResult exception = service.applyEvent(command("canceled-exception", "EXCEPTION",
                Instant.parse("2026-07-15T08:06:00Z")));

        assertFalse(moving.stateChanged());
        assertFalse(exception.stateChanged());
        assertEquals("HANDED_TO_CARRIER", value("SELECT status FROM trade_shipment WHERE id = " + SHIPMENT_ID,
                String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM trade_fulfillment_outbox_event "
                + "WHERE event_type IN ('DELIVERY_EXCEPTION', 'OUT_FOR_DELIVERY', 'DELIVERED')", Integer.class));
    }

    @Test
    void rejectedShipmentLevelLegEventCannotDriveShipmentOrSummary() {
        jdbc.update("UPDATE trade_shipment SET status = 'IN_TRANSIT', version = 2 WHERE id = ?", SHIPMENT_ID);
        jdbc.update("UPDATE trade_shipment_leg SET package_id = NULL, status = 'DELIVERED', version = 2 WHERE id = ?",
                LEG_ID);
        mapping("EXCEPTION", "DELIVERY_EXCEPTION", "v1", 60, "2026-01-01 00:00:00.000000");

        TrackingApplyResult result = service.applyEvent(command("shared-leg-exception", "EXCEPTION",
                Instant.parse("2026-07-15T08:10:00Z")).setPackageId(null));

        assertFalse(result.stateChanged());
        assertEquals("DELIVERED", value("SELECT status FROM trade_shipment_leg WHERE id = " + LEG_ID, String.class));
        assertEquals("IN_TRANSIT", value("SELECT status FROM trade_shipment WHERE id = " + SHIPMENT_ID,
                String.class));
        assertEquals("SHIPPED", value("SELECT status FROM trade_order_fulfillment_summary WHERE order_id = "
                + ORDER_ID, String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM trade_fulfillment_outbox_event "
                + "WHERE event_type = 'DELIVERY_EXCEPTION'", Integer.class));
    }

    @Test
    void alreadyCanceledTargetCannotCancelShipmentWithAnotherActivePackage() {
        jdbc.update("UPDATE trade_shipment SET status = 'HANDED_TO_CARRIER', version = 2 WHERE id = ?", SHIPMENT_ID);
        jdbc.update("UPDATE trade_shipment_package SET status = 'CANCELED', version = 2 WHERE id = ?", PACKAGE_ID);
        jdbc.update("UPDATE trade_shipment_leg SET status = 'CANCELED', version = 2 WHERE id = ?", LEG_ID);
        jdbc.update("INSERT INTO trade_shipment_package (id, tenant_id, shipment_id, package_no, package_type, "
                + "carrier_id, tracking_number, status, version) VALUES "
                + "(71002, ?, ?, 'PKG-2', 'PARCEL', 73, 'private-tracking-456', 'IN_TRANSIT', 2)",
                TENANT_ID, SHIPMENT_ID);
        mapping("CANCELED", "CANCELED", "v1", 110, "2026-01-01 00:00:00.000000");

        TrackingApplyResult result = service.applyEvent(command("already-canceled", "CANCELED",
                Instant.parse("2026-07-15T08:15:00Z")));

        assertTrue(result.inserted());
        assertFalse(result.stateChanged());
        assertEquals("CANCELED", value("SELECT status FROM trade_shipment_package WHERE id = " + PACKAGE_ID,
                String.class));
        assertEquals("HANDED_TO_CARRIER", value("SELECT status FROM trade_shipment WHERE id = " + SHIPMENT_ID,
                String.class));
        assertEquals("SHIPPED", value("SELECT status FROM trade_order_fulfillment_summary WHERE order_id = "
                + ORDER_ID, String.class));
        assertEquals(1, count("trade_tracking_event"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM trade_fulfillment_outbox_event "
                + "WHERE event_type = 'TRACKING_UPDATED'", Integer.class));
        assertEquals(1, count("trade_fulfillment_outbox_event"));
    }

    @Test
    void newlyCanceledLastActivePackageExplicitlyCancelsShipment() {
        mapping("CANCELED", "CANCELED", "v1", 110, "2026-01-01 00:00:00.000000");

        TrackingApplyResult result = service.applyEvent(command("last-active-canceled", "CANCELED",
                Instant.parse("2026-07-15T08:20:00Z")));

        assertTrue(result.stateChanged());
        assertEquals("CANCELED", value("SELECT status FROM trade_shipment_package WHERE id = " + PACKAGE_ID,
                String.class));
        assertEquals("CANCELED", value("SELECT status FROM trade_shipment_leg WHERE id = " + LEG_ID, String.class));
        assertEquals("CANCELED", value("SELECT status FROM trade_shipment WHERE id = " + SHIPMENT_ID, String.class));
        assertEquals("NOT_SHIPPED", value("SELECT status FROM trade_order_fulfillment_summary WHERE order_id = "
                + ORDER_ID, String.class));
        assertEquals(0, value("SELECT shipment_count FROM trade_order_fulfillment_summary WHERE order_id = "
                + ORDER_ID, Integer.class));
        assertEquals(1, count("trade_fulfillment_outbox_event"));
    }

    @Test
    void outboxFailureRollsBackTimelineWatermarksStatesAndSummary() {
        mapping("MOVING", "IN_TRANSIT", "v1", 30, "2026-01-01 00:00:00.000000");
        doThrow(new IllegalStateException("outbox failed")).when(outboxMapper).insert(any(FulfillmentOutboxEventDO.class));

        assertThrows(IllegalStateException.class, () -> service.applyEvent(
                command("rollback-1", "MOVING", Instant.parse("2026-07-15T08:00:00Z"))));

        assertEquals(0, count("trade_tracking_event"));
        assertEquals("HANDED_TO_CARRIER", value("SELECT status FROM trade_shipment WHERE id = " + SHIPMENT_ID,
                String.class));
        assertEquals(1, value("SELECT version FROM trade_shipment WHERE id = " + SHIPMENT_ID, Integer.class));
        assertEquals("SHIPPED", value("SELECT status FROM trade_order_fulfillment_summary WHERE order_id = "
                + ORDER_ID, String.class));
    }

    private ApplyTrackingEventCommand command(String externalId, String rawStatus, Instant occurredAt) {
        return new ApplyTrackingEventCommand().setTenantId(TENANT_ID).setShipmentId(SHIPMENT_ID)
                .setPackageId(PACKAGE_ID).setShipmentLegId(LEG_ID).setProviderId(PROVIDER_ID)
                .setReceivedAt(occurredAt.plusSeconds(5)).setSource(TrackingEventSourceEnum.WEBHOOK)
                .setProviderEvent(new ProviderTrackingEvent(externalId, rawStatus, occurredAt, "UTC",
                        "Toronto ON", "private description", "private-payload-ref"));
    }

    private void seedAggregate(String status, int version) {
        jdbc.update("INSERT INTO trade_carrier (id, tenant_id, code, name, country_codes, status) "
                + "VALUES (73, ?, 'UPS', 'Carrier', 'US,CA', 0)", TENANT_ID);
        jdbc.update("INSERT INTO trade_logistics_provider (id, tenant_id, code, name, capabilities, status) "
                + "VALUES (?, ?, 'provider-a', 'Provider', 'TRACKING_QUERY', 0)", PROVIDER_ID, TENANT_ID);
        jdbc.update("INSERT INTO trade_shipment (id, tenant_id, order_id, shipment_no, shipment_type, status, "
                + "origin_country, destination_country, origin_timezone, destination_timezone, warehouse_id, provider_id, version) "
                + "VALUES (?, ?, ?, 'SHP-TRACK-1', 'PARCEL', ?, 'CA', 'CA', 'America/Toronto', "
                + "'America/Toronto', 31, ?, ?)", SHIPMENT_ID, TENANT_ID, ORDER_ID, status, PROVIDER_ID, version);
        jdbc.update("INSERT INTO trade_shipment_package (id, tenant_id, shipment_id, package_no, package_type, "
                + "carrier_id, tracking_number, status, version) VALUES "
                + "(?, ?, ?, 'PKG-1', 'PARCEL', 73, 'private-tracking-123', ?, ?)",
                PACKAGE_ID, TENANT_ID, SHIPMENT_ID, status, version);
        jdbc.update("INSERT INTO trade_shipment_leg (id, tenant_id, shipment_id, package_id, sequence_no, leg_type, "
                + "carrier_id, provider_id, tracking_number, status, version) VALUES "
                + "(?, ?, ?, ?, 1, 'LAST_MILE', 73, ?, 'private-tracking-123', ?, ?)",
                LEG_ID, TENANT_ID, SHIPMENT_ID, PACKAGE_ID, PROVIDER_ID, status, version);
        jdbc.update("INSERT INTO trade_order_fulfillment_summary (id, tenant_id, order_id, status, shipment_count, "
                + "delivered_shipment_count, version) VALUES (9001, ?, ?, 'SHIPPED', 1, 0, 1)", TENANT_ID, ORDER_ID);
    }

    private void mapping(String raw, String standard, String version, int priority, String effectiveAt) {
        jdbc.update("INSERT INTO trade_tracking_status_mapping (tenant_id, provider_code, carrier_code, "
                        + "provider_status_normalized, standard_status, mapping_version, status_priority, effective_at) "
                        + "VALUES (?, 'provider-a', 'UPS', ?, ?, ?, ?, ?)",
                TENANT_ID, raw, standard, version, priority, effectiveAt);
    }

    private int count(String table) {
        return value("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private <T> T value(String sql, Class<T> type) {
        return jdbc.queryForObject(sql, type);
    }

    private ProviderTrackingEvent providerEvent(String externalId, String location, String description,
                                                String rawPayloadRef) {
        return new ProviderTrackingEvent(externalId, "MOVING", Instant.parse("2026-07-15T01:10:00Z"), "UTC",
                location, description, rawPayloadRef);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TenantProperties.class)
    static class TenantDbTestConfiguration {

        @Bean
        @Lazy(false)
        TenantLineInnerInterceptor tenantLineInnerInterceptor(TenantProperties properties,
                                                               MybatisPlusInterceptor interceptor) {
            TenantLineInnerInterceptor inner =
                    new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(properties));
            MyBatisUtils.addInterceptor(interceptor, inner, 0);
            return inner;
        }

    }
}
