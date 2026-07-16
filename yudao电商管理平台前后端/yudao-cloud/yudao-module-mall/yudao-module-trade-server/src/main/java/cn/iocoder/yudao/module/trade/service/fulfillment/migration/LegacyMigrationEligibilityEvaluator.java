package cn.iocoder.yudao.module.trade.service.fulfillment.migration;

import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.CarrierDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.LogisticsProviderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.CarrierMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.LegacyMigrationReferenceMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.LogisticsProviderMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentPackageMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class LegacyMigrationEligibilityEvaluator {

    private static final Set<String> SUPPORTED_COUNTRIES = Set.of("US", "CA");
    private static final Set<String> IANA_TIMEZONE_IDS = Set.copyOf(ZoneId.getAvailableZoneIds());

    private final TradeOrderItemMapper itemMapper;
    private final CarrierMapper carrierMapper;
    private final ShipmentMapper shipmentMapper;
    private final ShipmentPackageMapper packageMapper;
    private final LogisticsProviderMapper providerMapper;
    private final LegacyMigrationReferenceMapper referenceMapper;
    private final LegacyMigrationFactSource factSource;

    public MigrationOrderResult evaluate(Long tenantId, TradeOrderDO order) {
        return inspect(tenantId, order, false, false).result();
    }

    LegacyMigrationEvaluation inspect(Long tenantId, TradeOrderDO order, boolean replay, boolean lockFacts) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("order is required");
        }
        if (TradeOrderStatusEnum.UNDELIVERED.getStatus().equals(order.getStatus())) {
            return rejected(order, MigrationOutcome.NOT_SHIPPED);
        }
        if (!TradeOrderStatusEnum.DELIVERED.getStatus().equals(order.getStatus())) {
            return rejected(order, MigrationOutcome.CONCURRENT_CHANGE);
        }

        String tracking = trimToNull(order.getLogisticsNo());
        if (tracking == null) {
            return rejected(order, MigrationOutcome.BLANK_TRACKING);
        }
        if (order.getLogisticsId() == null || TradeOrderDO.LOGISTICS_ID_NULL.equals(order.getLogisticsId())) {
            return rejected(order, MigrationOutcome.INVALID_CARRIER);
        }
        List<CarrierDO> carriers = carrierMapper.selectEnabledByLegacyExpressId(tenantId, order.getLogisticsId());
        if (carriers == null || carriers.size() != 1) {
            return rejected(order, MigrationOutcome.INVALID_CARRIER);
        }
        CarrierDO carrier = carriers.get(0);
        if (order.getDeliveryTime() == null) {
            return rejected(order, MigrationOutcome.MISSING_DELIVERY_TIME);
        }

        List<TradeOrderItemDO> items = itemMapper.selectListByOrderId(order.getId());
        if (!validItems(items)) {
            return rejected(order, MigrationOutcome.INVALID_ORDER_ITEMS);
        }
        if (!replay && !shipmentMapper.selectListByOrderId(tenantId, order.getId()).isEmpty()) {
            return rejected(order, MigrationOutcome.EXISTING_FULFILLMENT);
        }
        if (!replay && packageMapper.selectByCarrierIdAndTrackingNumber(tenantId, carrier.getId(), tracking) != null) {
            return rejected(order, MigrationOutcome.TRACKING_CONFLICT);
        }

        Optional<LegacyMigrationFacts> maybeFacts = lockFacts
                ? factSource.findApprovedFactsForUpdate(tenantId, order.getId())
                : factSource.findApprovedFacts(tenantId, order.getId());
        if (maybeFacts.isEmpty()) {
            return rejected(order, MigrationOutcome.MISSING_ROUTE_FACTS);
        }
        LegacyMigrationFacts facts = maybeFacts.get();
        if (!validRouteFacts(facts)) {
            return rejected(order, MigrationOutcome.MISSING_ROUTE_FACTS);
        }
        if (facts.warehouseId() == null || facts.warehouseId() <= 0
                || referenceMapper.countEnabledWarehouse(tenantId, facts.warehouseId()) != 1L) {
            return rejected(order, MigrationOutcome.MISSING_WAREHOUSE);
        }
        if (facts.migrationProviderId() == null || facts.migrationProviderId() <= 0) {
            return rejected(order, MigrationOutcome.MISSING_PROVIDER);
        }
        LogisticsProviderDO provider = providerMapper.selectByIdAndTenantId(facts.migrationProviderId(), tenantId);
        if (provider == null || !Integer.valueOf(0).equals(provider.getStatus())) {
            return rejected(order, MigrationOutcome.MISSING_PROVIDER);
        }
        return new LegacyMigrationEvaluation(result(order, MigrationOutcome.WOULD_MIGRATE), order,
                List.copyOf(items), carrier, facts, provider, tracking);
    }

    private static boolean validItems(List<TradeOrderItemDO> items) {
        return items != null && !items.isEmpty() && items.stream().allMatch(item -> item != null
                && item.getId() != null && item.getSkuId() != null && item.getCount() != null && item.getCount() > 0);
    }

    private static boolean validRouteFacts(LegacyMigrationFacts facts) {
        if (facts == null || facts.approvedBy() == null || facts.approvedBy() <= 0 || facts.approvedAt() == null
                || trimToNull(facts.sourceReference()) == null) {
            return false;
        }
        String origin = facts.originCountry();
        String destination = facts.destinationCountry();
        return SUPPORTED_COUNTRIES.contains(origin) && origin.equals(destination)
                && validIanaTimezone(facts.originTimezone()) && validIanaTimezone(facts.destinationTimezone());
    }

    private static boolean validIanaTimezone(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            ZoneId zone = ZoneId.of(value);
            return !(zone instanceof ZoneOffset) && ("UTC".equals(value)
                    || value.contains("/") && IANA_TIMEZONE_IDS.contains(value));
        } catch (DateTimeException invalid) {
            return false;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static MigrationOrderResult result(TradeOrderDO order, MigrationOutcome outcome) {
        return MigrationOrderResult.of(order.getId(), outcome);
    }

    private static LegacyMigrationEvaluation rejected(TradeOrderDO order, MigrationOutcome outcome) {
        return new LegacyMigrationEvaluation(result(order, outcome), order, List.of(), null, null, null, null);
    }
}
