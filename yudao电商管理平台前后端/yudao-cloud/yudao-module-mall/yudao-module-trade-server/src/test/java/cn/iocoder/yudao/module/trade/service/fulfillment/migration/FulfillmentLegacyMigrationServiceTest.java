package cn.iocoder.yudao.module.trade.service.fulfillment.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.CarrierDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.LogisticsProviderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.CarrierMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.LegacyMigrationReferenceMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.LogisticsProviderMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentPackageMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FulfillmentLegacyMigrationServiceTest extends BaseMockitoUnitTest {

    private static final Long TENANT_ID = 121L;

    @Mock private TradeOrderMapper orderMapper;
    @Mock private TradeOrderItemMapper itemMapper;
    @Mock private CarrierMapper carrierMapper;
    @Mock private ShipmentMapper shipmentMapper;
    @Mock private ShipmentPackageMapper packageMapper;
    @Mock private LogisticsProviderMapper providerMapper;
    @Mock private LegacyMigrationReferenceMapper referenceMapper;
    @Mock private LegacyMigrationFactSource factSource;
    @Mock private FulfillmentLegacyMigrationWriter writer;

    private LegacyMigrationEligibilityEvaluator evaluator;
    private FulfillmentLegacyMigrationService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        evaluator = new LegacyMigrationEligibilityEvaluator(itemMapper, carrierMapper, shipmentMapper, packageMapper,
                providerMapper, referenceMapper, factSource);
        service = new FulfillmentLegacyMigrationServiceImpl(orderMapper, evaluator, writer);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void scansExclusiveCursorInAscendingBoundedPageAndAdvancesAcrossRejectedRows() {
        TradeOrderDO notShipped = order(11L, 10, 31L, "TRACK-11");
        TradeOrderDO blankTracking = order(12L, 20, 31L, "   ");
        TradeOrderDO lookahead = order(13L, 20, 31L, "TRACK-13");
        when(orderMapper.selectLegacyMigrationCandidates(TENANT_ID, 10L, 3))
                .thenReturn(List.of(notShipped, blankTracking, lookahead));

        MigrationBatchResult result = service.migrateActiveOrders(TENANT_ID, 10L, 2, true);

        assertTrue(result.dryRun());
        assertEquals(2, result.scanned());
        assertEquals(0, result.wouldMigrate());
        assertEquals(2, result.rejected());
        assertEquals(12L, result.nextAfterOrderId());
        assertTrue(result.hasMore());
        assertEquals(List.of(MigrationOutcome.NOT_SHIPPED, MigrationOutcome.BLANK_TRACKING),
                result.orders().stream().map(MigrationOrderResult::outcome).toList());
        verify(orderMapper).selectLegacyMigrationCandidates(TENANT_ID, 10L, 3);
    }

    @Test
    void rejectsTenantMismatchAndInvalidBoundsBeforeScanning() {
        assertThrows(IllegalArgumentException.class,
                () -> service.migrateActiveOrders(999L, 0L, 10, true));
        assertThrows(IllegalArgumentException.class,
                () -> service.migrateActiveOrders(TENANT_ID, 0L, 0, true));
        assertThrows(IllegalArgumentException.class,
                () -> service.migrateActiveOrders(TENANT_ID, 0L, 101, true));
        verify(orderMapper, never()).selectLegacyMigrationCandidates(anyLong(), anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void writeModeInvokesWriterOncePerScannedOrderAndCountsOutcomes() {
        TradeOrderDO first = order(41L, 20, 31L, "TRACK-41");
        TradeOrderDO second = order(42L, 20, 31L, "TRACK-42");
        when(orderMapper.selectLegacyMigrationCandidates(TENANT_ID, 0L, 11)).thenReturn(List.of(first, second));
        when(writer.migrateOne(TENANT_ID, 41L)).thenReturn(MigrationOrderResult.of(41L, MigrationOutcome.MIGRATED));
        when(writer.migrateOne(TENANT_ID, 42L))
                .thenReturn(MigrationOrderResult.of(42L, MigrationOutcome.ALREADY_MIGRATED));

        MigrationBatchResult result = service.migrateActiveOrders(TENANT_ID, 0L, 10, false);

        assertFalse(result.dryRun());
        assertEquals(1, result.migrated());
        assertEquals(1, result.alreadyMigrated());
        assertEquals(0, result.rejected());
        verify(writer).migrateOne(TENANT_ID, 41L);
        verify(writer).migrateOne(TENANT_ID, 42L);
    }

    @Test
    void statusTenIsAlwaysNotShippedWithoutInspectingMigrationFacts() {
        MigrationOrderResult result = evaluator.evaluate(TENANT_ID, order(21L, 10, 31L, "TRACK-21"));

        assertEquals(MigrationOutcome.NOT_SHIPPED, result.outcome());
        verify(itemMapper, never()).selectListByOrderId(anyLong());
        verify(factSource, never()).findApprovedFacts(anyLong(), anyLong());
    }

    @Test
    void otherwiseValidOrderFailsClosedWhenApprovalFactsAreMissing() {
        TradeOrderDO order = order(22L, 20, 31L, "  Case-Sensitive.22  ");
        stubValidPreFactState(order);
        when(factSource.findApprovedFacts(TENANT_ID, 22L)).thenReturn(Optional.empty());

        MigrationOrderResult result = evaluator.evaluate(TENANT_ID, order);

        assertEquals(MigrationOutcome.MISSING_ROUTE_FACTS, result.outcome());
        assertEquals("MISSING_ROUTE_FACTS", result.reasonCode());
        verify(packageMapper).selectByCarrierIdAndTrackingNumber(TENANT_ID, 401L, "Case-Sensitive.22");
    }

    @Test
    void approvedDomesticFactsProduceWouldMigrateOnlyAfterReferenceValidation() {
        TradeOrderDO order = order(23L, 20, 31L, "TRACK-23");
        stubValidPreFactState(order);
        LegacyMigrationFacts facts = new LegacyMigrationFacts("US", "US", "America/New_York",
                "America/Los_Angeles", 501L, 601L, 701L,
                LocalDateTime.of(2026, 7, 16, 10, 0), "approval-ticket-23");
        when(factSource.findApprovedFacts(TENANT_ID, 23L)).thenReturn(Optional.of(facts));
        when(referenceMapper.countEnabledWarehouse(TENANT_ID, 501L)).thenReturn(1L);
        when(providerMapper.selectByIdAndTenantId(601L, TENANT_ID)).thenReturn(new LogisticsProviderDO()
                .setId(601L).setTenantId(TENANT_ID).setStatus(0));

        MigrationOrderResult result = evaluator.evaluate(TENANT_ID, order);

        assertEquals(MigrationOutcome.WOULD_MIGRATE, result.outcome());
        assertEquals("WOULD_MIGRATE", result.reasonCode());
    }

    @Test
    void rejectsNonCanonicalCountryFactsInsteadOfNormalizingThem() {
        assertRouteFactsRejected(29L, "us", "US", "America/New_York", "America/Los_Angeles");
        assertRouteFactsRejected(30L, "US ", "US", "America/New_York", "America/Los_Angeles");
    }

    @Test
    void rejectsTimezoneFactsUnlessOriginalValueIsAnExactIanaIdentifier() {
        assertRouteFactsRejected(31L, "US", "US", " America/New_York", "America/Los_Angeles");
        assertRouteFactsRejected(32L, "US", "US", "america/New_York", "America/Los_Angeles");
    }

    @Test
    void rejectsAmbiguousCarrierCrossBorderAndExistingAggregateWithoutGuessing() {
        TradeOrderDO ambiguous = order(24L, 20, 31L, "TRACK-24");
        when(carrierMapper.selectEnabledByLegacyExpressId(TENANT_ID, 31L)).thenReturn(List.of(
                carrier(401L), carrier(402L)));
        assertEquals(MigrationOutcome.INVALID_CARRIER, evaluator.evaluate(TENANT_ID, ambiguous).outcome());

        TradeOrderDO crossBorder = order(25L, 20, 31L, "TRACK-25");
        stubValidPreFactState(crossBorder);
        when(factSource.findApprovedFacts(TENANT_ID, 25L)).thenReturn(Optional.of(new LegacyMigrationFacts(
                "US", "CA", "America/New_York", "America/Toronto", 501L, 601L, 701L,
                LocalDateTime.of(2026, 7, 16, 10, 0), "approval-ticket-25")));
        assertEquals(MigrationOutcome.MISSING_ROUTE_FACTS, evaluator.evaluate(TENANT_ID, crossBorder).outcome());

        TradeOrderDO existing = order(26L, 20, 31L, "TRACK-26");
        when(carrierMapper.selectEnabledByLegacyExpressId(TENANT_ID, 31L)).thenReturn(List.of(carrier(401L)));
        when(itemMapper.selectListByOrderId(26L)).thenReturn(List.of(item(2601L, 1)));
        when(shipmentMapper.selectListByOrderId(TENANT_ID, 26L)).thenReturn(List.of(new ShipmentDO().setId(900L)));
        assertEquals(MigrationOutcome.EXISTING_FULFILLMENT, evaluator.evaluate(TENANT_ID, existing).outcome());
    }

    @Test
    void rejectsUnapprovedWarehouseAndProviderReferencesWithStableReasons() {
        TradeOrderDO missingWarehouse = order(27L, 20, 31L, "TRACK-27");
        stubValidPreFactState(missingWarehouse);
        when(factSource.findApprovedFacts(TENANT_ID, 27L)).thenReturn(Optional.of(new LegacyMigrationFacts(
                "CA", "CA", "America/Toronto", "America/Vancouver", 999L, 601L, 701L,
                LocalDateTime.of(2026, 7, 16, 10, 0), "approval-ticket-27")));
        when(referenceMapper.countEnabledWarehouse(TENANT_ID, 999L)).thenReturn(0L);

        MigrationOrderResult warehouseResult = evaluator.evaluate(TENANT_ID, missingWarehouse);

        assertEquals(MigrationOutcome.MISSING_WAREHOUSE, warehouseResult.outcome());
        assertEquals("MISSING_WAREHOUSE", warehouseResult.reasonCode());
        verify(providerMapper, never()).selectByIdAndTenantId(anyLong(), anyLong());

        TradeOrderDO disabledProvider = order(28L, 20, 31L, "TRACK-28");
        stubValidPreFactState(disabledProvider);
        when(factSource.findApprovedFacts(TENANT_ID, 28L)).thenReturn(Optional.of(new LegacyMigrationFacts(
                "US", "US", "America/New_York", "America/Chicago", 501L, 602L, 701L,
                LocalDateTime.of(2026, 7, 16, 10, 0), "approval-ticket-28")));
        when(referenceMapper.countEnabledWarehouse(TENANT_ID, 501L)).thenReturn(1L);
        when(providerMapper.selectByIdAndTenantId(602L, TENANT_ID)).thenReturn(new LogisticsProviderDO()
                .setId(602L).setTenantId(TENANT_ID).setStatus(1));

        MigrationOrderResult providerResult = evaluator.evaluate(TENANT_ID, disabledProvider);

        assertEquals(MigrationOutcome.MISSING_PROVIDER, providerResult.outcome());
        assertEquals("MISSING_PROVIDER", providerResult.reasonCode());
    }

    @Test
    void emptyPageKeepsCursorAndReportsNoMoreRows() {
        when(orderMapper.selectLegacyMigrationCandidates(TENANT_ID, 40L, 11)).thenReturn(List.of());

        MigrationBatchResult result = service.migrateActiveOrders(TENANT_ID, 40L, 10, true);

        assertEquals(0, result.scanned());
        assertEquals(40L, result.nextAfterOrderId());
        assertFalse(result.hasMore());
        assertTrue(result.orders().isEmpty());
    }

    private void stubValidPreFactState(TradeOrderDO order) {
        when(carrierMapper.selectEnabledByLegacyExpressId(TENANT_ID, order.getLogisticsId()))
                .thenReturn(List.of(carrier(401L)));
        when(itemMapper.selectListByOrderId(order.getId())).thenReturn(List.of(item(order.getId() * 100, 2)));
        when(shipmentMapper.selectListByOrderId(TENANT_ID, order.getId())).thenReturn(List.of());
        when(packageMapper.selectByCarrierIdAndTrackingNumber(TENANT_ID, 401L, order.getLogisticsNo().trim()))
                .thenReturn(null);
    }

    private void assertRouteFactsRejected(Long orderId, String originCountry, String destinationCountry,
                                          String originTimezone, String destinationTimezone) {
        TradeOrderDO order = order(orderId, 20, 31L, "TRACK-" + orderId);
        stubValidPreFactState(order);
        when(factSource.findApprovedFacts(TENANT_ID, orderId)).thenReturn(Optional.of(new LegacyMigrationFacts(
                originCountry, destinationCountry, originTimezone, destinationTimezone, 501L, 601L, 701L,
                LocalDateTime.of(2026, 7, 16, 10, 0), "approval-ticket-" + orderId)));

        MigrationOrderResult result = evaluator.evaluate(TENANT_ID, order);

        assertEquals(MigrationOutcome.MISSING_ROUTE_FACTS, result.outcome());
        verify(referenceMapper, never()).countEnabledWarehouse(anyLong(), anyLong());
    }

    private static TradeOrderDO order(Long id, int status, Long logisticsId, String tracking) {
        return new TradeOrderDO().setId(id).setStatus(status).setLogisticsId(logisticsId)
                .setLogisticsNo(tracking).setDeliveryTime(LocalDateTime.of(2026, 7, 15, 12, 0));
    }

    private static CarrierDO carrier(Long id) {
        return new CarrierDO().setId(id).setTenantId(TENANT_ID).setLegacyExpressId(31L).setStatus(0);
    }

    private static TradeOrderItemDO item(Long id, int count) {
        return new TradeOrderItemDO().setId(id).setSkuId(id + 1).setCount(count);
    }
}
