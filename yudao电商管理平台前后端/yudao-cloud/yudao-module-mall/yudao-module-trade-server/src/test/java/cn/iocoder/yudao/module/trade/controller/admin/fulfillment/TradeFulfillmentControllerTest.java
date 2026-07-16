package cn.iocoder.yudao.module.trade.controller.admin.fulfillment;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.monitor.TracerUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo.*;
import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum;
import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentTypeEnum;
import cn.iocoder.yudao.module.trade.service.fulfillment.*;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.beans.Introspector;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TradeFulfillmentControllerTest {

    private static final Long TENANT_ID = 121L;
    private static final Long OPERATOR_ID = 9001L;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private FulfillmentCommandService commandService;
    private FulfillmentTrackingService trackingService;
    private FulfillmentQueryService queryService;
    private TradeFulfillmentController controller;

    @BeforeEach
    void setUp() {
        commandService = mock(FulfillmentCommandService.class);
        trackingService = mock(FulfillmentTrackingService.class);
        queryService = mock(FulfillmentQueryService.class);
        controller = new TradeFulfillmentController();
        ReflectionTestUtils.setField(controller, "commandService", commandService);
        ReflectionTestUtils.setField(controller, "trackingService", trackingService);
        ReflectionTestUtils.setField(controller, "queryService", queryService);
        TenantContextHolder.setTenantId(TENANT_ID);
        LoginUser loginUser = new LoginUser();
        loginUser.setId(OPERATOR_ID);
        loginUser.setUserType(2);
        SecurityFrameworkUtils.setLoginUser(loginUser, new MockHttpServletRequest());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void exposesExactlyNineMappingsAndFivePermissions() {
        List<Method> methods = endpointMethods();
        assertEquals(9, methods.size());
        assertEquals(6, methods.stream().filter(TradeFulfillmentControllerTest::isWrite).count());
        assertEquals(3, methods.stream().filter(m -> m.isAnnotationPresent(GetMapping.class)).count());
        assertEquals(Set.of("POST ", "PUT /{id}/ready", "POST /{id}/packages", "POST /{id}/legs",
                        "POST /{id}/dispatch", "POST /{id}/manual-event", "GET /{id}",
                        "GET /{id}/timeline", "GET /page"),
                methods.stream().map(TradeFulfillmentControllerTest::routeKey).collect(Collectors.toSet()));

        Set<String> permissions = methods.stream()
                .map(m -> m.getAnnotation(PreAuthorize.class))
                .filter(Objects::nonNull).map(PreAuthorize::value).collect(Collectors.toSet());
        assertEquals(Set.of(
                "@ss.hasPermission('trade:fulfillment:shipment:create')",
                "@ss.hasPermission('trade:fulfillment:shipment:update')",
                "@ss.hasPermission('trade:fulfillment:shipment:dispatch')",
                "@ss.hasPermission('trade:fulfillment:tracking:manual')",
                "@ss.hasPermission('trade:fulfillment:shipment:query')"), permissions);
    }

    @Test
    void everyWriteMappingRequiresIdempotencyKeyAndDisablesRequestLogging() {
        List<Method> writes = endpointMethods().stream().filter(TradeFulfillmentControllerTest::isWrite).toList();
        assertEquals(6, writes.size());
        for (Method method : writes) {
            assertNotNull(method.getAnnotation(PreAuthorize.class), method.getName());
            ApiAccessLog accessLog = method.getAnnotation(ApiAccessLog.class);
            assertNotNull(accessLog, method.getName());
            assertTrue(accessLog.enable(), method.getName());
            assertFalse(accessLog.requestEnable(), method.getName());
            assertFalse(accessLog.responseEnable(), method.getName());
            List<Parameter> headers = Arrays.stream(method.getParameters())
                    .filter(p -> p.isAnnotationPresent(RequestHeader.class)).toList();
            assertEquals(1, headers.size(), method.getName());
            Parameter header = headers.get(0);
            assertEquals("Idempotency-Key", header.getAnnotation(RequestHeader.class).name());
            assertNotNull(header.getAnnotation(NotBlank.class));
            assertEquals(128, header.getAnnotation(Size.class).max());

            Object[] arguments = new Object[method.getParameterCount()];
            arguments[headers.indexOf(header)] = " ";
            assertFalse(validator.forExecutables().validateParameters(controller, method, arguments).isEmpty());
            arguments[headers.indexOf(header)] = "x".repeat(128);
            assertTrue(validator.forExecutables().validateParameters(controller, method, arguments).isEmpty());
            arguments[headers.indexOf(header)] = "x".repeat(129);
            assertFalse(validator.forExecutables().validateParameters(controller, method, arguments).isEmpty());
        }
    }

    @Test
    void getMappingsDoNotRequireIdempotencyKey() {
        endpointMethods().stream().filter(m -> m.isAnnotationPresent(GetMapping.class)).forEach(method ->
                assertTrue(Arrays.stream(method.getParameters())
                        .noneMatch(p -> p.isAnnotationPresent(RequestHeader.class)), method.getName()));
    }

    @Test
    void createMapsTenantFromContextAndHasNoExpectedVersion() {
        ShipmentCreateReqVO req = validCreate();
        when(commandService.createShipment(eq("key"), any())).thenReturn(77L);
        assertEquals(77L, controller.createShipment("key", req).getData());
        var captor = org.mockito.ArgumentCaptor.forClass(CreateShipmentCommand.class);
        verify(commandService).createShipment(eq("key"), captor.capture());
        assertEquals(TENANT_ID, captor.getValue().getTenantId());
        assertEquals(req.getOrderId(), captor.getValue().getOrderId());
        assertEquals(req.getItems().get(0).getQuantity(), captor.getValue().getItems().get(0).getQuantity());
        assertFalse(Arrays.stream(CreateShipmentCommand.class.getDeclaredFields())
                .anyMatch(f -> f.getName().equals("expectedVersion")));
    }

    @Test
    void packageAndLegOverrideTenantAndShipmentFromServerContext() {
        ShipmentPackageCreateReqVO packageReq = validPackage();
        when(commandService.addPackage(eq("p-key"), any())).thenReturn(88L);
        controller.addPackage("p-key", 500L, packageReq);
        var packageCaptor = org.mockito.ArgumentCaptor.forClass(UpsertPackageCommand.class);
        verify(commandService).addPackage(eq("p-key"), packageCaptor.capture());
        assertEquals(TENANT_ID, packageCaptor.getValue().getTenantId());
        assertEquals(500L, packageCaptor.getValue().getShipmentId());
        assertEquals(packageReq.getExpectedVersion(), packageCaptor.getValue().getExpectedVersion());

        ShipmentLegCreateReqVO legReq = validLeg();
        when(commandService.addLeg(eq("l-key"), any())).thenReturn(89L);
        controller.addLeg("l-key", 501L, legReq);
        var legCaptor = org.mockito.ArgumentCaptor.forClass(AddShipmentLegCommand.class);
        verify(commandService).addLeg(eq("l-key"), legCaptor.capture());
        assertEquals(TENANT_ID, legCaptor.getValue().getTenantId());
        assertEquals(501L, legCaptor.getValue().getShipmentId());
        assertEquals(legReq.getTrackingNumber(), legCaptor.getValue().getTrackingNumber());
    }

    @Test
    void readyAndDispatchRequireNonNegativeVersion() {
        ShipmentVersionReqVO req = new ShipmentVersionReqVO();
        req.setExpectedVersion(0);
        assertValid(req);
        controller.markReady("ready", 42L, req);
        verify(commandService).markReady("ready", TENANT_ID, 42L, 0);
        controller.dispatch("dispatch", 43L, req);
        var captor = org.mockito.ArgumentCaptor.forClass(DispatchShipmentCommand.class);
        verify(commandService).dispatch(eq("dispatch"), captor.capture());
        assertEquals(TENANT_ID, captor.getValue().getTenantId());
        assertEquals(43L, captor.getValue().getShipmentId());
        req.setExpectedVersion(-1);
        assertInvalid(req);
    }

    @Test
    void manualEventMapsOperatorTraceAndPathShipmentFromServerContext() {
        ManualTrackingEventReqVO req = validManual();
        when(trackingService.applyManualEvent(eq("manual"), any()))
                .thenReturn(new TrackingApplyResult(true, true, "READY", "IN_TRANSIT"));
        CommonResult<TrackingApplyRespVO> response = controller.applyManualEvent("manual", 99L, req);
        var captor = org.mockito.ArgumentCaptor.forClass(ApplyManualTrackingEventCommand.class);
        verify(trackingService).applyManualEvent(eq("manual"), captor.capture());
        ApplyManualTrackingEventCommand command = captor.getValue();
        assertEquals(TENANT_ID, command.getTenantId());
        assertEquals(99L, command.getShipmentId());
        assertEquals(OPERATOR_ID, command.getOperatorId());
        assertNotNull(command.getRequestTraceId());
        assertFalse(command.getRequestTraceId().isBlank());
        assertTrue(command.getRequestTraceId().length() <= 64);
        assertEquals(req.getPackageId(), command.getPackageId());
        assertEquals(Boolean.TRUE, response.getData().getInserted());
        assertEquals("IN_TRANSIT", response.getData().getCurrentStatus());
    }

    @Test
    void manualTraceFallsBackToUuidAndSafelyTruncatesActiveTrace() {
        when(trackingService.applyManualEvent(any(), any()))
                .thenReturn(new TrackingApplyResult(true, false, "READY", "READY"));
        try (var tracer = mockStatic(TracerUtils.class)) {
            tracer.when(TracerUtils::getTraceId).thenReturn(" ");
            controller.applyManualEvent("blank-trace", 99L, validManual());
            var blankCaptor = org.mockito.ArgumentCaptor.forClass(ApplyManualTrackingEventCommand.class);
            verify(trackingService).applyManualEvent(eq("blank-trace"), blankCaptor.capture());
            assertEquals(36, blankCaptor.getValue().getRequestTraceId().length());

            tracer.when(TracerUtils::getTraceId).thenReturn("t".repeat(65));
            controller.applyManualEvent("long-trace", 99L, validManual());
            var longCaptor = org.mockito.ArgumentCaptor.forClass(ApplyManualTrackingEventCommand.class);
            verify(trackingService).applyManualEvent(eq("long-trace"), longCaptor.capture());
            assertEquals("t".repeat(64), longCaptor.getValue().getRequestTraceId());
        }
    }

    @Test
    void manualEventFailsClosedWithoutLoginUser() {
        SecurityContextHolder.clearContext();
        assertThrows(IllegalStateException.class,
                () -> controller.applyManualEvent("manual", 99L, validManual()));
        verifyNoInteractions(trackingService);
    }

    @Test
    void mutationResponsesKeepExistingLongBooleanAndTrackingResultShapes() {
        when(commandService.createShipment(any(), any())).thenReturn(1L);
        when(commandService.addPackage(any(), any())).thenReturn(2L);
        when(commandService.addLeg(any(), any())).thenReturn(3L);
        when(trackingService.applyManualEvent(any(), any()))
                .thenReturn(new TrackingApplyResult(true, false, "READY", "READY"));
        assertInstanceOf(Long.class, controller.createShipment("a", validCreate()).getData());
        assertInstanceOf(Long.class, controller.addPackage("b", 1L, validPackage()).getData());
        assertInstanceOf(Long.class, controller.addLeg("c", 1L, validLeg()).getData());
        assertEquals(Boolean.TRUE, controller.markReady("d", 1L, version(0)).getData());
        assertEquals(Boolean.TRUE, controller.dispatch("e", 1L, version(0)).getData());
        assertInstanceOf(TrackingApplyRespVO.class,
                controller.applyManualEvent("f", 1L, validManual()).getData());
    }

    @Test
    void requestAndResponseTypesExposeNoForbiddenProperties() throws Exception {
        assertNoProperties(ShipmentCreateReqVO.class,
                "tenantId", "shipmentId", "shipmentNo", "status", "version", "creator", "updater");
        assertNoProperties(ShipmentPackageCreateReqVO.class,
                "tenantId", "shipmentId", "status", "creator", "updater");
        assertNoProperties(ShipmentLegCreateReqVO.class,
                "tenantId", "shipmentId", "status", "creator", "updater");
        assertNoProperties(ManualTrackingEventReqVO.class,
                "tenantId", "shipmentId", "operatorId", "requestTraceId", "providerId", "carrierId",
                "trackingNumber", "proNumber", "bolNumber", "priority", "mappingVersion",
                "rawPayloadRef", "externalEventId", "outboxStatus", "credential");
        assertEquals(Set.of("inserted", "stateChanged", "previousStatus", "currentStatus"),
                beanProperties(TrackingApplyRespVO.class));

        validateBoundaries();
        assertSensitiveToStringExclusion(validPackage(), "TRACK-SECRET");
        assertSensitiveToStringExclusion(validLeg(), "TRACK-SECRET");
        assertSensitiveToStringExclusion(validManual(), "valid correction reason");
    }

    @Test
    void validationFailuresDoNotEchoOrGloballyLogSensitiveRejectedValues() throws Exception {
        GlobalExceptionHandler global = new GlobalExceptionHandler("test", mock(ApiErrorLogCommonApi.class));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(global).build();
        ch.qos.logback.classic.Logger globalLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        globalLogger.addAppender(appender);
        try {
            assertRejectedWithoutLeak(mockMvc, "/trade/fulfillment/shipments/99/manual-event",
                    "manual-key", "{\"packageId\":31,\"shipmentLegId\":32,"
                            + "\"requestedStatus\":\"IN_TRANSIT\",\"occurredAt\":\"2026-07-16T00:00:00Z\","
                            + "\"expectedVersion\":0,\"reason\":\"" + "REASON_SENTINEL_".repeat(40) + "\"}",
                    "REASON_SENTINEL_");
            assertRejectedWithoutLeak(mockMvc, "/trade/fulfillment/shipments/99/packages",
                    "package-key", "{\"expectedVersion\":0,\"packageNo\":\"PKG-1\","
                            + "\"packageType\":\"PARCEL\",\"trackingNumber\":\""
                            + "TRACKING_SENTINEL_".repeat(5) + "\"}", "TRACKING_SENTINEL_");
            assertRejectedWithoutLeak(mockMvc, "/trade/fulfillment/shipments/99/legs",
                    "leg-key", "{\"expectedVersion\":0,\"sequenceNo\":1,\"legType\":\"LAST_MILE\","
                            + "\"carrierId\":22,\"providerId\":23,\"originLocation\":\""
                            + "LOCATION_SENTINEL_".repeat(20) + "\"}", "LOCATION_SENTINEL_");
            assertTrue(appender.list.isEmpty(), "global validation handler must not be selected");
        } finally {
            globalLogger.detachAppender(appender);
            appender.stop();
        }

        Method handler = TradeFulfillmentController.class.getDeclaredMethod("handleSensitiveValidationFailure");
        assertEquals(0, handler.getParameterCount(), "handler must not receive rejected values or exceptions");
        assertArrayEquals(new Class<?>[]{MethodArgumentNotValidException.class,
                        org.springframework.validation.BindException.class,
                        jakarta.validation.ConstraintViolationException.class},
                handler.getAnnotation(ExceptionHandler.class).value());
        String keySentinel = "IDEMPOTENCY_KEY_SENTINEL_".repeat(6);
        assertTrue(keySentinel.length() > 128);
        assertFalse(controller.handleSensitiveValidationFailure().toString().contains(keySentinel));
    }

    private void validateBoundaries() {
        ManualTrackingEventReqVO manual = validManual();
        manual.setReason("1234"); assertInvalid(manual);
        manual.setReason("12345"); assertValid(manual);
        manual.setReason("x".repeat(500)); assertValid(manual);
        manual.setReason("x".repeat(501)); assertInvalid(manual);

        ShipmentCreateReqVO create = validCreate();
        create.setOriginCountry("US"); assertValid(create);
        create.setOriginCountry("CA"); assertValid(create);
        create.setOriginCountry("CN"); assertInvalid(create);
        create.setOriginCountry("USA"); assertInvalid(create);
        create.setOriginCountry("US");
        create.getItems().get(0).setQuantity(BigDecimal.ZERO); assertInvalid(create);
        create.getItems().get(0).setQuantity(new BigDecimal("0.000001")); assertValid(create);
        create.getItems().get(0).setQuantity(new BigDecimal("1.0000001")); assertInvalid(create);
        create.getItems().get(0).setSkuId(null); assertInvalid(create);

        ShipmentPackageCreateReqVO pkg = validPackage();
        pkg.setPackageNo("x".repeat(32)); assertValid(pkg);
        pkg.setPackageNo("x".repeat(33)); assertInvalid(pkg);
        pkg.setPackageNo("PKG-1");
        pkg.setTrackingNumber("x".repeat(64)); assertValid(pkg);
        pkg.setTrackingNumber("x".repeat(65)); assertInvalid(pkg);
        pkg.setTrackingNumber("TRACK-SECRET");
        pkg.setWeight(BigDecimal.ZERO); assertValid(pkg);
        pkg.setWeight(new BigDecimal("-0.1")); assertInvalid(pkg);
        pkg.setWeight(new BigDecimal("1.0000001")); assertInvalid(pkg);

        ShipmentLegCreateReqVO leg = validLeg();
        leg.setServiceLevel("x".repeat(64)); assertValid(leg);
        leg.setServiceLevel("x".repeat(65)); assertInvalid(leg);
        leg.setServiceLevel("GROUND");
        leg.setOriginLocation("x".repeat(256)); assertValid(leg);
        leg.setOriginLocation("x".repeat(257)); assertInvalid(leg);
    }

    private static List<Method> endpointMethods() {
        return Arrays.stream(TradeFulfillmentController.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(PostMapping.class)
                        || m.isAnnotationPresent(PutMapping.class)
                        || m.isAnnotationPresent(GetMapping.class))
                .toList();
    }

    private static void assertRejectedWithoutLeak(MockMvc mockMvc, String path, String key,
                                                  String body, String sentinel) throws Exception {
        String response = mockMvc.perform(post(path).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400))
                .andReturn().getResponse().getContentAsString();
        assertFalse(response.contains(sentinel));
    }

    private static boolean isWrite(Method method) {
        return method.isAnnotationPresent(PostMapping.class) || method.isAnnotationPresent(PutMapping.class);
    }

    private static String routeKey(Method method) {
        if (method.isAnnotationPresent(PostMapping.class)) {
            String[] values = method.getAnnotation(PostMapping.class).value();
            return "POST " + (values.length == 0 ? "" : values[0]);
        }
        if (method.isAnnotationPresent(PutMapping.class)) {
            String[] values = method.getAnnotation(PutMapping.class).value();
            return "PUT " + (values.length == 0 ? "" : values[0]);
        }
        String[] values = method.getAnnotation(GetMapping.class).value();
        return "GET " + (values.length == 0 ? "" : values[0]);
    }

    private static Set<String> beanProperties(Class<?> type) throws Exception {
        return Arrays.stream(Introspector.getBeanInfo(type, Object.class).getPropertyDescriptors())
                .map(p -> p.getName()).collect(Collectors.toSet());
    }

    private static void assertNoProperties(Class<?> type, String... names) throws Exception {
        Set<String> properties = beanProperties(type);
        for (String name : names) assertFalse(properties.contains(name), type.getSimpleName() + "." + name);
    }

    private void assertValid(Object value) {
        assertTrue(validator.validate(value).isEmpty(), violations(value));
    }

    private void assertInvalid(Object value) {
        assertFalse(validator.validate(value).isEmpty());
    }

    private String violations(Object value) {
        return validator.validate(value).stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(","));
    }

    private static void assertSensitiveToStringExclusion(Object value, String secret) {
        assertFalse(value.toString().contains(secret), value.getClass().getSimpleName());
    }

    private static ShipmentVersionReqVO version(int version) {
        ShipmentVersionReqVO req = new ShipmentVersionReqVO();
        req.setExpectedVersion(version);
        return req;
    }

    private static ShipmentCreateReqVO validCreate() {
        ShipmentCreateItemReqVO item = new ShipmentCreateItemReqVO();
        item.setOrderItemId(11L); item.setSkuId(12L); item.setQuantity(BigDecimal.ONE);
        ShipmentCreateReqVO req = new ShipmentCreateReqVO();
        req.setOrderId(10L); req.setShipmentType(ShipmentTypeEnum.PARCEL);
        req.setOriginCountry("US"); req.setDestinationCountry("CA");
        req.setOriginTimezone("America/New_York"); req.setDestinationTimezone("America/Toronto");
        req.setWarehouseId(13L); req.setProviderId(14L); req.setItems(List.of(item));
        return req;
    }

    private static ShipmentPackageCreateReqVO validPackage() {
        ShipmentPackageCreateReqVO req = new ShipmentPackageCreateReqVO();
        req.setExpectedVersion(0); req.setPackageNo("PKG-1"); req.setPackageType("PARCEL");
        req.setCarrierId(20L); req.setTrackingNumber("TRACK-SECRET");
        req.setWeight(BigDecimal.ONE); req.setWeightUnit("KG");
        req.setLength(BigDecimal.ONE); req.setWidth(BigDecimal.ONE); req.setHeight(BigDecimal.ONE);
        req.setDimensionUnit("CM"); return req;
    }

    private static ShipmentLegCreateReqVO validLeg() {
        ShipmentLegCreateReqVO req = new ShipmentLegCreateReqVO();
        req.setExpectedVersion(0); req.setPackageId(21L); req.setSequenceNo(1);
        req.setLegType("LAST_MILE"); req.setCarrierId(22L); req.setProviderId(23L);
        req.setServiceLevel("GROUND"); req.setTrackingNumber("TRACK-SECRET");
        req.setProNumber("PRO-SECRET"); req.setBolNumber("BOL-SECRET");
        req.setOriginLocation("ORIGIN-SECRET"); req.setDestinationLocation("DEST-SECRET"); return req;
    }

    private static ManualTrackingEventReqVO validManual() {
        ManualTrackingEventReqVO req = new ManualTrackingEventReqVO();
        req.setPackageId(31L); req.setShipmentLegId(32L);
        req.setRequestedStatus(ShipmentStatusEnum.IN_TRANSIT);
        req.setOccurredAt(Instant.parse("2026-07-16T00:00:00Z")); req.setExpectedVersion(0);
        req.setReason("valid correction reason"); return req;
    }
}
