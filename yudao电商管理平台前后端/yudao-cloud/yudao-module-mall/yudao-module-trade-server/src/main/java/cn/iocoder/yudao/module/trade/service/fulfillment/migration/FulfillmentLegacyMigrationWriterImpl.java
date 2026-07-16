package cn.iocoder.yudao.module.trade.service.fulfillment.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.FulfillmentIdempotencyDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.FulfillmentOutboxEventDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.OrderFulfillmentSummaryDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentItemDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentLegDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentPackageDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.TrackingEventDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.FulfillmentIdempotencyMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.FulfillmentOutboxEventMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.OrderFulfillmentSummaryMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentItemMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentLegMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentPackageMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.TrackingEventMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.enums.fulfillment.OrderFulfillmentStatusEnum;
import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum;
import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentTypeEnum;
import cn.iocoder.yudao.module.trade.enums.fulfillment.TrackingEventSourceEnum;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentProperties;
import cn.iocoder.yudao.module.trade.service.fulfillment.domain.ShipmentStateMachine;
import cn.iocoder.yudao.module.trade.service.fulfillment.support.FulfillmentHashing;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FulfillmentLegacyMigrationWriterImpl implements FulfillmentLegacyMigrationWriter {

    public static final String OPERATION = "LEGACY_ORDER_MIGRATION";
    private static final String RESOURCE_SHIPMENT = "SHIPMENT";
    private static final String COMPLETED = "COMPLETED";
    private static final String PROCESSING = "PROCESSING";
    private static final int HANDED_TO_CARRIER_PRIORITY = 20;
    private static final String SYSTEM_ACTOR = "fulfillment-migration";
    private static final LocalDateTime NEVER_EXPIRES = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    private final TradeOrderMapper orderMapper;
    private final LegacyMigrationEligibilityEvaluator evaluator;
    private final FulfillmentProperties properties;
    private final FulfillmentIdempotencyMapper idempotencyMapper;
    private final ShipmentMapper shipmentMapper;
    private final ShipmentItemMapper shipmentItemMapper;
    private final ShipmentPackageMapper packageMapper;
    private final ShipmentLegMapper legMapper;
    private final TrackingEventMapper eventMapper;
    private final OrderFulfillmentSummaryMapper summaryMapper;
    private final FulfillmentOutboxEventMapper outboxMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MigrationOrderResult migrateOne(Long tenantId, Long orderId) {
        requireActiveTenant(tenantId);
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("orderId is required");
        }
        String secret = properties.getIdempotencyHmacKey();
        String keyHash = FulfillmentHashing.hmacSha256Hex(secret,
                "legacy-migration:key:v1|" + tenantId + "|" + orderId);

        // Deliberately inspect replay state before any stale/new validation. The row is re-read after
        // the order lock so concurrent calls cannot create a second aggregate.
        FulfillmentIdempotencyDO replayBeforeLock = idempotencyMapper.selectByOperationAndKeyHash(
                tenantId, OPERATION, keyHash);
        TradeOrderDO lockedOrder = orderMapper.selectByIdAndTenantIdForUpdate(tenantId, orderId);
        if (lockedOrder == null) {
            return MigrationOrderResult.of(orderId, MigrationOutcome.CONCURRENT_CHANGE);
        }
        FulfillmentIdempotencyDO existing = idempotencyMapper.selectByOperationAndKeyHashForUpdate(
                tenantId, OPERATION, keyHash);
        if (existing == null && replayBeforeLock != null) {
            return MigrationOrderResult.of(orderId, MigrationOutcome.IDEMPOTENCY_CONFLICT);
        }
        if (existing != null) {
            return resolveReplay(tenantId, lockedOrder, existing, secret);
        }

        LegacyMigrationEvaluation evaluation = evaluator.inspect(tenantId, lockedOrder, false, true);
        if (!evaluation.eligible()) {
            return evaluation.result();
        }
        String requestHash = requestHash(secret, tenantId, evaluation);
        FulfillmentIdempotencyDO idempotency = new FulfillmentIdempotencyDO()
                .setTenantId(tenantId)
                .setOperation(OPERATION)
                .setIdempotencyKeyHash(keyHash)
                .setRequestHash(requestHash)
                .setResourceType(RESOURCE_SHIPMENT)
                .setStatus(PROCESSING)
                .setExpiresAt(NEVER_EXPIRES);
        audit(idempotency);
        idempotencyMapper.insert(idempotency);

        Long shipmentId = insertAggregate(tenantId, evaluation, requestHash);
        if (idempotencyMapper.completeProcessingById(tenantId, idempotency.getId(), requestHash,
                shipmentId, NEVER_EXPIRES) != 1) {
            throw new IllegalStateException("Legacy migration idempotency completion conflict");
        }
        return MigrationOrderResult.of(orderId, MigrationOutcome.MIGRATED);
    }

    private MigrationOrderResult resolveReplay(Long tenantId, TradeOrderDO order,
                                                FulfillmentIdempotencyDO existing, String secret) {
        if (!COMPLETED.equals(existing.getStatus()) || !RESOURCE_SHIPMENT.equals(existing.getResourceType())
                || existing.getResourceId() == null) {
            return MigrationOrderResult.of(order.getId(), MigrationOutcome.IDEMPOTENCY_CONFLICT);
        }
        LegacyMigrationEvaluation current = evaluator.inspect(tenantId, order, true, true);
        if (!current.eligible()) {
            return MigrationOrderResult.of(order.getId(), MigrationOutcome.IDEMPOTENCY_CONFLICT);
        }
        String currentRequestHash = requestHash(secret, tenantId, current);
        ShipmentDO resource = shipmentMapper.selectByIdAndTenantId(existing.getResourceId(), tenantId);
        if (!FulfillmentHashing.constantTimeEquals(existing.getRequestHash(), currentRequestHash)
                || resource == null || !order.getId().equals(resource.getOrderId())) {
            return MigrationOrderResult.of(order.getId(), MigrationOutcome.IDEMPOTENCY_CONFLICT);
        }
        return MigrationOrderResult.of(order.getId(), MigrationOutcome.ALREADY_MIGRATED);
    }

    private Long insertAggregate(Long tenantId, LegacyMigrationEvaluation evaluation, String requestHash) {
        LocalDateTime occurredAt = evaluation.order().getDeliveryTime();
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        String status = ShipmentStatusEnum.HANDED_TO_CARRIER.name();
        ShipmentDO shipment = new ShipmentDO()
                .setTenantId(tenantId)
                .setOrderId(evaluation.order().getId())
                .setShipmentNo("MIG-S-" + requestHash.substring(0, 24))
                .setShipmentType(ShipmentTypeEnum.PARCEL.name())
                .setStatus(status)
                .setOriginCountry(evaluation.facts().originCountry())
                .setDestinationCountry(evaluation.facts().destinationCountry())
                .setOriginTimezone(evaluation.facts().originTimezone())
                .setDestinationTimezone(evaluation.facts().destinationTimezone())
                .setWarehouseId(evaluation.facts().warehouseId())
                .setProviderId(evaluation.provider().getId())
                .setLastEventOccurredAt(occurredAt)
                .setLastEventStatusPriority(HANDED_TO_CARRIER_PRIORITY)
                .setVersion(0);
        audit(shipment);
        shipmentMapper.insert(shipment);

        for (TradeOrderItemDO item : evaluation.items()) {
            ShipmentItemDO shipmentItem = new ShipmentItemDO()
                    .setTenantId(tenantId)
                    .setShipmentId(shipment.getId())
                    .setOrderItemId(item.getId())
                    .setSkuId(item.getSkuId())
                    .setQuantity(BigDecimal.valueOf(item.getCount()));
            audit(shipmentItem);
            shipmentItemMapper.insert(shipmentItem);
        }
        ShipmentPackageDO shipmentPackage = new ShipmentPackageDO()
                .setTenantId(tenantId)
                .setShipmentId(shipment.getId())
                .setPackageNo("MIG-P-" + requestHash.substring(0, 24))
                .setPackageType(ShipmentTypeEnum.PARCEL.name())
                .setCarrierId(evaluation.carrier().getId())
                .setTrackingNumber(evaluation.trackingNumber())
                .setStatus(status)
                .setLastEventOccurredAt(occurredAt)
                .setLastEventStatusPriority(HANDED_TO_CARRIER_PRIORITY)
                .setVersion(0);
        audit(shipmentPackage);
        packageMapper.insert(shipmentPackage);

        ShipmentLegDO leg = new ShipmentLegDO()
                .setTenantId(tenantId)
                .setShipmentId(shipment.getId())
                .setPackageId(shipmentPackage.getId())
                .setSequenceNo(1)
                .setLegType("LAST_MILE")
                .setCarrierId(evaluation.carrier().getId())
                .setProviderId(evaluation.provider().getId())
                .setTrackingNumber(evaluation.trackingNumber())
                .setStatus(status)
                .setLastEventOccurredAt(occurredAt)
                .setLastEventStatusPriority(HANDED_TO_CARRIER_PRIORITY)
                .setStartedAt(occurredAt)
                .setVersion(0);
        audit(leg);
        legMapper.insert(leg);

        String eventDigest = FulfillmentHashing.hmacSha256Hex(properties.getIdempotencyHmacKey(),
                "legacy-migration:event:v1|" + tenantId + "|" + evaluation.order().getId()
                        + "|" + requestHash);
        TrackingEventDO event = new TrackingEventDO()
                .setTenantId(tenantId)
                .setShipmentId(shipment.getId())
                .setPackageId(shipmentPackage.getId())
                .setShipmentLegId(leg.getId())
                .setProviderId(evaluation.provider().getId())
                .setExternalEventId("migration:" + eventDigest)
                .setStandardStatus(status)
                .setProviderStatus(TrackingEventSourceEnum.MIGRATION.name())
                .setProviderStatusNormalized(TrackingEventSourceEnum.MIGRATION.name())
                .setMappingVersion("MIGRATION_V1")
                .setMappingEffectiveAt(occurredAt)
                .setMappingKnown(true)
                .setTransitionDecision(ShipmentStateMachine.TransitionDecision.TIMELINE_ONLY.name())
                .setPreviousStatus(status)
                .setResultStatus(status)
                .setOccurredAt(occurredAt)
                .setOccurredTimezone(evaluation.facts().destinationTimezone())
                .setReceivedAt(nowUtc)
                .setSource(TrackingEventSourceEnum.MIGRATION.name());
        audit(event);
        eventMapper.insert(event);

        OrderFulfillmentSummaryDO summary = new OrderFulfillmentSummaryDO()
                .setTenantId(tenantId)
                .setOrderId(evaluation.order().getId())
                .setStatus(OrderFulfillmentStatusEnum.SHIPPED.name())
                .setShipmentCount(1)
                .setDeliveredShipmentCount(0)
                .setVersion(0);
        audit(summary);
        summaryMapper.insert(summary);
        FulfillmentOutboxEventDO outbox = new FulfillmentOutboxEventDO()
                .setTenantId(tenantId)
                .setEventId(toUuid(FulfillmentHashing.hmacSha256Hex(properties.getIdempotencyHmacKey(),
                        "legacy-migration:outbox:v1|" + tenantId + "|" + evaluation.order().getId())))
                .setAggregateType(RESOURCE_SHIPMENT)
                .setAggregateId(shipment.getId())
                .setEventType("LEGACY_ORDER_MIGRATED")
                .setPayload(Map.of("tenantId", tenantId, "orderId", evaluation.order().getId(),
                        "shipmentId", shipment.getId(), "shipmentStatus", status,
                        "fulfillmentStatus", OrderFulfillmentStatusEnum.SHIPPED.name()))
                .setStatus("PENDING")
                .setAttemptCount(0)
                .setNextAttemptAt(nowUtc);
        audit(outbox);
        outboxMapper.insert(outbox);
        return shipment.getId();
    }

    private static String requestHash(String secret, Long tenantId, LegacyMigrationEvaluation evaluation) {
        StringBuilder canonical = new StringBuilder(512);
        append(canonical, tenantId);
        TradeOrderDO order = evaluation.order();
        append(canonical, order.getId());
        append(canonical, order.getStatus());
        append(canonical, order.getLogisticsId());
        append(canonical, evaluation.trackingNumber());
        append(canonical, order.getDeliveryTime());
        append(canonical, evaluation.carrier().getId());
        LegacyMigrationFacts facts = evaluation.facts();
        append(canonical, facts.originCountry());
        append(canonical, facts.destinationCountry());
        append(canonical, facts.originTimezone());
        append(canonical, facts.destinationTimezone());
        append(canonical, facts.warehouseId());
        append(canonical, facts.migrationProviderId());
        append(canonical, facts.approvedBy());
        append(canonical, facts.approvedAt());
        append(canonical, facts.sourceReference());
        List<TradeOrderItemDO> items = evaluation.items().stream()
                .sorted(Comparator.comparing(TradeOrderItemDO::getId)).toList();
        append(canonical, items.size());
        for (TradeOrderItemDO item : items) {
            append(canonical, item.getId());
            append(canonical, item.getSkuId());
            append(canonical, item.getCount());
        }
        return FulfillmentHashing.hmacSha256Hex(secret, "legacy-migration:request:v1|" + canonical);
    }

    private static void append(StringBuilder target, Object value) {
        String text = value == null ? "" : value.toString();
        target.append(text.getBytes(StandardCharsets.UTF_8).length).append(':').append(text).append('|');
    }

    private static String toUuid(String digest) {
        String value = digest.substring(0, 32);
        return value.substring(0, 8) + "-" + value.substring(8, 12) + "-" + value.substring(12, 16)
                + "-" + value.substring(16, 20) + "-" + value.substring(20, 32);
    }

    private static void requireActiveTenant(Long tenantId) {
        Long activeTenant = TenantContextHolder.getRequiredTenantId();
        if (tenantId == null || !tenantId.equals(activeTenant)) {
            throw new IllegalArgumentException("tenantId must match the active tenant");
        }
    }

    private static void audit(cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO value) {
        value.setCreator(SYSTEM_ACTOR);
        value.setUpdater(SYSTEM_ACTOR);
    }

}
