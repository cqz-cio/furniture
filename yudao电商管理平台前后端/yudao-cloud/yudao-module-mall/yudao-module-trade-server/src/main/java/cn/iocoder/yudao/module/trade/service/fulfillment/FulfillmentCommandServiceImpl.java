package cn.iocoder.yudao.module.trade.service.fulfillment;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.FulfillmentIdempotencyDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.FulfillmentOutboxEventDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.OrderFulfillmentSummaryDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentItemDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.FulfillmentIdempotencyMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.FulfillmentOutboxEventMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.OrderFulfillmentSummaryMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentItemMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.enums.fulfillment.OrderFulfillmentStatusEnum;
import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentProperties;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.CreateShipmentCommand;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.CreateShipmentItemCommand;
import cn.iocoder.yudao.module.trade.service.fulfillment.support.FulfillmentHashing;
import cn.iocoder.yudao.module.trade.service.fulfillment.support.FulfillmentNoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_COUNTRY_NOT_SUPPORTED;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_CROSS_BORDER_NOT_SUPPORTED;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_IDEMPOTENCY_CONFLICT;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_ORDER_ITEM_QUANTITY_EXCEEDED;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_ORDER_NOT_FOUND;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.ORDER_ITEM_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class FulfillmentCommandServiceImpl implements FulfillmentCommandService {

    private static final String OPERATION_CREATE_SHIPMENT = "CREATE_SHIPMENT";
    private static final String IDEMPOTENCY_PROCESSING = "PROCESSING";
    private static final String IDEMPOTENCY_COMPLETED = "COMPLETED";
    private static final String RESOURCE_SHIPMENT = "SHIPMENT";
    private static final Set<String> SUPPORTED_COUNTRIES = Set.of("US", "CA");
    private static final Set<String> IANA_TIMEZONE_IDS = Set.copyOf(ZoneId.getAvailableZoneIds());

    private final TradeOrderMapper tradeOrderMapper;
    private final TradeOrderItemMapper tradeOrderItemMapper;
    private final ShipmentMapper shipmentMapper;
    private final ShipmentItemMapper shipmentItemMapper;
    private final OrderFulfillmentSummaryMapper summaryMapper;
    private final FulfillmentIdempotencyMapper idempotencyMapper;
    private final FulfillmentOutboxEventMapper outboxMapper;
    private final FulfillmentProperties properties;
    private final FulfillmentNoGenerator noGenerator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createShipment(String idempotencyKey, CreateShipmentCommand command) {
        validateRequiredCommand(command);
        String keyHash = FulfillmentHashing.hmacSha256Hex(properties.getIdempotencyHmacKey(), idempotencyKey);
        String requestHash = FulfillmentHashing.sha256Command(command);
        AtomicReference<Long> result = new AtomicReference<>();
        TenantUtils.execute(command.getTenantId(),
                () -> result.set(createShipmentInTenant(keyHash, requestHash, command)));
        return result.get();
    }

    private Long createShipmentInTenant(String keyHash, String requestHash, CreateShipmentCommand command) {
        LocalDateTime nowUtc = LocalDateTime.now(Clock.systemUTC());
        FulfillmentIdempotencyDO idempotency = new FulfillmentIdempotencyDO()
                .setTenantId(command.getTenantId())
                .setOperation(OPERATION_CREATE_SHIPMENT)
                .setIdempotencyKeyHash(keyHash)
                .setRequestHash(requestHash)
                .setResourceType(RESOURCE_SHIPMENT)
                .setStatus(IDEMPOTENCY_PROCESSING)
                .setExpiresAt(nowUtc.plusHours(24));
        try {
            idempotencyMapper.insert(idempotency);
        } catch (DuplicateKeyException duplicate) {
            return resolveDuplicateRequest(command.getTenantId(), keyHash, requestHash);
        }

        TradeOrderDO order = tradeOrderMapper.selectByIdForUpdate(command.getOrderId());
        if (order == null) {
            throw exception(FULFILLMENT_ORDER_NOT_FOUND);
        }
        List<TradeOrderItemDO> orderItems = tradeOrderItemMapper.selectListByOrderId(command.getOrderId());
        Map<Long, TradeOrderItemDO> orderItemsById = new HashMap<>(orderItems.size());
        for (TradeOrderItemDO orderItem : orderItems) {
            orderItemsById.put(orderItem.getId(), orderItem);
        }

        String originCountry = normalizeCountry(command.getOriginCountry());
        String destinationCountry = normalizeCountry(command.getDestinationCountry());
        validateDomesticRoute(originCountry, destinationCountry);
        validateItems(command, orderItemsById);
        String originTimezone = normalizeIanaTimezone(command.getOriginTimezone());
        String destinationTimezone = normalizeIanaTimezone(command.getDestinationTimezone());

        ShipmentDO shipment = new ShipmentDO()
                .setTenantId(command.getTenantId())
                .setOrderId(command.getOrderId())
                .setShipmentNo(noGenerator.generate())
                .setShipmentType(command.getShipmentType().name())
                .setStatus(ShipmentStatusEnum.DRAFT.name())
                .setOriginCountry(originCountry)
                .setDestinationCountry(destinationCountry)
                .setOriginTimezone(originTimezone)
                .setDestinationTimezone(destinationTimezone)
                .setWarehouseId(command.getWarehouseId())
                .setProviderId(command.getProviderId())
                .setVersion(0);
        shipmentMapper.insert(shipment);
        for (CreateShipmentItemCommand item : command.getItems()) {
            shipmentItemMapper.insert(new ShipmentItemDO()
                    .setTenantId(command.getTenantId())
                    .setShipmentId(shipment.getId())
                    .setOrderItemId(item.getOrderItemId())
                    .setSkuId(item.getSkuId())
                    .setQuantity(item.getQuantity()));
        }

        upsertSummary(command.getTenantId(), command.getOrderId());
        outboxMapper.insert(new FulfillmentOutboxEventDO()
                .setTenantId(command.getTenantId())
                .setEventId(UUID.randomUUID().toString())
                .setAggregateType(RESOURCE_SHIPMENT)
                .setAggregateId(shipment.getId())
                .setEventType("SHIPMENT_CREATED")
                .setPayload(Map.of(
                        "tenantId", command.getTenantId(),
                        "orderId", command.getOrderId(),
                        "shipmentId", shipment.getId(),
                        "status", ShipmentStatusEnum.DRAFT.name()))
                .setStatus("PENDING")
                .setAttemptCount(0)
                .setNextAttemptAt(nowUtc));

        int completed = idempotencyMapper.completeProcessingById(command.getTenantId(), idempotency.getId(),
                requestHash, shipment.getId(), nowUtc.plusHours(24));
        if (completed != 1) {
            throw exception(FULFILLMENT_IDEMPOTENCY_CONFLICT);
        }
        return shipment.getId();
    }

    private Long resolveDuplicateRequest(Long tenantId, String keyHash, String requestHash) {
        FulfillmentIdempotencyDO existing = idempotencyMapper.selectByOperationAndKeyHash(
                tenantId, OPERATION_CREATE_SHIPMENT, keyHash);
        if (existing != null
                && FulfillmentHashing.constantTimeEquals(existing.getRequestHash(), requestHash)
                && IDEMPOTENCY_COMPLETED.equals(existing.getStatus())
                && existing.getResourceId() != null) {
            return existing.getResourceId();
        }
        throw exception(FULFILLMENT_IDEMPOTENCY_CONFLICT);
    }

    private void validateItems(CreateShipmentCommand command, Map<Long, TradeOrderItemDO> orderItemsById) {
        Set<Long> requestedOrderItemIds = new HashSet<>();
        for (CreateShipmentItemCommand item : command.getItems()) {
            if (item == null || item.getOrderItemId() == null || item.getSkuId() == null) {
                throw exception(ORDER_ITEM_NOT_FOUND);
            }
            if (!requestedOrderItemIds.add(item.getOrderItemId())) {
                throw exception(FULFILLMENT_ORDER_ITEM_QUANTITY_EXCEEDED);
            }
            TradeOrderItemDO orderItem = orderItemsById.get(item.getOrderItemId());
            if (orderItem == null || !item.getSkuId().equals(orderItem.getSkuId())) {
                throw exception(ORDER_ITEM_NOT_FOUND);
            }
            if (item.getQuantity() == null || item.getQuantity().signum() <= 0) {
                throw exception(FULFILLMENT_ORDER_ITEM_QUANTITY_EXCEEDED);
            }
            BigDecimal alreadyShipped = shipmentItemMapper.sumQuantityByOrderItemId(
                    command.getTenantId(), item.getOrderItemId());
            if (alreadyShipped == null) {
                alreadyShipped = BigDecimal.ZERO;
            }
            BigDecimal ordered = BigDecimal.valueOf(orderItem.getCount());
            if (alreadyShipped.add(item.getQuantity()).compareTo(ordered) > 0) {
                throw exception(FULFILLMENT_ORDER_ITEM_QUANTITY_EXCEEDED);
            }
        }
    }

    private void upsertSummary(Long tenantId, Long orderId) {
        OrderFulfillmentSummaryDO existing = summaryMapper.selectByOrderId(tenantId, orderId);
        if (existing == null) {
            summaryMapper.insert(new OrderFulfillmentSummaryDO()
                    .setTenantId(tenantId)
                    .setOrderId(orderId)
                    .setStatus(OrderFulfillmentStatusEnum.NOT_SHIPPED.name())
                    .setShipmentCount(1)
                    .setDeliveredShipmentCount(0)
                    .setVersion(0));
            return;
        }
        int shipmentCount = existing.getShipmentCount() == null ? 1 : existing.getShipmentCount() + 1;
        int deliveredCount = existing.getDeliveredShipmentCount() == null ? 0 : existing.getDeliveredShipmentCount();
        int version = existing.getVersion() == null ? 0 : existing.getVersion();
        int updated = summaryMapper.updateCountsAndStatusByIdAndVersion(tenantId, existing.getId(), version,
                existing.getStatus(), shipmentCount, deliveredCount);
        if (updated != 1) {
            throw exception(FULFILLMENT_VERSION_CONFLICT);
        }
    }

    private static void validateDomesticRoute(String originCountry, String destinationCountry) {
        if (!SUPPORTED_COUNTRIES.contains(originCountry) || !SUPPORTED_COUNTRIES.contains(destinationCountry)) {
            throw exception(FULFILLMENT_COUNTRY_NOT_SUPPORTED);
        }
        if (!originCountry.equals(destinationCountry)) {
            throw exception(FULFILLMENT_CROSS_BORDER_NOT_SUPPORTED);
        }
    }

    private static void validateRequiredCommand(CreateShipmentCommand command) {
        if (command == null || command.getTenantId() == null || command.getOrderId() == null) {
            throw exception(FULFILLMENT_ORDER_NOT_FOUND);
        }
        if (command.getShipmentType() == null || command.getWarehouseId() == null
                || command.getOriginTimezone() == null || command.getOriginTimezone().isBlank()
                || command.getDestinationTimezone() == null || command.getDestinationTimezone().isBlank()) {
            throw new IllegalArgumentException("Required shipment fields are missing");
        }
        if (command.getItems() == null || command.getItems().isEmpty()) {
            throw exception(ORDER_ITEM_NOT_FOUND);
        }
        if (command.getItems().stream().anyMatch(item -> item == null)) {
            throw exception(ORDER_ITEM_NOT_FOUND);
        }
    }

    private static String normalizeCountry(String country) {
        return country == null ? "" : country.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeIanaTimezone(String timezone) {
        String normalized = timezone.trim();
        final ZoneId zoneId;
        try {
            zoneId = ZoneId.of(normalized);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Timezone must be a valid IANA Zone ID", exception);
        }
        if (zoneId instanceof ZoneOffset || (!"UTC".equals(normalized)
                && (!normalized.contains("/") || !IANA_TIMEZONE_IDS.contains(normalized)))) {
            throw new IllegalArgumentException("Timezone must be a valid IANA Zone ID");
        }
        return normalized;
    }

}
