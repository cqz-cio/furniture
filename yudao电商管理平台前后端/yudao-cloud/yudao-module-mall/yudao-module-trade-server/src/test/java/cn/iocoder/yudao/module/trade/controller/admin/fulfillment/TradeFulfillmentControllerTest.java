package cn.iocoder.yudao.module.trade.controller.admin.fulfillment;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
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
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        Map<String, String> permissionByRoute = methods.stream().collect(Collectors.toMap(
                TradeFulfillmentControllerTest::routeKey,
                method -> method.getAnnotation(PreAuthorize.class).value()));
        assertEquals(Map.of(
                "POST ", "@ss.hasPermission('trade:fulfillment:shipment:create')",
                "PUT /{id}/ready", "@ss.hasPermission('trade:fulfillment:shipment:update')",
                "POST /{id}/packages", "@ss.hasPermission('trade:fulfillment:shipment:update')",
                "POST /{id}/legs", "@ss.hasPermission('trade:fulfillment:shipment:update')",
                "POST /{id}/dispatch", "@ss.hasPermission('trade:fulfillment:shipment:dispatch')",
                "POST /{id}/manual-event", "@ss.hasPermission('trade:fulfillment:tracking:manual')",
                "GET /{id}", "@ss.hasPermission('trade:fulfillment:shipment:query')",
                "GET /{id}/timeline", "@ss.hasPermission('trade:fulfillment:shipment:query')",
                "GET /page", "@ss.hasPermission('trade:fulfillment:shipment:query')"), permissionByRoute);
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
        CreateShipmentCommand command = captor.getValue();
        assertEquals(TENANT_ID, command.getTenantId());
        assertEquals(req.getOrderId(), command.getOrderId());
        assertEquals(req.getShipmentType(), command.getShipmentType());
        assertEquals(req.getOriginCountry(), command.getOriginCountry());
        assertEquals(req.getDestinationCountry(), command.getDestinationCountry());
        assertEquals(req.getOriginTimezone(), command.getOriginTimezone());
        assertEquals(req.getDestinationTimezone(), command.getDestinationTimezone());
        assertEquals(req.getWarehouseId(), command.getWarehouseId());
        assertEquals(req.getProviderId(), command.getProviderId());
        assertEquals(1, command.getItems().size());
        assertEquals(req.getItems().get(0).getOrderItemId(), command.getItems().get(0).getOrderItemId());
        assertEquals(req.getItems().get(0).getSkuId(), command.getItems().get(0).getSkuId());
        assertEquals(req.getItems().get(0).getQuantity(), command.getItems().get(0).getQuantity());
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
        UpsertPackageCommand packageCommand = packageCaptor.getValue();
        assertEquals(TENANT_ID, packageCommand.getTenantId());
        assertEquals(500L, packageCommand.getShipmentId());
        assertEquals(packageReq.getExpectedVersion(), packageCommand.getExpectedVersion());
        assertEquals(packageReq.getPackageNo(), packageCommand.getPackageNo());
        assertEquals(packageReq.getPackageType(), packageCommand.getPackageType());
        assertEquals(packageReq.getCarrierId(), packageCommand.getCarrierId());
        assertEquals(packageReq.getTrackingNumber(), packageCommand.getTrackingNumber());
        assertEquals(packageReq.getWeight(), packageCommand.getWeight());
        assertEquals(packageReq.getWeightUnit(), packageCommand.getWeightUnit());
        assertEquals(packageReq.getLength(), packageCommand.getLength());
        assertEquals(packageReq.getWidth(), packageCommand.getWidth());
        assertEquals(packageReq.getHeight(), packageCommand.getHeight());
        assertEquals(packageReq.getDimensionUnit(), packageCommand.getDimensionUnit());

        ShipmentLegCreateReqVO legReq = validLeg();
        when(commandService.addLeg(eq("l-key"), any())).thenReturn(89L);
        controller.addLeg("l-key", 501L, legReq);
        var legCaptor = org.mockito.ArgumentCaptor.forClass(AddShipmentLegCommand.class);
        verify(commandService).addLeg(eq("l-key"), legCaptor.capture());
        AddShipmentLegCommand legCommand = legCaptor.getValue();
        assertEquals(TENANT_ID, legCommand.getTenantId());
        assertEquals(501L, legCommand.getShipmentId());
        assertEquals(legReq.getExpectedVersion(), legCommand.getExpectedVersion());
        assertEquals(legReq.getPackageId(), legCommand.getPackageId());
        assertEquals(legReq.getSequenceNo(), legCommand.getSequenceNo());
        assertEquals(legReq.getLegType(), legCommand.getLegType());
        assertEquals(legReq.getCarrierId(), legCommand.getCarrierId());
        assertEquals(legReq.getProviderId(), legCommand.getProviderId());
        assertEquals(legReq.getServiceLevel(), legCommand.getServiceLevel());
        assertEquals(legReq.getTrackingNumber(), legCommand.getTrackingNumber());
        assertEquals(legReq.getProNumber(), legCommand.getProNumber());
        assertEquals(legReq.getBolNumber(), legCommand.getBolNumber());
        assertEquals(legReq.getOriginLocation(), legCommand.getOriginLocation());
        assertEquals(legReq.getDestinationLocation(), legCommand.getDestinationLocation());
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
        assertEquals(req.getShipmentLegId(), command.getShipmentLegId());
        assertEquals(req.getRequestedStatus(), command.getRequestedStatus());
        assertEquals(req.getOccurredAt(), command.getOccurredAt());
        assertEquals(req.getExpectedVersion(), command.getExpectedShipmentVersion());
        assertEquals(req.getReason(), command.getReason());
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
        assertProperties(ShipmentCreateReqVO.class, "orderId", "shipmentType", "originCountry",
                "destinationCountry", "originTimezone", "destinationTimezone", "warehouseId", "providerId", "items");
        assertProperties(ShipmentCreateItemReqVO.class, "orderItemId", "skuId", "quantity");
        assertProperties(ShipmentPackageCreateReqVO.class, "expectedVersion", "packageNo", "packageType",
                "carrierId", "trackingNumber", "weight", "weightUnit", "length", "width", "height", "dimensionUnit");
        assertProperties(ShipmentLegCreateReqVO.class, "expectedVersion", "packageId", "sequenceNo", "legType",
                "carrierId", "providerId", "serviceLevel", "trackingNumber", "proNumber", "bolNumber",
                "originLocation", "destinationLocation");
        assertProperties(ShipmentVersionReqVO.class, "expectedVersion");
        assertProperties(ManualTrackingEventReqVO.class, "packageId", "shipmentLegId", "requestedStatus",
                "occurredAt", "expectedVersion", "reason");
        assertProperties(TrackingApplyRespVO.class, "inserted", "stateChanged", "previousStatus", "currentStatus");

        validateBoundaries();
        assertSensitiveToStringExclusion(validPackage(), "TRACK-SECRET");
        assertSensitiveToStringExclusion(validLeg(), "TRACK-SECRET");
        assertSensitiveToStringExclusion(validManual(), "valid correction reason");
    }

    @Test
    void validationFailuresDoNotEchoOrGloballyLogSensitiveRejectedValues() throws Exception {
        GlobalExceptionHandler global = new GlobalExceptionHandler("test", mock(ApiErrorLogCommonApi.class));
        MockMvc mockMvc = proxiedMockMvc(global);
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

        assertTrue(Arrays.stream(TradeFulfillmentController.class.getDeclaredMethods())
                .noneMatch(method -> method.isAnnotationPresent(ExceptionHandler.class)));
    }

    @Test
    void httpMethodValidationRejectsBadKeysBeforeServiceAndPreservesValidKeyExactly() throws Exception {
        GlobalExceptionHandler global = new GlobalExceptionHandler("test", mock(ApiErrorLogCommonApi.class));
        MockMvc mockMvc = proxiedMockMvc(global);
        ch.qos.logback.classic.Logger globalLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        globalLogger.addAppender(appender);
        String longKey = "IDEMPOTENCY_KEY_SENTINEL_".repeat(6);
        try {
            mockMvc.perform(post("/trade/fulfillment/shipments")
                            .contentType(MediaType.APPLICATION_JSON).content(validCreateJson()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));
            mockMvc.perform(post("/trade/fulfillment/shipments").header("Idempotency-Key", " ")
                            .contentType(MediaType.APPLICATION_JSON).content(validCreateJson()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));
            String longResponse = mockMvc.perform(post("/trade/fulfillment/shipments")
                            .header("Idempotency-Key", longKey)
                            .contentType(MediaType.APPLICATION_JSON).content(validCreateJson()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400))
                    .andReturn().getResponse().getContentAsString();
            assertFalse(longResponse.contains(longKey));
            verifyNoInteractions(commandService);

            when(commandService.createShipment(eq("  exact-key  "), any())).thenReturn(701L);
            mockMvc.perform(post("/trade/fulfillment/shipments")
                            .header("Idempotency-Key", "  exact-key  ")
                            .contentType(MediaType.APPLICATION_JSON).content(validCreateJson()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").value(701));
            verify(commandService).createShipment(eq("  exact-key  "), any());
            assertTrue(appender.list.isEmpty(), "global handler must not log request validation");
        } finally {
            globalLogger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void runtimeGetRoutesResolvePageDetailAndTimelineExactly() throws Exception {
        GlobalExceptionHandler global = new GlobalExceptionHandler("test", mock(ApiErrorLogCommonApi.class));
        MockMvc mockMvc = proxiedMockMvc(global);
        when(queryService.getShipmentPage(eq(TENANT_ID), any())).thenReturn(PageResult.empty());
        when(queryService.getShipment(TENANT_ID, 123L)).thenReturn(new ShipmentDetailRespVO());
        when(queryService.getTimeline(TENANT_ID, 123L)).thenReturn(List.of());

        mockMvc.perform(get("/trade/fulfillment/shipments/page"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        verify(queryService).getShipmentPage(eq(TENANT_ID), any());
        verify(queryService, never()).getShipment(eq(TENANT_ID), isNull());

        mockMvc.perform(get("/trade/fulfillment/shipments/123"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        verify(queryService).getShipment(TENANT_ID, 123L);

        mockMvc.perform(get("/trade/fulfillment/shipments/123/timeline"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        verify(queryService).getTimeline(TENANT_ID, 123L);
    }

    @Test
    void disabledFeatureReturnsStableServiceErrorWithoutConfigurationDetails() throws Exception {
        when(commandService.createShipment(any(), any())).thenThrow(
                cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(
                        cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_FEATURE_DISABLED));
        GlobalExceptionHandler global = new GlobalExceptionHandler("test", mock(ApiErrorLogCommonApi.class));
        MockMvc mockMvc = proxiedMockMvc(global);

        String response = mockMvc.perform(post("/trade/fulfillment/shipments")
                        .header("Idempotency-Key", "disabled-key")
                        .contentType(MediaType.APPLICATION_JSON).content(validCreateJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1_011_009_012L))
                .andReturn().getResponse().getContentAsString();

        assertTrue(response.contains("Fulfillment feature is disabled"));
        assertFalse(response.contains("write-new-model"));
        assertFalse(response.contains("read-from-new-model"));
        assertFalse(response.contains("legacy-migration-write-enabled"));
    }

    @Test
    void getPageValidationIsValueFreeAndDoesNotCallQueryService() throws Exception {
        GlobalExceptionHandler global = new GlobalExceptionHandler("test", mock(ApiErrorLogCommonApi.class));
        MockMvc mockMvc = proxiedMockMvc(global);
        String response = mockMvc.perform(get("/trade/fulfillment/shipments/page")
                        .param("originCountry", "COUNTRY_SENTINEL"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400))
                .andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("COUNTRY_SENTINEL"));
        verifyNoInteractions(queryService);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void serviceConstraintViolationReturnsSafe500AndOnlyLogsMetadata() throws Exception {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        when(violation.getRootBeanClass()).thenReturn((Class) FulfillmentQueryService.class);
        Path path = mock(Path.class);
        Path.Node methodNode = mock(Path.Node.class);
        when(methodNode.getKind()).thenReturn(ElementKind.METHOD);
        when(methodNode.getName()).thenReturn("getShipment");
        Path.Node parameterNode = mock(Path.Node.class);
        when(parameterNode.getKind()).thenReturn(ElementKind.PARAMETER);
        when(parameterNode.getName()).thenReturn("tenantId");
        when(path.iterator()).thenAnswer(ignored -> List.of(methodNode, parameterNode).iterator());
        when(violation.getPropertyPath()).thenReturn(path);
        ConstraintViolationException serviceFailure = new ConstraintViolationException(
                "SERVICE_VALUE_SENTINEL", Set.<ConstraintViolation<?>>of(violation));
        when(queryService.getShipment(TENANT_ID, 123L)).thenThrow(serviceFailure);

        GlobalExceptionHandler global = new GlobalExceptionHandler("test", mock(ApiErrorLogCommonApi.class));
        MockMvc mockMvc = proxiedMockMvc(global);
        ch.qos.logback.classic.Logger safeLogger = (ch.qos.logback.classic.Logger)
                LoggerFactory.getLogger(TradeFulfillmentValidationExceptionHandler.class);
        ch.qos.logback.classic.Logger globalLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> safeAppender = new ListAppender<>();
        ListAppender<ILoggingEvent> globalAppender = new ListAppender<>();
        safeAppender.start(); globalAppender.start();
        safeLogger.addAppender(safeAppender); globalLogger.addAppender(globalAppender);
        try {
            String response = mockMvc.perform(get("/trade/fulfillment/shipments/123"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                    .andReturn().getResponse().getContentAsString();
            assertFalse(response.contains("SERVICE_VALUE_SENTINEL"));
            assertEquals(1, safeAppender.list.size());
            String log = safeAppender.list.get(0).getFormattedMessage();
            assertTrue(log.contains("FulfillmentQueryService"));
            assertTrue(log.contains("getShipment"));
            assertTrue(log.contains("tenantId"));
            assertFalse(log.contains("SERVICE_VALUE_SENTINEL"));
            assertNull(safeAppender.list.get(0).getThrowableProxy());
            assertTrue(globalAppender.list.isEmpty());
        } finally {
            safeLogger.detachAppender(safeAppender); globalLogger.detachAppender(globalAppender);
            safeAppender.stop(); globalAppender.stop();
        }
    }

    @Test
    void illegalJsonEnumReturnsValueFree400BeforeService() throws Exception {
        GlobalExceptionHandler global = new GlobalExceptionHandler("test", mock(ApiErrorLogCommonApi.class));
        MockMvc mockMvc = proxiedMockMvc(global);
        String body = validCreateJson().replace("\"PARCEL\"", "\"ENUM_SENTINEL\"");
        String response = mockMvc.perform(post("/trade/fulfillment/shipments")
                        .header("Idempotency-Key", "enum-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400))
                .andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("ENUM_SENTINEL"));
        verifyNoInteractions(commandService);
    }

    private void validateBoundaries() {
        ManualTrackingEventReqVO manual = validManual();
        manual.setReason("1234"); assertInvalid(manual);
        manual.setReason("12345"); assertValid(manual);
        manual.setReason("x".repeat(500)); assertValid(manual);
        manual.setReason("x".repeat(501)); assertInvalid(manual);
        manual = validManual(); manual.setExpectedVersion(null); assertInvalid(manual);

        ShipmentVersionReqVO version = version(0);
        version.setExpectedVersion(null); assertInvalid(version);

        ShipmentCreateReqVO create = validCreate();
        create.setOriginCountry("US"); assertValid(create);
        create.setOriginCountry("CA"); assertValid(create);
        create.setOriginCountry("CN"); assertInvalid(create);
        create.setOriginCountry("USA"); assertInvalid(create);
        create.setOriginCountry("US");
        create.setOriginTimezone("x".repeat(64)); assertValid(create);
        create.setOriginTimezone("x".repeat(65)); assertInvalid(create);
        create.setOriginTimezone("America/New_York");
        create.setDestinationTimezone("x".repeat(64)); assertValid(create);
        create.setDestinationTimezone("x".repeat(65)); assertInvalid(create);
        create.setDestinationTimezone("America/Toronto");
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
        pkg.setExpectedVersion(null); assertInvalid(pkg);
        pkg.setExpectedVersion(0);
        pkg.setPackageType("BOX"); assertInvalid(pkg);
        pkg.setPackageType("PARCEL");
        pkg.setWeightUnit("OZ"); assertInvalid(pkg);
        pkg.setWeightUnit("KG");
        pkg.setDimensionUnit("FT"); assertInvalid(pkg);
        pkg.setDimensionUnit("CM");
        pkg.setWeight(BigDecimal.ZERO); assertValid(pkg);
        pkg.setWeight(new BigDecimal("-0.1")); assertInvalid(pkg);
        pkg.setWeight(new BigDecimal("1.0000001")); assertInvalid(pkg);
        pkg = validPackage(); pkg.setLength(BigDecimal.ZERO); assertValid(pkg);
        pkg.setLength(new BigDecimal("-0.1")); assertInvalid(pkg);
        pkg = validPackage(); pkg.setWidth(BigDecimal.ZERO); assertValid(pkg);
        pkg.setWidth(new BigDecimal("-0.1")); assertInvalid(pkg);
        pkg = validPackage(); pkg.setHeight(BigDecimal.ZERO); assertValid(pkg);
        pkg.setHeight(new BigDecimal("1.0000001")); assertInvalid(pkg);

        ShipmentLegCreateReqVO leg = validLeg();
        leg.setExpectedVersion(null); assertInvalid(leg);
        leg.setExpectedVersion(0);
        leg.setLegType("MIDDLE_MILE"); assertInvalid(leg);
        leg.setLegType("LAST_MILE");
        leg.setServiceLevel("x".repeat(64)); assertValid(leg);
        leg.setServiceLevel("x".repeat(65)); assertInvalid(leg);
        leg.setServiceLevel("GROUND");
        leg.setProNumber("x".repeat(64)); assertValid(leg);
        leg.setProNumber("x".repeat(65)); assertInvalid(leg);
        leg.setProNumber("PRO-SECRET");
        leg.setBolNumber("x".repeat(64)); assertValid(leg);
        leg.setBolNumber("x".repeat(65)); assertInvalid(leg);
        leg.setBolNumber("BOL-SECRET");
        leg.setOriginLocation("x".repeat(256)); assertValid(leg);
        leg.setOriginLocation("x".repeat(257)); assertInvalid(leg);
        leg.setOriginLocation("ORIGIN-SECRET");
        leg.setDestinationLocation("x".repeat(256)); assertValid(leg);
        leg.setDestinationLocation("x".repeat(257)); assertInvalid(leg);
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

    private MockMvc proxiedMockMvc(GlobalExceptionHandler global) {
        ProxyFactory proxyFactory = new ProxyFactory(controller);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new MethodValidationInterceptor(validator));
        Object proxiedController = proxyFactory.getProxy();
        return MockMvcBuilders.standaloneSetup(proxiedController)
                .setControllerAdvice(new TradeFulfillmentValidationExceptionHandler(), global)
                .build();
    }

    private static String validCreateJson() {
        return "{\"orderId\":10,\"shipmentType\":\"PARCEL\",\"originCountry\":\"US\","
                + "\"destinationCountry\":\"CA\",\"originTimezone\":\"America/New_York\","
                + "\"destinationTimezone\":\"America/Toronto\",\"warehouseId\":13,\"providerId\":14,"
                + "\"items\":[{\"orderItemId\":11,\"skuId\":12,\"quantity\":1}]}";
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

    private static void assertProperties(Class<?> type, String... names) throws Exception {
        assertEquals(Set.of(names), beanProperties(type), type.getSimpleName());
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
