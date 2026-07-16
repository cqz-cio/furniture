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
import java.util.Locale;
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
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("order is required");
        }
        if (TradeOrderStatusEnum.UNDELIVERED.getStatus().equals(order.getStatus())) {
            return result(order, MigrationOutcome.NOT_SHIPPED);
        }
        if (!TradeOrderStatusEnum.DELIVERED.getStatus().equals(order.getStatus())) {
            return result(order, MigrationOutcome.CONCURRENT_CHANGE);
        }

        String tracking = trimToNull(order.getLogisticsNo());
        if (tracking == null) {
            return result(order, MigrationOutcome.BLANK_TRACKING);
        }
        if (order.getLogisticsId() == null || TradeOrderDO.LOGISTICS_ID_NULL.equals(order.getLogisticsId())) {
            return result(order, MigrationOutcome.INVALID_CARRIER);
        }
        List<CarrierDO> carriers = carrierMapper.selectEnabledByLegacyExpressId(tenantId, order.getLogisticsId());
        if (carriers == null || carriers.size() != 1) {
            return result(order, MigrationOutcome.INVALID_CARRIER);
        }
        CarrierDO carrier = carriers.get(0);
        if (order.getDeliveryTime() == null) {
            return result(order, MigrationOutcome.MISSING_DELIVERY_TIME);
        }

        List<TradeOrderItemDO> items = itemMapper.selectListByOrderId(order.getId());
        if (!validItems(items)) {
            return result(order, MigrationOutcome.INVALID_ORDER_ITEMS);
        }
        if (!shipmentMapper.selectListByOrderId(tenantId, order.getId()).isEmpty()) {
            return result(order, MigrationOutcome.EXISTING_FULFILLMENT);
        }
        if (packageMapper.selectByCarrierIdAndTrackingNumber(tenantId, carrier.getId(), tracking) != null) {
            return result(order, MigrationOutcome.TRACKING_CONFLICT);
        }

        Optional<LegacyMigrationFacts> maybeFacts = factSource.findApprovedFacts(tenantId, order.getId());
        if (maybeFacts.isEmpty()) {
            return result(order, MigrationOutcome.MISSING_ROUTE_FACTS);
        }
        LegacyMigrationFacts facts = maybeFacts.get();
        if (!validRouteFacts(facts)) {
            return result(order, MigrationOutcome.MISSING_ROUTE_FACTS);
        }
        if (facts.warehouseId() == null || facts.warehouseId() <= 0
                || referenceMapper.countEnabledWarehouse(tenantId, facts.warehouseId()) != 1L) {
            return result(order, MigrationOutcome.MISSING_WAREHOUSE);
        }
        if (facts.migrationProviderId() == null || facts.migrationProviderId() <= 0) {
            return result(order, MigrationOutcome.MISSING_PROVIDER);
        }
        LogisticsProviderDO provider = providerMapper.selectByIdAndTenantId(facts.migrationProviderId(), tenantId);
        if (provider == null || !Integer.valueOf(0).equals(provider.getStatus())) {
            return result(order, MigrationOutcome.MISSING_PROVIDER);
        }
        return result(order, MigrationOutcome.WOULD_MIGRATE);
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
        String origin = normalizeCountry(facts.originCountry());
        String destination = normalizeCountry(facts.destinationCountry());
        return SUPPORTED_COUNTRIES.contains(origin) && origin.equals(destination)
                && validIanaTimezone(facts.originTimezone()) && validIanaTimezone(facts.destinationTimezone());
    }

    private static boolean validIanaTimezone(String value) {
        String timezone = trimToNull(value);
        if (timezone == null) {
            return false;
        }
        try {
            ZoneId zone = ZoneId.of(timezone);
            return !(zone instanceof ZoneOffset) && ("UTC".equals(timezone)
                    || timezone.contains("/") && IANA_TIMEZONE_IDS.contains(timezone));
        } catch (DateTimeException invalid) {
            return false;
        }
    }

    private static String normalizeCountry(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
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
}
