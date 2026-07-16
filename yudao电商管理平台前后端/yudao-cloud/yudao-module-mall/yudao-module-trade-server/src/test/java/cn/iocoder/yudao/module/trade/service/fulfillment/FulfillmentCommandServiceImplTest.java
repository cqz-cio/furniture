package cn.iocoder.yudao.module.trade.service.fulfillment;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.FulfillmentIdempotencyDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.FulfillmentOutboxEventDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.OrderFulfillmentSummaryDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentItemDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.FulfillmentIdempotencyMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.FulfillmentOutboxEventMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.CarrierMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.LogisticsProviderMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.OrderFulfillmentSummaryMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentItemMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentLegMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentPackageMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.enums.fulfillment.OrderFulfillmentStatusEnum;
import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum;
import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentTypeEnum;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentProperties;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentFeatureGuard;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.LogisticsProviderRegistry;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.CreateShipmentCommand;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.CreateShipmentItemCommand;
import cn.iocoder.yudao.module.trade.service.fulfillment.support.FulfillmentHashing;
import cn.iocoder.yudao.module.trade.service.fulfillment.support.FulfillmentNoGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_COUNTRY_NOT_SUPPORTED;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_CROSS_BORDER_NOT_SUPPORTED;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_IDEMPOTENCY_CONFLICT;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_ORDER_ITEM_QUANTITY_EXCEEDED;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_ORDER_NOT_FOUND;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.ORDER_ITEM_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FulfillmentCommandServiceImplTest extends BaseMockitoUnitTest {

    private static final Long TENANT_ID = 121L;
    private static final Long ORDER_ID = 100L;
    private static final Long ORDER_ITEM_ID = 501L;
    private static final Long SKU_ID = 901L;
    private static final Long SHIPMENT_ID = 70001L;
    private static final String IDEMPOTENCY_KEY = "wms-outbound-private-key";

    @InjectMocks
    private FulfillmentCommandServiceImpl service;

    @Mock
    private TradeOrderMapper tradeOrderMapper;
    @Mock
    private TradeOrderItemMapper tradeOrderItemMapper;
    @Mock
    private ShipmentMapper shipmentMapper;
    @Mock
    private ShipmentItemMapper shipmentItemMapper;
    @Mock
    private ShipmentPackageMapper packageMapper;
    @Mock
    private ShipmentLegMapper legMapper;
    @Mock
    private CarrierMapper carrierMapper;
    @Mock
    private LogisticsProviderMapper providerMapper;
    @Mock
    private OrderFulfillmentSummaryMapper summaryMapper;
    @Mock
    private FulfillmentIdempotencyMapper idempotencyMapper;
    @Mock
    private FulfillmentOutboxEventMapper outboxMapper;
    @Mock
    private FulfillmentProperties properties;
    @Mock
    private FulfillmentFeatureGuard featureGuard;
    @Mock
    private FulfillmentNoGenerator noGenerator;
    @Mock
    private LogisticsProviderRegistry providerRegistry;
    @Mock
    private FulfillmentTrackingRegistrationFailureService registrationFailureService;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getIdempotencyHmacKey()).thenReturn("unit-test-hmac-secret");
        lenient().when(noGenerator.generate()).thenReturn("SHP-20260715-0123456789ABCDEF");
        lenient().doAnswer(invocation -> {
            FulfillmentIdempotencyDO row = invocation.getArgument(0);
            row.setId(801L);
            return 1;
        }).when(idempotencyMapper).insert(any(FulfillmentIdempotencyDO.class));
        lenient().doAnswer(invocation -> {
            ShipmentDO row = invocation.getArgument(0);
            row.setId(SHIPMENT_ID);
            return 1;
        }).when(shipmentMapper).insert(any(ShipmentDO.class));
        lenient().when(idempotencyMapper.completeProcessingById(eq(TENANT_ID), eq(801L), anyString(), eq(SHIPMENT_ID),
                any(LocalDateTime.class))).thenReturn(1);
    }

    @Test
    void everyPublicMutationChecksWriteFlagBeforeValidationOrPersistence() {
        IllegalStateException disabled = new IllegalStateException("writes disabled");
        doThrow(disabled).when(featureGuard).requireWriteEnabled();

        assertEquals(disabled, assertThrows(IllegalStateException.class,
                () -> service.createShipment(IDEMPOTENCY_KEY, null)));
        assertEquals(disabled, assertThrows(IllegalStateException.class,
                () -> service.addPackage(IDEMPOTENCY_KEY, null)));
        assertEquals(disabled, assertThrows(IllegalStateException.class,
                () -> service.addLeg(IDEMPOTENCY_KEY, null)));
        assertEquals(disabled, assertThrows(IllegalStateException.class,
                () -> service.markReady(IDEMPOTENCY_KEY, null, null, null)));
        assertEquals(disabled, assertThrows(IllegalStateException.class,
                () -> service.dispatch(IDEMPOTENCY_KEY, null)));

        verify(featureGuard, org.mockito.Mockito.times(5)).requireWriteEnabled();
        verifyNoInteractions(tradeOrderMapper, tradeOrderItemMapper, shipmentMapper, shipmentItemMapper,
                packageMapper, legMapper, carrierMapper, providerMapper, summaryMapper, idempotencyMapper,
                outboxMapper, providerRegistry, registrationFailureService);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void createsUnitedStatesDraftAtomicallyAndStoresOnlyHashedIdempotencyKey() {
        stubOrderAndItem();
        LocalDateTime beforeUtc = LocalDateTime.now(Clock.systemUTC());

        Long result = service.createShipment(IDEMPOTENCY_KEY, command("US", "US", BigDecimal.ONE));
        LocalDateTime afterUtc = LocalDateTime.now(Clock.systemUTC());

        assertEquals(SHIPMENT_ID, result);
        ArgumentCaptor<FulfillmentIdempotencyDO> idempotencyCaptor =
                ArgumentCaptor.forClass(FulfillmentIdempotencyDO.class);
        verify(idempotencyMapper).insert(idempotencyCaptor.capture());
        FulfillmentIdempotencyDO idempotency = idempotencyCaptor.getValue();
        assertEquals("PROCESSING", idempotency.getStatus());
        assertNotEquals(IDEMPOTENCY_KEY, idempotency.getIdempotencyKeyHash());
        assertTrue(idempotency.getIdempotencyKeyHash().matches("[0-9a-f]{64}"));
        assertTrue(idempotency.getRequestHash().matches("[0-9a-f]{64}"));
        assertFalse(idempotency.toString().contains(IDEMPOTENCY_KEY));
        assertFalse(idempotency.getExpiresAt().isBefore(beforeUtc.plusHours(24)));
        assertFalse(idempotency.getExpiresAt().isAfter(afterUtc.plusHours(24)));

        verify(tradeOrderMapper).selectByIdForUpdate(ORDER_ID);
        ArgumentCaptor<ShipmentDO> shipmentCaptor = ArgumentCaptor.forClass(ShipmentDO.class);
        verify(shipmentMapper).insert(shipmentCaptor.capture());
        ShipmentDO shipment = shipmentCaptor.getValue();
        assertEquals(ShipmentStatusEnum.DRAFT.name(), shipment.getStatus());
        assertEquals("US", shipment.getOriginCountry());
        assertEquals("US", shipment.getDestinationCountry());
        assertEquals(0, shipment.getVersion());

        ArgumentCaptor<OrderFulfillmentSummaryDO> summaryCaptor =
                ArgumentCaptor.forClass(OrderFulfillmentSummaryDO.class);
        verify(summaryMapper).insert(summaryCaptor.capture());
        assertEquals(OrderFulfillmentStatusEnum.NOT_SHIPPED.name(), summaryCaptor.getValue().getStatus());
        assertEquals(1, summaryCaptor.getValue().getShipmentCount());
        assertEquals(0, summaryCaptor.getValue().getDeliveredShipmentCount());

        ArgumentCaptor<FulfillmentOutboxEventDO> outboxCaptor =
                ArgumentCaptor.forClass(FulfillmentOutboxEventDO.class);
        verify(outboxMapper).insert(outboxCaptor.capture());
        assertEquals("SHIPMENT_CREATED", outboxCaptor.getValue().getEventType());
        assertEquals(SHIPMENT_ID, outboxCaptor.getValue().getAggregateId());
        assertFalse(outboxCaptor.getValue().toString().contains(IDEMPOTENCY_KEY));
        String payload = outboxCaptor.getValue().getPayload().toString();
        assertFalse(payload.contains(IDEMPOTENCY_KEY));
        assertFalse(payload.contains("unit-test-hmac-secret"));
        assertFalse(payload.contains("receiver-address-secret"));
        assertFalse(outboxCaptor.getValue().getNextAttemptAt().isBefore(beforeUtc));
        assertFalse(outboxCaptor.getValue().getNextAttemptAt().isAfter(afterUtc));
        verify(idempotencyMapper).completeProcessingById(eq(TENANT_ID), eq(801L),
                eq(idempotency.getRequestHash()), eq(SHIPMENT_ID), any(LocalDateTime.class));
    }

    @Test
    void createsCanadianDraft() {
        stubOrderAndItem();

        Long result = service.createShipment(IDEMPOTENCY_KEY, command("CA", "CA", BigDecimal.ONE));

        assertEquals(SHIPMENT_ID, result);
        verify(shipmentMapper).insert(org.mockito.ArgumentMatchers.argThat((ShipmentDO shipment) ->
                "CA".equals(shipment.getOriginCountry()) && "CA".equals(shipment.getDestinationCountry())));
    }

    @Test
    void rejectsOffsetShortAndUnknownTimezonesBeforeShipmentInsert() {
        stubOrderAndItem();

        for (String invalidTimezone : List.of("+02:00", "EST", "not/a-zone")) {
            CreateShipmentCommand invalidOrigin = command("US", "US", BigDecimal.ONE)
                    .setOriginTimezone(invalidTimezone);
            assertThrows(IllegalArgumentException.class,
                    () -> service.createShipment(IDEMPOTENCY_KEY, invalidOrigin));

            CreateShipmentCommand invalidDestination = command("US", "US", BigDecimal.ONE)
                    .setDestinationTimezone(invalidTimezone);
            assertThrows(IllegalArgumentException.class,
                    () -> service.createShipment(IDEMPOTENCY_KEY, invalidDestination));
        }

        verify(shipmentMapper, never()).insert(any(ShipmentDO.class));
    }

    @Test
    void acceptsUtcAndStoresTrimmedIanaTimezones() {
        stubOrderAndItem();
        CreateShipmentCommand command = command("US", "US", BigDecimal.ONE)
                .setOriginTimezone(" UTC ")
                .setDestinationTimezone(" America/Toronto ");

        service.createShipment(IDEMPOTENCY_KEY, command);

        ArgumentCaptor<ShipmentDO> shipmentCaptor = ArgumentCaptor.forClass(ShipmentDO.class);
        verify(shipmentMapper).insert(shipmentCaptor.capture());
        assertEquals("UTC", shipmentCaptor.getValue().getOriginTimezone());
        assertEquals("America/Toronto", shipmentCaptor.getValue().getDestinationTimezone());
    }

    @Test
    void rejectsCrossBorderShipment() {
        stubOrderAndItem();

        assertServiceException(() -> service.createShipment(IDEMPOTENCY_KEY,
                command("US", "CA", BigDecimal.ONE)), FULFILLMENT_CROSS_BORDER_NOT_SUPPORTED);

        InOrder calls = inOrder(idempotencyMapper, tradeOrderMapper, tradeOrderItemMapper);
        calls.verify(idempotencyMapper).insert(any(FulfillmentIdempotencyDO.class));
        calls.verify(tradeOrderMapper).selectByIdForUpdate(ORDER_ID);
        calls.verify(tradeOrderItemMapper).selectListByOrderId(ORDER_ID);
        verify(shipmentMapper, never()).insert(any(ShipmentDO.class));
    }

    @Test
    void rejectsUnsupportedCountry() {
        stubOrderAndItem();

        assertServiceException(() -> service.createShipment(IDEMPOTENCY_KEY,
                command("CN", "CN", BigDecimal.ONE)), FULFILLMENT_COUNTRY_NOT_SUPPORTED);

        InOrder calls = inOrder(idempotencyMapper, tradeOrderMapper, tradeOrderItemMapper);
        calls.verify(idempotencyMapper).insert(any(FulfillmentIdempotencyDO.class));
        calls.verify(tradeOrderMapper).selectByIdForUpdate(ORDER_ID);
        calls.verify(tradeOrderItemMapper).selectListByOrderId(ORDER_ID);
        verify(shipmentMapper, never()).insert(any(ShipmentDO.class));
    }

    @Test
    void rejectsOrderInvisibleInCommandTenantBeforeInspectingInvalidRoute() {
        when(tradeOrderMapper.selectByIdForUpdate(ORDER_ID)).thenAnswer(invocation -> {
            assertEquals(TENANT_ID, TenantContextHolder.getTenantId());
            return null;
        });

        assertServiceException(() -> service.createShipment(IDEMPOTENCY_KEY,
                command("CN", "CN", BigDecimal.ONE)), FULFILLMENT_ORDER_NOT_FOUND);

        verify(tradeOrderItemMapper, never()).selectListByOrderId(anyLong());
        verify(shipmentMapper, never()).insert(any(ShipmentDO.class));
    }

    @Test
    void rejectsSkuMismatch() {
        stubOrderAndItem();
        CreateShipmentCommand command = command("US", "US", BigDecimal.ONE);
        command.getItems().get(0).setSkuId(99999L);

        assertServiceException(() -> service.createShipment(IDEMPOTENCY_KEY, command), ORDER_ITEM_NOT_FOUND);

        verify(shipmentMapper, never()).insert(any(ShipmentDO.class));
    }

    @Test
    void rejectsZeroQuantity() {
        stubOrderAndItem();

        assertServiceException(() -> service.createShipment(IDEMPOTENCY_KEY,
                command("US", "US", BigDecimal.ZERO)), FULFILLMENT_ORDER_ITEM_QUANTITY_EXCEEDED);

        verify(shipmentMapper, never()).insert(any(ShipmentDO.class));
    }

    @Test
    void rejectsAggregateQuantityExceedingOrderLine() {
        stubOrderAndItem();
        when(shipmentItemMapper.sumQuantityByOrderItemId(TENANT_ID, ORDER_ITEM_ID))
                .thenReturn(new BigDecimal("2"));

        assertServiceException(() -> service.createShipment(IDEMPOTENCY_KEY,
                command("US", "US", new BigDecimal("2"))), FULFILLMENT_ORDER_ITEM_QUANTITY_EXCEEDED);

        verify(shipmentMapper, never()).insert(any(ShipmentDO.class));
    }

    @Test
    void rejectsDuplicateOrderItemInsideOneCommand() {
        stubOrderAndItem();
        CreateShipmentCommand command = command("US", "US", BigDecimal.ONE);
        command.setItems(List.of(item(BigDecimal.ONE), item(BigDecimal.ONE)));

        assertServiceException(() -> service.createShipment(IDEMPOTENCY_KEY, command),
                FULFILLMENT_ORDER_ITEM_QUANTITY_EXCEEDED);

        verify(shipmentMapper, never()).insert(any(ShipmentDO.class));
    }

    @Test
    void rejectsNullItemWithControlledErrorBeforeCanonicalHashing() {
        CreateShipmentCommand command = command("US", "US", BigDecimal.ONE);
        command.setItems(java.util.Collections.singletonList(null));

        assertServiceException(() -> service.createShipment(IDEMPOTENCY_KEY, command), ORDER_ITEM_NOT_FOUND);

        verify(idempotencyMapper, never()).insert(any(FulfillmentIdempotencyDO.class));
    }

    @Test
    void repeatedIdenticalRequestReturnsCompletedShipment() {
        CreateShipmentCommand command = command("US", "US", BigDecimal.ONE);
        AtomicReference<String> requestHash = new AtomicReference<>();
        when(idempotencyMapper.selectByOperationAndKeyHash(eq(TENANT_ID), eq("CREATE_SHIPMENT"), anyString()))
                .thenAnswer(invocation -> new FulfillmentIdempotencyDO()
                        .setTenantId(TENANT_ID)
                        .setOperation("CREATE_SHIPMENT")
                        .setIdempotencyKeyHash(invocation.getArgument(2))
                        .setRequestHash(requestHash.get())
                        .setResourceType("SHIPMENT")
                        .setResourceId(SHIPMENT_ID)
                        .setStatus("COMPLETED"));
        doAnswer(invocation -> {
            FulfillmentIdempotencyDO attempted = invocation.getArgument(0);
            requestHash.set(attempted.getRequestHash());
            throw new DuplicateKeyException("duplicate idempotency row");
        }).when(idempotencyMapper).insert(any(FulfillmentIdempotencyDO.class));

        Long result = service.createShipment(IDEMPOTENCY_KEY, command);

        assertEquals(SHIPMENT_ID, result);
        verify(tradeOrderMapper, never()).selectByIdForUpdate(anyLong());
        verify(shipmentMapper, never()).insert(any(ShipmentDO.class));
    }

    @Test
    void repeatedKeyWithDifferentRequestHashReturnsConflict() {
        doThrow(new DuplicateKeyException("duplicate idempotency row"))
                .when(idempotencyMapper).insert(any(FulfillmentIdempotencyDO.class));
        when(idempotencyMapper.selectByOperationAndKeyHash(eq(TENANT_ID), eq("CREATE_SHIPMENT"), anyString()))
                .thenReturn(new FulfillmentIdempotencyDO()
                        .setTenantId(TENANT_ID)
                        .setRequestHash("different-request-hash")
                        .setResourceId(SHIPMENT_ID)
                        .setStatus("COMPLETED"));

        assertServiceException(() -> service.createShipment(IDEMPOTENCY_KEY,
                command("US", "US", BigDecimal.ONE)), FULFILLMENT_IDEMPOTENCY_CONFLICT);

        verify(tradeOrderMapper, never()).selectByIdForUpdate(anyLong());
    }

    @Test
    void matchingRequestStillConflictsWhileOriginalRequestIsProcessing() {
        AtomicReference<String> requestHash = new AtomicReference<>();
        doAnswer(invocation -> {
            FulfillmentIdempotencyDO attempted = invocation.getArgument(0);
            requestHash.set(attempted.getRequestHash());
            throw new DuplicateKeyException("duplicate idempotency row");
        }).when(idempotencyMapper).insert(any(FulfillmentIdempotencyDO.class));
        when(idempotencyMapper.selectByOperationAndKeyHash(eq(TENANT_ID), eq("CREATE_SHIPMENT"), anyString()))
                .thenAnswer(invocation -> new FulfillmentIdempotencyDO()
                        .setTenantId(TENANT_ID)
                        .setRequestHash(requestHash.get())
                        .setResourceId(null)
                        .setStatus("PROCESSING"));

        assertServiceException(() -> service.createShipment(IDEMPOTENCY_KEY,
                command("US", "US", BigDecimal.ONE)), FULFILLMENT_IDEMPOTENCY_CONFLICT);

        verify(shipmentMapper, never()).insert(any(ShipmentDO.class));
    }

    @Test
    void incrementsExistingSummaryWithVersionGuard() {
        stubOrderAndItem();
        OrderFulfillmentSummaryDO existing = new OrderFulfillmentSummaryDO()
                .setId(901L)
                .setTenantId(TENANT_ID)
                .setOrderId(ORDER_ID)
                .setStatus(OrderFulfillmentStatusEnum.NOT_SHIPPED.name())
                .setShipmentCount(1)
                .setDeliveredShipmentCount(0)
                .setVersion(3);
        when(summaryMapper.selectByOrderId(TENANT_ID, ORDER_ID)).thenReturn(existing);
        when(summaryMapper.updateCountsAndStatusByIdAndVersion(TENANT_ID, 901L, 3,
                OrderFulfillmentStatusEnum.NOT_SHIPPED.name(), 2, 0)).thenReturn(1);

        service.createShipment(IDEMPOTENCY_KEY, command("US", "US", BigDecimal.ONE));

        verify(summaryMapper).updateCountsAndStatusByIdAndVersion(TENANT_ID, 901L, 3,
                OrderFulfillmentStatusEnum.NOT_SHIPPED.name(), 2, 0);
        verify(summaryMapper, never()).insert(any(OrderFulfillmentSummaryDO.class));
    }

    @Test
    void staleSummaryVersionRaisesControlledConflictBeforeOutbox() {
        stubOrderAndItem();
        OrderFulfillmentSummaryDO existing = new OrderFulfillmentSummaryDO()
                .setId(901L)
                .setTenantId(TENANT_ID)
                .setOrderId(ORDER_ID)
                .setStatus(OrderFulfillmentStatusEnum.NOT_SHIPPED.name())
                .setShipmentCount(1)
                .setDeliveredShipmentCount(0)
                .setVersion(3);
        when(summaryMapper.selectByOrderId(TENANT_ID, ORDER_ID)).thenReturn(existing);
        when(summaryMapper.updateCountsAndStatusByIdAndVersion(TENANT_ID, 901L, 3,
                OrderFulfillmentStatusEnum.NOT_SHIPPED.name(), 2, 0)).thenReturn(0);

        assertServiceException(() -> service.createShipment(IDEMPOTENCY_KEY,
                command("US", "US", BigDecimal.ONE)), FULFILLMENT_VERSION_CONFLICT);

        verify(outboxMapper, never()).insert(any(FulfillmentOutboxEventDO.class));
        verify(idempotencyMapper, never()).completeProcessingById(anyLong(), anyLong(), anyString(), anyLong(), any());
    }

    @Test
    void transactionStopsBeforeOutboxWhenShipmentItemInsertFails() throws NoSuchMethodException {
        stubOrderAndItem();
        doThrow(new IllegalStateException("item insert failed"))
                .when(shipmentItemMapper).insert(any(ShipmentItemDO.class));

        assertThrows(IllegalStateException.class, () -> service.createShipment(IDEMPOTENCY_KEY,
                command("US", "US", BigDecimal.ONE)));

        assertNotNull(FulfillmentCommandServiceImpl.class
                .getMethod("createShipment", String.class, CreateShipmentCommand.class)
                .getAnnotation(Transactional.class));
        verify(shipmentMapper).insert(any(ShipmentDO.class));
        verify(outboxMapper, never()).insert(any(FulfillmentOutboxEventDO.class));
        verify(idempotencyMapper, never()).completeProcessingById(anyLong(), anyLong(), anyString(), anyLong(), any());
    }

    @Test
    void canonicalHashIsStableAcrossItemOrderingAndNumericScale() {
        CreateShipmentCommand first = command("us", "us", new BigDecimal("1.0"));
        first.setItems(List.of(
                new CreateShipmentItemCommand().setOrderItemId(502L).setSkuId(902L)
                        .setQuantity(new BigDecimal("2.00")),
                item(new BigDecimal("1.0"))));
        CreateShipmentCommand second = command("US", "US", BigDecimal.ONE);
        second.setItems(List.of(
                item(BigDecimal.ONE),
                new CreateShipmentItemCommand().setOrderItemId(502L).setSkuId(902L)
                        .setQuantity(new BigDecimal("2"))));

        assertEquals(FulfillmentHashing.sha256Command(first), FulfillmentHashing.sha256Command(second));
        String hmac = FulfillmentHashing.hmacSha256Hex("secret", IDEMPOTENCY_KEY);
        assertTrue(hmac.matches("[0-9a-f]{64}"));
        assertNotEquals(IDEMPOTENCY_KEY, hmac);
    }

    @Test
    void shipmentNumberUsesUtcDateAndSixteenUppercaseHexCharacters() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-15T23:30:00Z"), ZoneOffset.ofHours(-7));

        String shipmentNo = new FulfillmentNoGenerator(clock).generate();

        assertTrue(shipmentNo.matches("SHP-20260715-[0-9A-F]{16}"));
    }

    @Test
    void propertiesToStringDoesNotExposeHmacSecret() {
        FulfillmentProperties actual = new FulfillmentProperties();
        actual.setIdempotencyHmacKey("properties-hmac-secret");

        assertFalse(actual.toString().contains("properties-hmac-secret"));
    }

    private void stubOrderAndItem() {
        when(tradeOrderMapper.selectByIdForUpdate(ORDER_ID)).thenAnswer(invocation -> {
            assertEquals(TENANT_ID, TenantContextHolder.getTenantId());
            return new TradeOrderDO().setId(ORDER_ID);
        });
        when(tradeOrderItemMapper.selectListByOrderId(ORDER_ID)).thenReturn(List.of(new TradeOrderItemDO()
                .setId(ORDER_ITEM_ID)
                .setOrderId(ORDER_ID)
                .setSkuId(SKU_ID)
                .setCount(3)));
        lenient().when(shipmentItemMapper.sumQuantityByOrderItemId(TENANT_ID, ORDER_ITEM_ID))
                .thenReturn(BigDecimal.ZERO);
        lenient().when(summaryMapper.selectByOrderId(TENANT_ID, ORDER_ID)).thenReturn(null);
    }

    private static CreateShipmentCommand command(String originCountry, String destinationCountry, BigDecimal quantity) {
        return new CreateShipmentCommand()
                .setTenantId(TENANT_ID)
                .setOrderId(ORDER_ID)
                .setShipmentType(ShipmentTypeEnum.PARCEL)
                .setOriginCountry(originCountry)
                .setDestinationCountry(destinationCountry)
                .setOriginTimezone("America/New_York")
                .setDestinationTimezone("America/Los_Angeles")
                .setWarehouseId(31L)
                .setProviderId(41L)
                .setItems(List.of(item(quantity)));
    }

    private static CreateShipmentItemCommand item(BigDecimal quantity) {
        return new CreateShipmentItemCommand()
                .setOrderItemId(ORDER_ITEM_ID)
                .setSkuId(SKU_ID)
                .setQuantity(quantity);
    }

}
