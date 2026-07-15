package cn.iocoder.yudao.module.trade.service.fulfillment;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.*;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.*;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.enums.fulfillment.OrderFulfillmentStatusEnum;
import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum;
import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentTypeEnum;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentProperties;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.LogisticsProviderClient;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.LogisticsProviderRegistry;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.ProviderCapability;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.dto.TrackingRegistrationCommand;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.AddShipmentLegCommand;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.DispatchShipmentCommand;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.UpsertPackageCommand;
import cn.iocoder.yudao.module.trade.service.fulfillment.support.FulfillmentNoGenerator;
import cn.iocoder.yudao.module.trade.service.fulfillment.support.FulfillmentDispatchHashing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FulfillmentDispatchServiceTest extends BaseMockitoUnitTest {

    private static final Long TENANT_ID = 121L;
    private static final Long ORDER_ID = 100L;
    private static final Long SHIPMENT_ID = 70001L;
    private static final Long PACKAGE_ID = 71001L;
    private static final Long LEG_ID = 72001L;
    private static final Long CARRIER_ID = 73L;
    private static final Long PROVIDER_ID = 83L;
    private static final Long LEGACY_EXPRESS_ID = 93L;
    private static final String TRACKING_NUMBER = "private-tracking-123";
    private static final String IDEMPOTENCY_KEY = "private-idempotency-key";

    @InjectMocks
    private FulfillmentCommandServiceImpl service;

    @Mock private TradeOrderMapper tradeOrderMapper;
    @Mock private TradeOrderItemMapper tradeOrderItemMapper;
    @Mock private ShipmentMapper shipmentMapper;
    @Mock private ShipmentItemMapper shipmentItemMapper;
    @Mock private ShipmentPackageMapper packageMapper;
    @Mock private ShipmentLegMapper legMapper;
    @Mock private CarrierMapper carrierMapper;
    @Mock private LogisticsProviderMapper providerMapper;
    @Mock private OrderFulfillmentSummaryMapper summaryMapper;
    @Mock private FulfillmentIdempotencyMapper idempotencyMapper;
    @Mock private FulfillmentOutboxEventMapper outboxMapper;
    @Mock private FulfillmentProperties properties;
    @Mock private FulfillmentNoGenerator noGenerator;
    @Mock private LogisticsProviderRegistry providerRegistry;
    @Mock private FulfillmentTrackingRegistrationFailureService registrationFailureService;
    @Mock private LogisticsProviderClient providerClient;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getIdempotencyHmacKey()).thenReturn("test-hmac-secret");
        lenient().doAnswer(invocation -> {
            FulfillmentIdempotencyDO row = invocation.getArgument(0);
            row.setId(8001L);
            return 1;
        }).when(idempotencyMapper).insert(any(FulfillmentIdempotencyDO.class));
        lenient().when(idempotencyMapper.completeProcessingById(eq(TENANT_ID), eq(8001L), anyString(),
                anyLong(), any(LocalDateTime.class))).thenReturn(1);
        lenient().doAnswer(invocation -> {
            ShipmentPackageDO row = invocation.getArgument(0);
            row.setId(PACKAGE_ID);
            return 1;
        }).when(packageMapper).insert(any(ShipmentPackageDO.class));
        lenient().doAnswer(invocation -> {
            ShipmentLegDO row = invocation.getArgument(0);
            row.setId(LEG_ID);
            return 1;
        }).when(legMapper).insert(any(ShipmentLegDO.class));
        lenient().when(shipmentMapper.updateStatusByIdAndVersion(anyLong(), anyLong(), anyInt(), anyString(), any()))
                .thenReturn(1);
        lenient().when(packageMapper.updateStatusByIdAndVersion(anyLong(), anyLong(), anyInt(), anyString()))
                .thenReturn(1);
        lenient().when(legMapper.updateStatusByIdAndVersion(anyLong(), anyLong(), anyInt(), anyString(), any()))
                .thenReturn(1);
        lenient().when(summaryMapper.updateCountsAndStatusByIdAndVersion(anyLong(), anyLong(), anyInt(), anyString(),
                anyInt(), anyInt())).thenReturn(1);
        lenient().when(tradeOrderMapper.updateFulfillmentProjectionByIdAndStatus(anyLong(), anyInt(), anyLong(),
                anyString(), anyBoolean(), any())).thenReturn(1);
        lenient().when(carrierMapper.selectByIdAndTenantId(CARRIER_ID, TENANT_ID)).thenReturn(carrier());
        lenient().when(providerMapper.selectByIdAndTenantId(PROVIDER_ID, TENANT_ID)).thenReturn(provider());
        lenient().when(providerRegistry.getClient("mock")).thenReturn(providerClient);
        lenient().when(providerClient.getCapabilities()).thenReturn(Set.of(ProviderCapability.TRACKING_QUERY));
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TenantContextHolder.clear();
    }

    @Test
    void addsDraftPackageIdempotentlyWithoutPersistingRawKey() {
        when(shipmentMapper.selectByIdForUpdate(TENANT_ID, SHIPMENT_ID)).thenReturn(shipment(ShipmentTypeEnum.PARCEL,
                ShipmentStatusEnum.DRAFT, 0));

        Long result = service.addPackage(IDEMPOTENCY_KEY, packageCommand());

        assertEquals(PACKAGE_ID, result);
        ArgumentCaptor<ShipmentPackageDO> packageCaptor = ArgumentCaptor.forClass(ShipmentPackageDO.class);
        verify(packageMapper).insert(packageCaptor.capture());
        ShipmentPackageDO created = packageCaptor.getValue();
        assertEquals(ShipmentStatusEnum.DRAFT.name(), created.getStatus());
        assertEquals(0, created.getVersion());
        assertFalse(created.toString().contains(TRACKING_NUMBER));
        ArgumentCaptor<FulfillmentIdempotencyDO> idempotencyCaptor =
                ArgumentCaptor.forClass(FulfillmentIdempotencyDO.class);
        verify(idempotencyMapper).insert(idempotencyCaptor.capture());
        assertEquals("ADD_PACKAGE", idempotencyCaptor.getValue().getOperation());
        assertEquals("PACKAGE", idempotencyCaptor.getValue().getResourceType());
        assertFalse(idempotencyCaptor.getValue().toString().contains(IDEMPOTENCY_KEY));
        assertNotEquals(IDEMPOTENCY_KEY, idempotencyCaptor.getValue().getIdempotencyKeyHash());
    }

    @Test
    void identicalAddPackageReplayReturnsExistingPackage() {
        doThrow(new DuplicateKeyException("duplicate")).when(idempotencyMapper)
                .insert(any(FulfillmentIdempotencyDO.class));
        when(idempotencyMapper.selectByOperationAndKeyHash(eq(TENANT_ID), eq("ADD_PACKAGE"), anyString()))
                .thenAnswer(invocation -> new FulfillmentIdempotencyDO()
                        .setRequestHash(FulfillmentDispatchHashing.hash(packageCommand()))
                        .setResourceId(PACKAGE_ID).setStatus("COMPLETED"));

        assertEquals(PACKAGE_ID, service.addPackage(IDEMPOTENCY_KEY, packageCommand()));

        verify(shipmentMapper, never()).selectByIdForUpdate(anyLong(), anyLong());
        verify(packageMapper, never()).insert(any(ShipmentPackageDO.class));
    }

    @Test
    void rejectsDuplicateActiveCarrierTrackingNumber() {
        when(shipmentMapper.selectByIdForUpdate(TENANT_ID, SHIPMENT_ID)).thenReturn(shipment(ShipmentTypeEnum.PARCEL,
                ShipmentStatusEnum.DRAFT, 0));
        when(packageMapper.selectByCarrierIdAndTrackingNumber(TENANT_ID, CARRIER_ID, TRACKING_NUMBER))
                .thenReturn(new ShipmentPackageDO().setId(999L));

        assertServiceException(() -> service.addPackage(IDEMPOTENCY_KEY, packageCommand()),
                FULFILLMENT_DUPLICATE_TRACKING_NUMBER);

        verify(packageMapper, never()).insert(any(ShipmentPackageDO.class));
    }

    @Test
    void addsLtlLegWithProNumberAndProtectsSensitiveFields() {
        when(shipmentMapper.selectByIdForUpdate(TENANT_ID, SHIPMENT_ID)).thenReturn(shipment(ShipmentTypeEnum.LTL,
                ShipmentStatusEnum.DRAFT, 0));
        when(packageMapper.selectByIdAndTenantId(PACKAGE_ID, TENANT_ID)).thenReturn(packageRow(CARRIER_ID, null));
        AddShipmentLegCommand command = legCommand().setTrackingNumber(null).setProNumber("private-pro")
                .setBolNumber(null).setOriginLocation("private-origin").setDestinationLocation("private-destination");

        assertEquals(LEG_ID, service.addLeg(IDEMPOTENCY_KEY, command));

        ArgumentCaptor<ShipmentLegDO> captor = ArgumentCaptor.forClass(ShipmentLegDO.class);
        verify(legMapper).insert(captor.capture());
        assertEquals("private-pro", captor.getValue().getProNumber());
        assertFalse(captor.getValue().toString().contains("private-pro"));
        assertFalse(captor.getValue().toString().contains("private-origin"));
        verify(idempotencyMapper).insert(argThat((FulfillmentIdempotencyDO row) -> "ADD_LEG".equals(row.getOperation())
                && "LEG".equals(row.getResourceType())));
    }

    @Test
    void packageAndLegMutationsRejectNonDraftShipment() {
        when(shipmentMapper.selectByIdForUpdate(TENANT_ID, SHIPMENT_ID)).thenReturn(shipment(ShipmentTypeEnum.PARCEL,
                ShipmentStatusEnum.READY_TO_SHIP, 1));

        assertServiceException(() -> service.addPackage(IDEMPOTENCY_KEY, packageCommand()),
                FULFILLMENT_INVALID_STATUS_TRANSITION);
        assertServiceException(() -> service.addLeg("leg-key", legCommand()),
                FULFILLMENT_INVALID_STATUS_TRANSITION);

        verify(packageMapper, never()).insert(any(ShipmentPackageDO.class));
        verify(legMapper, never()).insert(any(ShipmentLegDO.class));
    }

    @Test
    void addLegRejectsPackageFromAnotherShipmentAndDisabledProvider() {
        when(shipmentMapper.selectByIdForUpdate(TENANT_ID, SHIPMENT_ID)).thenReturn(shipment(ShipmentTypeEnum.PARCEL,
                ShipmentStatusEnum.DRAFT, 0));
        when(packageMapper.selectByIdAndTenantId(PACKAGE_ID, TENANT_ID))
                .thenReturn(packageRow(CARRIER_ID, TRACKING_NUMBER).setShipmentId(99999L));

        assertServiceException(() -> service.addLeg(IDEMPOTENCY_KEY, legCommand()),
                FULFILLMENT_DISPATCH_INCOMPLETE);

        when(providerMapper.selectByIdAndTenantId(PROVIDER_ID, TENANT_ID)).thenReturn(provider().setStatus(1));
        assertServiceException(() -> service.addLeg("provider-disabled-key", legCommand()),
                FULFILLMENT_PROVIDER_NOT_AVAILABLE);
        verify(legMapper, never()).insert(any(ShipmentLegDO.class));
    }

    @Test
    void markReadyRejectsStaleVersion() {
        when(shipmentMapper.selectByIdForUpdate(TENANT_ID, SHIPMENT_ID)).thenReturn(shipment(ShipmentTypeEnum.PARCEL,
                ShipmentStatusEnum.DRAFT, 4));

        assertServiceException(() -> service.markReady(IDEMPOTENCY_KEY, TENANT_ID, SHIPMENT_ID, 3),
                FULFILLMENT_VERSION_CONFLICT);

        verify(shipmentMapper, never()).updateStatusByIdAndVersion(anyLong(), anyLong(), anyInt(), anyString(), any());
    }

    @Test
    void markReadyValidatesCompletenessAndMovesDraftToReady() {
        when(shipmentMapper.selectByIdForUpdate(TENANT_ID, SHIPMENT_ID)).thenReturn(shipment(ShipmentTypeEnum.PARCEL,
                ShipmentStatusEnum.DRAFT, 0));

        assertServiceException(() -> service.markReady(IDEMPOTENCY_KEY, TENANT_ID, SHIPMENT_ID, 0),
                FULFILLMENT_DISPATCH_INCOMPLETE);

        when(shipmentItemMapper.selectListByShipmentId(TENANT_ID, SHIPMENT_ID))
                .thenReturn(List.of(new ShipmentItemDO().setId(1L)));
        when(packageMapper.selectListByShipmentId(TENANT_ID, SHIPMENT_ID))
                .thenReturn(List.of(packageRow(CARRIER_ID, TRACKING_NUMBER)));
        when(legMapper.selectListByShipmentId(TENANT_ID, SHIPMENT_ID))
                .thenReturn(List.of(legRow(CARRIER_ID, PROVIDER_ID, TRACKING_NUMBER, null)));

        service.markReady("complete-ready-key", TENANT_ID, SHIPMENT_ID, 0);

        verify(shipmentMapper).updateStatusByIdAndVersion(eq(TENANT_ID), eq(SHIPMENT_ID), eq(0),
                eq(ShipmentStatusEnum.READY_TO_SHIP.name()), any(LocalDateTime.class));
    }

    @Test
    void eachMutationOperationUsesIsolatedReplayAndRejectsChangedRequestHash() {
        doThrow(new DuplicateKeyException("duplicate")).when(idempotencyMapper)
                .insert(any(FulfillmentIdempotencyDO.class));
        when(idempotencyMapper.selectByOperationAndKeyHash(eq(TENANT_ID), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String operation = invocation.getArgument(1);
                    String requestHash = switch (operation) {
                        case "ADD_LEG" -> FulfillmentDispatchHashing.hash(legCommand());
                        case "MARK_READY" -> FulfillmentDispatchHashing.hashMarkReady(TENANT_ID, SHIPMENT_ID, 0);
                        case "DISPATCH" -> FulfillmentDispatchHashing.hash(dispatchCommand());
                        default -> "different-hash";
                    };
                    return new FulfillmentIdempotencyDO().setRequestHash(requestHash).setResourceId(SHIPMENT_ID)
                            .setStatus("COMPLETED");
                });

        assertEquals(SHIPMENT_ID, service.addLeg("shared-key", legCommand()));
        service.markReady("shared-key", TENANT_ID, SHIPMENT_ID, 0);
        service.dispatch("shared-key", dispatchCommand());
        assertServiceException(() -> service.addPackage("shared-key", packageCommand()),
                FULFILLMENT_IDEMPOTENCY_CONFLICT);

        verify(shipmentMapper, never()).selectByIdForUpdate(anyLong(), anyLong());
    }

    @Test
    void dispatchRejectsMissingItemsPackagesLegsCarrierProviderAndParcelTracking() {
        ShipmentDO ready = shipment(ShipmentTypeEnum.PARCEL, ShipmentStatusEnum.READY_TO_SHIP, 1);
        when(shipmentMapper.selectByIdForUpdate(TENANT_ID, SHIPMENT_ID)).thenReturn(ready);
        DispatchShipmentCommand command = dispatchCommand();

        assertServiceException(() -> service.dispatch(IDEMPOTENCY_KEY, command), FULFILLMENT_DISPATCH_INCOMPLETE);
        verify(shipmentMapper, never()).updateStatusByIdAndVersion(anyLong(), anyLong(), anyInt(), anyString(), any());

        clearInvocations(shipmentMapper);
        when(shipmentMapper.selectByIdForUpdate(TENANT_ID, SHIPMENT_ID)).thenReturn(ready);
        when(shipmentItemMapper.selectListByShipmentId(TENANT_ID, SHIPMENT_ID))
                .thenReturn(List.of(new ShipmentItemDO().setId(1L)));
        assertServiceException(() -> service.dispatch("key-packages", command), FULFILLMENT_DISPATCH_INCOMPLETE);

        when(packageMapper.selectListByShipmentId(TENANT_ID, SHIPMENT_ID))
                .thenReturn(List.of(packageRow(null, null)));
        assertServiceException(() -> service.dispatch("key-carrier", command), FULFILLMENT_DISPATCH_INCOMPLETE);

        when(packageMapper.selectListByShipmentId(TENANT_ID, SHIPMENT_ID))
                .thenReturn(List.of(packageRow(CARRIER_ID, null)));
        assertServiceException(() -> service.dispatch("key-tracking", command), FULFILLMENT_DISPATCH_INCOMPLETE);

        when(packageMapper.selectListByShipmentId(TENANT_ID, SHIPMENT_ID))
                .thenReturn(List.of(packageRow(CARRIER_ID, TRACKING_NUMBER)));
        assertServiceException(() -> service.dispatch("key-legs", command), FULFILLMENT_DISPATCH_INCOMPLETE);

        when(legMapper.selectListByShipmentId(TENANT_ID, SHIPMENT_ID))
                .thenReturn(List.of(legRow(CARRIER_ID, null, null, null)));
        assertServiceException(() -> service.dispatch("key-provider", command), FULFILLMENT_DISPATCH_INCOMPLETE);
    }

    @Test
    void dispatchesLtlWithBolAndProjectsOnlyFieldsWhileAnotherShipmentIsUnshipped() {
        stubDispatchAggregate(ShipmentTypeEnum.LTL, "private-bol");
        ShipmentDO draft = shipment(ShipmentTypeEnum.PARCEL, ShipmentStatusEnum.DRAFT, 0).setId(50000L);
        ShipmentDO earlier = shipment(ShipmentTypeEnum.PARCEL, ShipmentStatusEnum.HANDED_TO_CARRIER, 1)
                .setId(60000L);
        when(shipmentMapper.selectListByOrderId(TENANT_ID, ORDER_ID)).thenReturn(List.of(draft, earlier,
                shipment(ShipmentTypeEnum.LTL, ShipmentStatusEnum.READY_TO_SHIP, 1)));
        ShipmentPackageDO earlierPackage = packageRow(CARRIER_ID, "first-order-tracking").setId(61000L)
                .setShipmentId(60000L).setStatus(ShipmentStatusEnum.HANDED_TO_CARRIER.name());
        when(packageMapper.selectListByShipmentId(TENANT_ID, 60000L)).thenReturn(List.of(earlierPackage));
        when(tradeOrderMapper.selectByIdForUpdate(ORDER_ID)).thenReturn(new TradeOrderDO().setId(ORDER_ID)
                .setStatus(TradeOrderStatusEnum.UNDELIVERED.getStatus()));

        service.dispatch(IDEMPOTENCY_KEY, dispatchCommand());

        verify(summaryMapper).updateCountsAndStatusByIdAndVersion(TENANT_ID, 9001L, 2,
                OrderFulfillmentStatusEnum.PARTIALLY_SHIPPED.name(), 3, 0);
        verify(tradeOrderMapper).updateFulfillmentProjectionByIdAndStatus(ORDER_ID,
                TradeOrderStatusEnum.UNDELIVERED.getStatus(), LEGACY_EXPRESS_ID, "first-order-tracking", false, null);
        verify(packageMapper).updateStatusByIdAndVersion(TENANT_ID, PACKAGE_ID, 0,
                ShipmentStatusEnum.HANDED_TO_CARRIER.name());
        verify(legMapper).updateStatusByIdAndVersion(eq(TENANT_ID), eq(LEG_ID), eq(0),
                eq(ShipmentStatusEnum.HANDED_TO_CARRIER.name()), any(LocalDateTime.class));
    }

    @Test
    void missingLegacyExpressMappingDoesNotBlockNewModelOrForgeLegacyProjection() {
        stubDispatchAggregate(ShipmentTypeEnum.PARCEL, null);
        when(shipmentMapper.selectListByOrderId(TENANT_ID, ORDER_ID)).thenReturn(List.of(
                shipment(ShipmentTypeEnum.PARCEL, ShipmentStatusEnum.READY_TO_SHIP, 1)));
        when(carrierMapper.selectByIdAndTenantId(CARRIER_ID, TENANT_ID))
                .thenReturn(carrier().setLegacyExpressId(null));
        service.dispatch(IDEMPOTENCY_KEY, dispatchCommand());

        verify(shipmentMapper).updateStatusByIdAndVersion(eq(TENANT_ID), eq(SHIPMENT_ID), eq(1),
                eq(ShipmentStatusEnum.HANDED_TO_CARRIER.name()), any(LocalDateTime.class));
        verify(tradeOrderMapper, never()).updateFulfillmentProjectionByIdAndStatus(anyLong(), anyInt(), anyLong(),
                anyString(), anyBoolean(), any());
    }

    @Test
    void fullyDispatchesParcelUsingLegacyExpressIdAndSkipsQueryOnlyRegistration() {
        stubDispatchAggregate(ShipmentTypeEnum.PARCEL, null);
        when(shipmentMapper.selectListByOrderId(TENANT_ID, ORDER_ID)).thenReturn(List.of(
                shipment(ShipmentTypeEnum.PARCEL, ShipmentStatusEnum.READY_TO_SHIP, 1)));
        when(tradeOrderMapper.selectByIdForUpdate(ORDER_ID)).thenReturn(new TradeOrderDO().setId(ORDER_ID)
                .setStatus(TradeOrderStatusEnum.UNDELIVERED.getStatus()));

        service.dispatch(IDEMPOTENCY_KEY, dispatchCommand());

        verify(shipmentMapper).updateStatusByIdAndVersion(eq(TENANT_ID), eq(SHIPMENT_ID), eq(1),
                eq(ShipmentStatusEnum.HANDED_TO_CARRIER.name()), any(LocalDateTime.class));
        verify(summaryMapper).updateCountsAndStatusByIdAndVersion(TENANT_ID, 9001L, 2,
                OrderFulfillmentStatusEnum.SHIPPED.name(), 1, 0);
        ArgumentCaptor<LocalDateTime> deliveredAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(tradeOrderMapper).updateFulfillmentProjectionByIdAndStatus(eq(ORDER_ID),
                eq(TradeOrderStatusEnum.UNDELIVERED.getStatus()), eq(LEGACY_EXPRESS_ID), eq(TRACKING_NUMBER), eq(true),
                deliveredAt.capture());
        assertNotNull(deliveredAt.getValue());
        ArgumentCaptor<FulfillmentOutboxEventDO> outbox = ArgumentCaptor.forClass(FulfillmentOutboxEventDO.class);
        verify(outboxMapper).insert(outbox.capture());
        assertEquals("PACKAGE_DISPATCHED", outbox.getValue().getEventType());
        assertFalse(outbox.getValue().getPayload().toString().contains(TRACKING_NUMBER));
        assertFalse(outbox.getValue().getPayload().toString().contains(IDEMPOTENCY_KEY));
        verify(providerClient, never()).registerTracking(any());
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
    }

    @Test
    void zeroLegacySentinelAndBlankTrackingAreTreatedAsEmptyProjection() {
        stubDispatchAggregate(ShipmentTypeEnum.PARCEL, null);
        when(shipmentMapper.selectListByOrderId(TENANT_ID, ORDER_ID)).thenReturn(List.of(
                shipment(ShipmentTypeEnum.PARCEL, ShipmentStatusEnum.READY_TO_SHIP, 1)));
        when(tradeOrderMapper.selectByIdForUpdate(ORDER_ID)).thenReturn(new TradeOrderDO().setId(ORDER_ID)
                .setStatus(TradeOrderStatusEnum.UNDELIVERED.getStatus()).setLogisticsId(TradeOrderDO.LOGISTICS_ID_NULL)
                .setLogisticsNo(""));

        service.dispatch(IDEMPOTENCY_KEY, dispatchCommand());

        verify(tradeOrderMapper).updateFulfillmentProjectionByIdAndStatus(eq(ORDER_ID),
                eq(TradeOrderStatusEnum.UNDELIVERED.getStatus()), eq(LEGACY_EXPRESS_ID), eq(TRACKING_NUMBER), eq(true),
                any(LocalDateTime.class));
    }

    @Test
    void nonTrackingDuplicateKeyIsNotMisreportedAsTrackingConflict() {
        when(shipmentMapper.selectByIdForUpdate(TENANT_ID, SHIPMENT_ID)).thenReturn(shipment(ShipmentTypeEnum.PARCEL,
                ShipmentStatusEnum.DRAFT, 0));
        DuplicateKeyException packageNoConflict = new DuplicateKeyException("package number conflict");
        doThrow(packageNoConflict).when(packageMapper).insert(any(ShipmentPackageDO.class));
        when(packageMapper.selectByCarrierIdAndTrackingNumber(TENANT_ID, CARRIER_ID, TRACKING_NUMBER))
                .thenReturn(null);

        assertSame(packageNoConflict,
                assertThrows(DuplicateKeyException.class,
                        () -> service.addPackage(IDEMPOTENCY_KEY, packageCommand())));
    }

    @Test
    void capableRegistrationRunsOnlyAfterCommitAndFailureCreatesSafeRetryOutbox() {
        stubDispatchAggregate(ShipmentTypeEnum.PARCEL, null);
        when(shipmentMapper.selectListByOrderId(TENANT_ID, ORDER_ID)).thenReturn(List.of(
                shipment(ShipmentTypeEnum.PARCEL, ShipmentStatusEnum.READY_TO_SHIP, 1)));
        when(tradeOrderMapper.selectByIdForUpdate(ORDER_ID)).thenReturn(new TradeOrderDO().setId(ORDER_ID)
                .setStatus(TradeOrderStatusEnum.UNDELIVERED.getStatus()));
        when(providerClient.getCapabilities()).thenReturn(Set.of(ProviderCapability.TRACKING_REGISTRATION));
        doThrow(new IllegalStateException("provider failed without sensitive values"))
                .when(providerClient).registerTracking(any(TrackingRegistrationCommand.class));
        TransactionSynchronizationManager.initSynchronization();

        service.dispatch(IDEMPOTENCY_KEY, dispatchCommand());

        verify(providerClient, never()).registerTracking(any());
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, synchronizations.size());
        synchronizations.get(0).afterCommit();
        ArgumentCaptor<TrackingRegistrationCommand> registration =
                ArgumentCaptor.forClass(TrackingRegistrationCommand.class);
        verify(providerClient).registerTracking(registration.capture());
        assertEquals(TRACKING_NUMBER, registration.getValue().getTrackingNumber());
        assertFalse(registration.getValue().toString().contains(TRACKING_NUMBER));
        verify(registrationFailureService).recordRetry(TENANT_ID, SHIPMENT_ID, PACKAGE_ID, PROVIDER_ID);
    }

    @Test
    void capableRegistrationSuccessAlsoRunsOnlyAfterCommit() {
        stubDispatchAggregate(ShipmentTypeEnum.PARCEL, null);
        when(shipmentMapper.selectListByOrderId(TENANT_ID, ORDER_ID)).thenReturn(List.of(
                shipment(ShipmentTypeEnum.PARCEL, ShipmentStatusEnum.READY_TO_SHIP, 1)));
        when(tradeOrderMapper.selectByIdForUpdate(ORDER_ID)).thenReturn(new TradeOrderDO().setId(ORDER_ID)
                .setStatus(TradeOrderStatusEnum.UNDELIVERED.getStatus()));
        when(providerClient.getCapabilities()).thenReturn(Set.of(ProviderCapability.TRACKING_REGISTRATION));
        TransactionSynchronizationManager.initSynchronization();

        service.dispatch(IDEMPOTENCY_KEY, dispatchCommand());

        verify(providerClient, never()).registerTracking(any());
        TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();
        verify(providerClient).registerTracking(any(TrackingRegistrationCommand.class));
        verify(registrationFailureService, never()).recordRetry(anyLong(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void retryPersistenceFailureEscapesAfterCommitInsteadOfLookingSuccessful() {
        stubDispatchAggregate(ShipmentTypeEnum.PARCEL, null);
        when(shipmentMapper.selectListByOrderId(TENANT_ID, ORDER_ID)).thenReturn(List.of(
                shipment(ShipmentTypeEnum.PARCEL, ShipmentStatusEnum.READY_TO_SHIP, 1)));
        when(tradeOrderMapper.selectByIdForUpdate(ORDER_ID)).thenReturn(new TradeOrderDO().setId(ORDER_ID)
                .setStatus(TradeOrderStatusEnum.UNDELIVERED.getStatus()));
        when(providerClient.getCapabilities()).thenReturn(Set.of(ProviderCapability.TRACKING_REGISTRATION));
        doThrow(new IllegalStateException("registration failed")).when(providerClient).registerTracking(any());
        doThrow(new IllegalStateException("retry persistence failed")).when(registrationFailureService)
                .recordRetry(TENANT_ID, SHIPMENT_ID, PACKAGE_ID, PROVIDER_ID);
        TransactionSynchronizationManager.initSynchronization();
        service.dispatch(IDEMPOTENCY_KEY, dispatchCommand());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit());
        assertEquals("retry persistence failed", failure.getMessage());
    }

    @Test
    void registrationFailureHelperRequiresNewTransactionAndPersistsOnlyReferenceIds() throws Exception {
        FulfillmentTrackingRegistrationFailureService helper =
                new FulfillmentTrackingRegistrationFailureService(outboxMapper);

        helper.recordRetry(TENANT_ID, SHIPMENT_ID, PACKAGE_ID, PROVIDER_ID);

        Transactional transactional = FulfillmentTrackingRegistrationFailureService.class
                .getMethod("recordRetry", Long.class, Long.class, Long.class, Long.class)
                .getAnnotation(Transactional.class);
        assertNotNull(transactional);
        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
        ArgumentCaptor<FulfillmentOutboxEventDO> captor = ArgumentCaptor.forClass(FulfillmentOutboxEventDO.class);
        verify(outboxMapper).insert(captor.capture());
        FulfillmentOutboxEventDO event = captor.getValue();
        assertEquals("TRACKING_REGISTRATION_RETRY", event.getEventType());
        assertEquals(Set.of("tenantId", "shipmentId", "packageId", "providerId"), event.getPayload().keySet());
        String serialized = event.getPayload().toString();
        assertFalse(serialized.contains(TRACKING_NUMBER));
        assertFalse(serialized.contains("private-pro"));
        assertFalse(serialized.contains("private-bol"));
        assertFalse(serialized.contains("private-origin"));
        assertFalse(serialized.contains(IDEMPOTENCY_KEY));
    }

    @Test
    void legacyProjectionFailsClosedWhenLockedOrderAlreadyHasDifferentValues() {
        stubDispatchAggregate(ShipmentTypeEnum.PARCEL, null);
        when(shipmentMapper.selectListByOrderId(TENANT_ID, ORDER_ID)).thenReturn(List.of(
                shipment(ShipmentTypeEnum.PARCEL, ShipmentStatusEnum.READY_TO_SHIP, 1)));
        when(tradeOrderMapper.selectByIdForUpdate(ORDER_ID)).thenReturn(new TradeOrderDO().setId(ORDER_ID)
                .setStatus(TradeOrderStatusEnum.UNDELIVERED.getStatus()).setLogisticsId(999L)
                .setLogisticsNo("different-existing-tracking"));

        assertServiceException(() -> service.dispatch(IDEMPOTENCY_KEY, dispatchCommand()),
                FULFILLMENT_VERSION_CONFLICT);

        verify(tradeOrderMapper, never()).updateFulfillmentProjectionByIdAndStatus(anyLong(), anyInt(), anyLong(),
                anyString(), anyBoolean(), any());
    }

    private void stubDispatchAggregate(ShipmentTypeEnum type, String bolNumber) {
        when(shipmentMapper.selectByIdForUpdate(TENANT_ID, SHIPMENT_ID))
                .thenReturn(shipment(type, ShipmentStatusEnum.READY_TO_SHIP, 1));
        when(shipmentItemMapper.selectListByShipmentId(TENANT_ID, SHIPMENT_ID))
                .thenReturn(List.of(new ShipmentItemDO().setId(1L).setTenantId(TENANT_ID).setShipmentId(SHIPMENT_ID)));
        when(packageMapper.selectListByShipmentId(TENANT_ID, SHIPMENT_ID))
                .thenReturn(List.of(packageRow(CARRIER_ID, TRACKING_NUMBER)));
        when(legMapper.selectListByShipmentId(TENANT_ID, SHIPMENT_ID))
                .thenReturn(List.of(legRow(CARRIER_ID, PROVIDER_ID,
                        type == ShipmentTypeEnum.LTL ? null : TRACKING_NUMBER, bolNumber)));
        when(summaryMapper.selectByOrderId(TENANT_ID, ORDER_ID)).thenReturn(new OrderFulfillmentSummaryDO()
                .setId(9001L).setTenantId(TENANT_ID).setOrderId(ORDER_ID).setStatus("NOT_SHIPPED")
                .setShipmentCount(1).setDeliveredShipmentCount(0).setVersion(2));
    }

    private static ShipmentDO shipment(ShipmentTypeEnum type, ShipmentStatusEnum status, int version) {
        return new ShipmentDO().setId(SHIPMENT_ID).setTenantId(TENANT_ID).setOrderId(ORDER_ID)
                .setShipmentType(type.name()).setStatus(status.name()).setVersion(version);
    }

    private static ShipmentPackageDO packageRow(Long carrierId, String tracking) {
        return new ShipmentPackageDO().setId(PACKAGE_ID).setTenantId(TENANT_ID).setShipmentId(SHIPMENT_ID)
                .setPackageNo("PKG-1").setPackageType("PARCEL").setCarrierId(carrierId).setTrackingNumber(tracking)
                .setStatus(ShipmentStatusEnum.DRAFT.name()).setVersion(0);
    }

    private static ShipmentLegDO legRow(Long carrierId, Long providerId, String tracking, String bol) {
        return new ShipmentLegDO().setId(LEG_ID).setTenantId(TENANT_ID).setShipmentId(SHIPMENT_ID)
                .setPackageId(PACKAGE_ID).setSequenceNo(1).setLegType("LAST_MILE").setCarrierId(carrierId)
                .setProviderId(providerId).setTrackingNumber(tracking).setBolNumber(bol)
                .setStatus(ShipmentStatusEnum.DRAFT.name()).setVersion(0);
    }

    private static CarrierDO carrier() {
        return new CarrierDO().setId(CARRIER_ID).setTenantId(TENANT_ID).setCode("UPS")
                .setName("Carrier").setCountryCodes("US,CA").setLegacyExpressId(LEGACY_EXPRESS_ID).setStatus(0);
    }

    private static LogisticsProviderDO provider() {
        return new LogisticsProviderDO().setId(PROVIDER_ID).setTenantId(TENANT_ID).setCode("mock")
                .setName("Mock").setCapabilities("TRACKING_QUERY").setStatus(0);
    }

    private static UpsertPackageCommand packageCommand() {
        return new UpsertPackageCommand().setTenantId(TENANT_ID).setShipmentId(SHIPMENT_ID)
                .setPackageNo("PKG-1").setPackageType("PARCEL").setCarrierId(CARRIER_ID)
                .setTrackingNumber(TRACKING_NUMBER).setWeight(new BigDecimal("10.5")).setWeightUnit("LB")
                .setLength(new BigDecimal("20")).setWidth(new BigDecimal("10")).setHeight(new BigDecimal("5"))
                .setDimensionUnit("IN");
    }

    private static AddShipmentLegCommand legCommand() {
        return new AddShipmentLegCommand().setTenantId(TENANT_ID).setShipmentId(SHIPMENT_ID)
                .setPackageId(PACKAGE_ID).setSequenceNo(1).setLegType("LAST_MILE").setCarrierId(CARRIER_ID)
                .setProviderId(PROVIDER_ID).setServiceLevel("GROUND").setTrackingNumber(TRACKING_NUMBER);
    }

    private static DispatchShipmentCommand dispatchCommand() {
        return new DispatchShipmentCommand().setTenantId(TENANT_ID).setShipmentId(SHIPMENT_ID).setExpectedVersion(1);
    }

}
