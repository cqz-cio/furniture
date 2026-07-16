package cn.iocoder.yudao.module.trade.service.fulfillment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo.ShipmentDetailRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo.ShipmentPageItemRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo.ShipmentPageReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo.TrackingEventRespVO;
import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum;
import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentTypeEnum;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentFeatureGuard;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentProperties;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_FEATURE_DISABLED;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_SHIPMENT_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import({FulfillmentQueryServiceImpl.class, FulfillmentFeatureGuard.class, FulfillmentProperties.class})
class FulfillmentQueryServiceImplTest extends BaseDbUnitTest {

    private static final Long TENANT_ID = 121L;
    private static final Long OTHER_TENANT_ID = 122L;
    private static final Long DETAIL_SHIPMENT_ID = 70002L;
    private static final Long EMPTY_SHIPMENT_ID = 70001L;
    private static final LocalDateTime CREATE_TIME = LocalDateTime.of(2026, 7, 16, 8, 0);

    @Resource
    private FulfillmentQueryService queryService;
    @Resource
    private DataSource dataSource;
    @Resource
    private FulfillmentProperties fulfillmentProperties;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        fulfillmentProperties.setEnabled(true);
        fulfillmentProperties.setReadFromNewModel(true);
        LoginUser loginUser = new LoginUser().setId(110L).setTenantId(TENANT_ID).setUserType(1);
        SecurityFrameworkUtils.setLoginUser(loginUser, new MockHttpServletRequest());
        jdbc = new JdbcTemplate(dataSource);
        seedShipments();
        seedDetailChildren();
    }

    @Test
    void adminQueriesRemainAvailableWhenLegacyProjectionReadIsDisabled() {
        fulfillmentProperties.setReadFromNewModel(false);
        try {
            assertEquals(DETAIL_SHIPMENT_ID, queryService.getShipment(TENANT_ID, DETAIL_SHIPMENT_ID).getId());
            assertEquals(3, queryService.getTimeline(TENANT_ID, DETAIL_SHIPMENT_ID).size());
            assertEquals(2L, queryService.getShipmentPage(TENANT_ID, pageRequest()).getTotal());
        } finally {
            fulfillmentProperties.setReadFromNewModel(true);
        }
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getShipment_masksIdentifiersAndReturnsOrderedChildren() {
        ShipmentDetailRespVO response = queryService.getShipment(TENANT_ID, DETAIL_SHIPMENT_ID);

        assertEquals(DETAIL_SHIPMENT_ID, response.getId());
        assertEquals(ShipmentTypeEnum.LTL, response.getShipmentType());
        assertEquals(List.of(101L, 102L), response.getItems().stream().map(item -> item.getId()).toList());
        assertEquals(List.of(701L, 702L), response.getPackages().stream().map(item -> item.getId()).toList());
        assertEquals("***9999", response.getPackages().get(0).getTrackingNumberMasked());
        assertNull(response.getPackages().get(1).getTrackingNumberMasked());
        assertEquals(List.of(711L, 712L), response.getLegs().stream().map(item -> item.getId()).toList());
        assertEquals("***AB", response.getLegs().get(0).getTrackingNumberMasked());
        assertEquals("***1234", response.getLegs().get(0).getProNumberMasked());
        assertEquals("***9999", response.getLegs().get(0).getBolNumberMasked());

        String json = JsonUtils.toJsonString(response);
        assertDoesNotExposeCommonInternals(json);
        assertFalse(json.contains("\"trackingNumber\":"));
        assertFalse(json.contains("\"proNumber\":"));
        assertFalse(json.contains("\"bolNumber\":"));
        assertFalse(json.contains("\"originLocation\":"));
        assertFalse(json.contains("\"destinationLocation\":"));
        assertFalse(json.contains("private-tracking"));
        assertFalse(json.contains("private origin"));
    }

    @Test
    void getShipment_returnsEmptyChildListsInsteadOfNull() {
        ShipmentDetailRespVO response = queryService.getShipment(TENANT_ID, EMPTY_SHIPMENT_ID);

        assertNotNull(response.getItems());
        assertNotNull(response.getPackages());
        assertNotNull(response.getLegs());
        assertTrue(response.getItems().isEmpty());
        assertTrue(response.getPackages().isEmpty());
        assertTrue(response.getLegs().isEmpty());
    }

    @Test
    void getShipment_rejectsCrossTenantLikeMissingShipment() {
        assertServiceException(() -> queryService.getShipment(OTHER_TENANT_ID, DETAIL_SHIPMENT_ID),
                FULFILLMENT_SHIPMENT_NOT_FOUND);
        assertServiceException(() -> queryService.getShipment(TENANT_ID, 99999L),
                FULFILLMENT_SHIPMENT_NOT_FOUND);
    }

    @Test
    void getTimeline_requiresTenantScopedParentAndOrdersByOccurredAtThenId() {
        List<TrackingEventRespVO> timeline = queryService.getTimeline(TENANT_ID, DETAIL_SHIPMENT_ID);

        assertEquals(List.of(91001L, 91003L, 91002L), timeline.stream().map(TrackingEventRespVO::getId).toList());
        assertServiceException(() -> queryService.getTimeline(OTHER_TENANT_ID, DETAIL_SHIPMENT_ID),
                FULFILLMENT_SHIPMENT_NOT_FOUND);
        assertServiceException(() -> queryService.getTimeline(TENANT_ID, 99999L),
                FULFILLMENT_SHIPMENT_NOT_FOUND);
    }

    @Test
    void getTimeline_exposesOnlyNormalizedProviderStatus() {
        List<TrackingEventRespVO> timeline = queryService.getTimeline(TENANT_ID, DETAIL_SHIPMENT_ID);

        assertEquals(List.of("MANUAL_NORMALIZED", "PICKED_UP_NORMALIZED", "MOVING_NORMALIZED"),
                timeline.stream().map(TrackingEventRespVO::getProviderStatusNormalized).toList());
        String json = JsonUtils.toJsonString(timeline);
        assertDoesNotExposeCommonInternals(json);
        assertFalse(json.contains("\"providerStatus\":"));
        assertFalse(json.contains("\"providerId\":"));
        assertFalse(json.contains("\"externalEventId\":"));
        assertFalse(json.contains("\"eventHash\":"));
        assertFalse(json.contains("\"description\":"));
        assertFalse(json.contains("\"location\":"));
        assertFalse(json.contains("\"rawPayloadRef\":"));
        assertFalse(json.contains("\"manualOperatorId\":"));
        assertFalse(json.contains("\"manualReason\":"));
        assertFalse(json.contains("\"requestTraceId\":"));
        assertFalse(json.contains("raw-provider-secret"));
        assertFalse(json.contains("private location"));
        assertFalse(json.contains("manual correction secret"));
    }

    @Test
    void getShipmentPage_isTenantScopedAndStableForEqualCreateTime() {
        PageResult<ShipmentPageItemRespVO> page = queryService.getShipmentPage(TENANT_ID, pageRequest());

        assertEquals(2L, page.getTotal());
        assertEquals(List.of(DETAIL_SHIPMENT_ID, EMPTY_SHIPMENT_ID),
                page.getList().stream().map(ShipmentPageItemRespVO::getId).toList());
        String json = JsonUtils.toJsonString(page);
        assertDoesNotExposeCommonInternals(json);
        assertFalse(json.contains("SHP-OTHER"));
    }

    @Test
    void getShipmentPage_filtersByOrderId() {
        ShipmentPageReqVO request = pageRequest();
        request.setOrderId(100L);
        assertPageContainsOnly(request, EMPTY_SHIPMENT_ID);
    }

    @Test
    void getShipmentPage_filtersByShipmentNo() {
        ShipmentPageReqVO request = pageRequest();
        request.setShipmentNo("SHP-DETAIL");
        assertPageContainsOnly(request, DETAIL_SHIPMENT_ID);
    }

    @Test
    void getShipmentPage_filtersByShipmentType() {
        ShipmentPageReqVO request = pageRequest();
        request.setShipmentType(ShipmentTypeEnum.LTL);
        assertPageContainsOnly(request, DETAIL_SHIPMENT_ID);
    }

    @Test
    void getShipmentPage_filtersByStatus() {
        ShipmentPageReqVO request = pageRequest();
        request.setStatus(ShipmentStatusEnum.IN_TRANSIT);
        assertPageContainsOnly(request, DETAIL_SHIPMENT_ID);
    }

    @Test
    void getShipmentPage_filtersByOriginCountry() {
        ShipmentPageReqVO request = pageRequest();
        request.setOriginCountry("CA");
        assertPageContainsOnly(request, DETAIL_SHIPMENT_ID);
    }

    @Test
    void getShipmentPage_filtersByDestinationCountry() {
        ShipmentPageReqVO request = pageRequest();
        request.setDestinationCountry("CA");
        assertPageContainsOnly(request, DETAIL_SHIPMENT_ID);
    }

    @Test
    void getShipmentPage_filtersByCreateTimeRange() {
        ShipmentPageReqVO request = pageRequest();
        request.setCreateTime(new LocalDateTime[]{CREATE_TIME.minusMinutes(1), CREATE_TIME.plusMinutes(1)});
        assertEquals(2L, queryService.getShipmentPage(TENANT_ID, request).getTotal());

        request.setCreateTime(new LocalDateTime[]{CREATE_TIME.plusMinutes(1), CREATE_TIME.plusMinutes(2)});
        assertTrue(queryService.getShipmentPage(TENANT_ID, request).getList().isEmpty());
    }

    @Test
    void maskIdentifier_handlesNullBlankShortAndLongValues() {
        assertNull(FulfillmentQueryServiceImpl.maskIdentifier(null));
        assertNull(FulfillmentQueryServiceImpl.maskIdentifier(""));
        assertNull(FulfillmentQueryServiceImpl.maskIdentifier("   "));
        assertEquals("***AB", FulfillmentQueryServiceImpl.maskIdentifier("AB"));
        assertEquals("***1234", FulfillmentQueryServiceImpl.maskIdentifier("1234"));
        assertEquals("***9999", FulfillmentQueryServiceImpl.maskIdentifier("1Z999999"));
    }

    private ShipmentPageReqVO pageRequest() {
        ShipmentPageReqVO request = new ShipmentPageReqVO();
        request.setPageNo(1);
        request.setPageSize(10);
        return request;
    }

    private void assertPageContainsOnly(ShipmentPageReqVO request, Long id) {
        PageResult<ShipmentPageItemRespVO> page = queryService.getShipmentPage(TENANT_ID, request);
        assertEquals(1L, page.getTotal());
        assertEquals(List.of(id), page.getList().stream().map(ShipmentPageItemRespVO::getId).toList());
    }

    private static void assertDoesNotExposeCommonInternals(String json) {
        for (String property : List.of("tenantId", "creator", "updater", "deleted",
                "lastEventOccurredAt", "lastEventId", "idempotencyKeyHash")) {
            assertFalse(json.contains("\"" + property + "\":"), property + " must not be exposed");
        }
    }

    private void seedShipments() {
        insertShipment(EMPTY_SHIPMENT_ID, TENANT_ID, 100L, "SHP-EMPTY", "PARCEL", "DRAFT",
                "US", 31L, 81L, CREATE_TIME);
        insertShipment(DETAIL_SHIPMENT_ID, TENANT_ID, 200L, "SHP-DETAIL", "LTL", "IN_TRANSIT",
                "CA", 32L, 82L, CREATE_TIME);
        insertShipment(80001L, OTHER_TENANT_ID, 300L, "SHP-OTHER", "WHITE_GLOVE", "DELIVERED",
                "US", 33L, 83L, CREATE_TIME.plusDays(1));
    }

    private void insertShipment(Long id, Long tenantId, Long orderId, String shipmentNo, String shipmentType,
                                String status, String country, Long warehouseId, Long providerId,
                                LocalDateTime createTime) {
        jdbc.update("INSERT INTO trade_shipment (id, tenant_id, order_id, shipment_no, shipment_type, status, "
                        + "origin_country, destination_country, origin_timezone, destination_timezone, warehouse_id, "
                        + "provider_id, estimated_delivery_at, delivered_at, version, create_time, update_time) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'America/Toronto', 'America/Toronto', ?, ?, ?, ?, 3, ?, ?)",
                id, tenantId, orderId, shipmentNo, shipmentType, status, country, country, warehouseId, providerId,
                createTime.plusDays(2), "DELIVERED".equals(status) ? createTime.plusDays(1) : null,
                createTime, createTime.plusHours(1));
    }

    private void seedDetailChildren() {
        jdbc.update("INSERT INTO trade_shipment_item "
                        + "(id, tenant_id, shipment_id, order_item_id, sku_id, quantity) VALUES "
                        + "(102, ?, ?, 502, 602, 2.000000), (101, ?, ?, 501, 601, 1.000000)",
                TENANT_ID, DETAIL_SHIPMENT_ID, TENANT_ID, DETAIL_SHIPMENT_ID);
        jdbc.update("INSERT INTO trade_shipment_package "
                        + "(id, tenant_id, shipment_id, package_no, package_type, carrier_id, tracking_number, "
                        + "weight, weight_unit, length, width, height, dimension_unit, status, version) VALUES "
                        + "(702, ?, ?, 'PKG-2', 'PALLET', 72, '', 20, 'LB', 2, 3, 4, 'IN', 'DRAFT', 0), "
                        + "(701, ?, ?, 'PKG-1', 'PALLET', 71, '1Z999999', 10, 'LB', 1, 2, 3, 'IN', 'IN_TRANSIT', 1)",
                TENANT_ID, DETAIL_SHIPMENT_ID, TENANT_ID, DETAIL_SHIPMENT_ID);
        jdbc.update("INSERT INTO trade_shipment_leg "
                        + "(id, tenant_id, shipment_id, package_id, sequence_no, leg_type, carrier_id, provider_id, "
                        + "service_level, tracking_number, pro_number, bol_number, origin_location, "
                        + "destination_location, status, started_at, completed_at, version) VALUES "
                        + "(712, ?, ?, 702, 2, 'LAST_MILE', 72, 82, 'WHITE_GLOVE', 'private-tracking-2', NULL, NULL, "
                        + "'private origin 2', 'private destination 2', 'DRAFT', NULL, NULL, 0), "
                        + "(711, ?, ?, 701, 1, 'LTL', 71, 82, 'GROUND', 'AB', '1234', '1Z999999', "
                        + "'private origin 1', 'private destination 1', 'IN_TRANSIT', ?, NULL, 1)",
                TENANT_ID, DETAIL_SHIPMENT_ID, TENANT_ID, DETAIL_SHIPMENT_ID, CREATE_TIME);
        insertTrackingEvent(91002L, "provider-event-2", null, "IN_TRANSIT", "raw-provider-secret-2",
                "MOVING_NORMALIZED", "v2", true, "APPLIED", "DRAFT", "IN_TRANSIT",
                CREATE_TIME.plusHours(3), "WEBHOOK", null, null, null);
        insertTrackingEvent(91001L, "manual-event-digest", null, "IN_TRANSIT", "MANUAL",
                "MANUAL_NORMALIZED", null, true, "APPLIED", "DRAFT", "IN_TRANSIT",
                CREATE_TIME.plusHours(2), "MANUAL", 110L, "manual correction secret", "trace-secret");
        insertTrackingEvent(91003L, null, "event-hash-secret", "HANDED_TO_CARRIER", "raw-provider-secret-1",
                "PICKED_UP_NORMALIZED", "v1", true, "APPLIED", "DRAFT", "HANDED_TO_CARRIER",
                CREATE_TIME.plusHours(2), "POLLING", null, null, null);
    }

    private void insertTrackingEvent(Long id, String externalEventId, String eventHash, String standardStatus,
                                     String providerStatus, String normalized, String mappingVersion,
                                     Boolean mappingKnown, String transitionDecision, String previousStatus,
                                     String resultStatus, LocalDateTime occurredAt, String source,
                                     Long operatorId, String reason, String traceId) {
        jdbc.update("INSERT INTO trade_tracking_event (id, tenant_id, shipment_id, package_id, shipment_leg_id, "
                        + "provider_id, external_event_id, event_hash, standard_status, provider_status, "
                        + "provider_status_normalized, mapping_version, mapping_known, transition_decision, "
                        + "previous_status, result_status, description, location, occurred_at, occurred_timezone, "
                        + "received_at, raw_payload_ref, source, manual_operator_id, manual_reason, request_trace_id) "
                        + "VALUES (?, ?, ?, 701, 711, 82, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'private description', "
                        + "'private location', ?, 'America/Toronto', ?, 'private payload', ?, ?, ?, ?)",
                id, TENANT_ID, DETAIL_SHIPMENT_ID, externalEventId, eventHash, standardStatus, providerStatus,
                normalized, mappingVersion, mappingKnown, transitionDecision, previousStatus, resultStatus,
                occurredAt, occurredAt.plusMinutes(1), source, operatorId, reason, traceId);
    }

}
