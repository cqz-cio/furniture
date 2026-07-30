package cn.iocoder.yudao.module.trade.service.fulfillment;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.FulfillmentIdempotencyDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.FulfillmentOutboxEventDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.CarrierDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.LogisticsProviderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.OrderFulfillmentSummaryDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentItemDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentLegDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentPackageDO;
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
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentFeatureGuard;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentProperties;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.LogisticsProviderClient;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.LogisticsProviderRegistry;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.ProviderCapability;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.dto.TrackingRegistrationCommand;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.dto.TrackingRegistrationResult;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.AddShipmentLegCommand;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.CreateShipmentCommand;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.CreateShipmentItemCommand;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.DispatchShipmentCommand;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.UpsertPackageCommand;
import cn.iocoder.yudao.module.trade.service.fulfillment.domain.ShipmentStateMachine;
import cn.iocoder.yudao.module.trade.service.fulfillment.support.FulfillmentDispatchHashing;
import cn.iocoder.yudao.module.trade.service.fulfillment.support.FulfillmentHashing;
import cn.iocoder.yudao.module.trade.service.fulfillment.support.FulfillmentNoGenerator;
import cn.iocoder.yudao.module.trade.service.fulfillment.support.FulfillmentPersistenceTextPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_COUNTRY_NOT_SUPPORTED;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_CROSS_BORDER_NOT_SUPPORTED;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_IDEMPOTENCY_CONFLICT;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_DISPATCH_INCOMPLETE;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_DUPLICATE_TRACKING_NUMBER;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_INVALID_STATUS_TRANSITION;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_ORDER_ITEM_QUANTITY_EXCEEDED;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_ORDER_NOT_FOUND;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_PROVIDER_NOT_AVAILABLE;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_SHIPMENT_NOT_FOUND;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.ORDER_ITEM_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class FulfillmentCommandServiceImpl implements FulfillmentCommandService {

    private static final String OPERATION_CREATE_SHIPMENT = "CREATE_SHIPMENT";
    private static final String OPERATION_ADD_PACKAGE = "ADD_PACKAGE";
    private static final String OPERATION_ADD_LEG = "ADD_LEG";
    private static final String OPERATION_MARK_READY = "MARK_READY";
    private static final String OPERATION_DISPATCH = "DISPATCH";
    private static final String IDEMPOTENCY_PROCESSING = "PROCESSING";
    private static final String IDEMPOTENCY_COMPLETED = "COMPLETED";
    private static final String RESOURCE_SHIPMENT = "SHIPMENT";
    private static final String TRACKING_UNIQUE_CONSTRAINT = "uk_package_tracking";
    private static final Set<String> PACKAGE_TYPES = Set.of("PARCEL", "CARTON", "PALLET", "FURNITURE_ITEM");
    private static final Set<String> LEG_TYPES = Set.of("FIRST_MILE", "LINEHAUL", "LAST_MILE");
    private static final Set<String> WEIGHT_UNITS = Set.of("LB", "KG");
    private static final Set<String> DIMENSION_UNITS = Set.of("IN", "CM");
    private static final Set<String> SUPPORTED_COUNTRIES = Set.of("US", "CA");
    private static final Set<String> IANA_TIMEZONE_IDS = Set.copyOf(ZoneId.getAvailableZoneIds());
    private static final Set<ShipmentStatusEnum> DISPATCHED_STATUSES = Set.of(
            ShipmentStatusEnum.HANDED_TO_CARRIER,
            ShipmentStatusEnum.IN_TRANSIT,
            ShipmentStatusEnum.AT_LOCAL_TERMINAL,
            ShipmentStatusEnum.APPOINTMENT_REQUIRED,
            ShipmentStatusEnum.APPOINTMENT_CONFIRMED,
            ShipmentStatusEnum.OUT_FOR_DELIVERY,
            ShipmentStatusEnum.DELIVERED,
            ShipmentStatusEnum.DELIVERY_EXCEPTION,
            ShipmentStatusEnum.RETURNING,
            ShipmentStatusEnum.RETURNED);
    private static final Set<ShipmentStatusEnum> COMPLETED_LEG_STATUSES = Set.of(
            ShipmentStatusEnum.CANCELED,
            ShipmentStatusEnum.DELIVERED,
            ShipmentStatusEnum.RETURNED);

    private final TradeOrderMapper tradeOrderMapper;
    private final TradeOrderItemMapper tradeOrderItemMapper;
    private final ShipmentMapper shipmentMapper;
    private final ShipmentItemMapper shipmentItemMapper;
    private final ShipmentPackageMapper packageMapper;
    private final ShipmentLegMapper legMapper;
    private final CarrierMapper carrierMapper;
    private final LogisticsProviderMapper providerMapper;
    private final OrderFulfillmentSummaryMapper summaryMapper;
    private final FulfillmentIdempotencyMapper idempotencyMapper;
    private final FulfillmentOutboxEventMapper outboxMapper;
    private final FulfillmentProperties properties;
    private final FulfillmentFeatureGuard featureGuard;
    private final FulfillmentNoGenerator noGenerator;
    private final LogisticsProviderRegistry providerRegistry;
    private final FulfillmentTrackingRegistrationFailureService registrationFailureService;
    private final ShipmentStateMachine stateMachine = new ShipmentStateMachine();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createShipment(String idempotencyKey, CreateShipmentCommand command) {
        featureGuard.requireWriteEnabled();
        validateRequiredCommand(command);
        String keyHash = FulfillmentHashing.hmacSha256Hex(properties.getIdempotencyHmacKey(), idempotencyKey);
        String requestHash = FulfillmentHashing.sha256Command(command);
        AtomicReference<Long> result = new AtomicReference<>();
        TenantUtils.execute(command.getTenantId(),
                () -> result.set(createShipmentInTenant(keyHash, requestHash, command)));
        return result.get();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addPackage(String idempotencyKey, UpsertPackageCommand command) {
        featureGuard.requireWriteEnabled();
        validatePackageCommand(command);
        return executeMutation(idempotencyKey, command.getTenantId(), OPERATION_ADD_PACKAGE, "PACKAGE",
                FulfillmentDispatchHashing.hash(command), () -> addPackageInTenant(command));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addLeg(String idempotencyKey, AddShipmentLegCommand command) {
        featureGuard.requireWriteEnabled();
        validateLegCommand(command);
        return executeMutation(idempotencyKey, command.getTenantId(), OPERATION_ADD_LEG, "LEG",
                FulfillmentDispatchHashing.hash(command), () -> addLegInTenant(command));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markReady(String idempotencyKey, Long tenantId, Long shipmentId, Integer expectedVersion) {
        featureGuard.requireWriteEnabled();
        validateMutationIdentity(tenantId, shipmentId, expectedVersion);
        executeMutation(idempotencyKey, tenantId, OPERATION_MARK_READY, RESOURCE_SHIPMENT,
                FulfillmentDispatchHashing.hashMarkReady(tenantId, shipmentId, expectedVersion), () -> {
                    ShipmentDO shipment = lockShipment(tenantId, shipmentId);
                    validateVersion(shipment, expectedVersion);
                    requireStatus(shipment, ShipmentStatusEnum.DRAFT);
                    loadAndValidateCompleteness(tenantId, shipment);
                    LocalDateTime nowUtc = LocalDateTime.now(Clock.systemUTC());
                    requireTransition(shipment, ShipmentStatusEnum.READY_TO_SHIP, nowUtc);
                    updateShipmentStatus(tenantId, shipment, ShipmentStatusEnum.READY_TO_SHIP, nowUtc);
                    return shipmentId;
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dispatch(String idempotencyKey, DispatchShipmentCommand command) {
        featureGuard.requireWriteEnabled();
        if (command == null) {
            throw exception(FULFILLMENT_SHIPMENT_NOT_FOUND);
        }
        validateMutationIdentity(command.getTenantId(), command.getShipmentId(), command.getExpectedVersion());
        executeMutation(idempotencyKey, command.getTenantId(), OPERATION_DISPATCH, RESOURCE_SHIPMENT,
                FulfillmentDispatchHashing.hash(command), () -> dispatchInTenant(command));
    }

    private Long addPackageInTenant(UpsertPackageCommand command) {
        ShipmentDO shipment = lockShipment(command.getTenantId(), command.getShipmentId());
        requireStatus(shipment, ShipmentStatusEnum.DRAFT);
        validateVersion(shipment, command.getExpectedVersion());
        String trackingNumber = normalizeOptional(command.getTrackingNumber());
        if (command.getCarrierId() != null) {
            requireEnabledCarrier(command.getTenantId(), command.getCarrierId());
        }
        if (command.getCarrierId() != null && trackingNumber != null
                && packageMapper.selectByCarrierIdAndTrackingNumber(command.getTenantId(), command.getCarrierId(),
                trackingNumber) != null) {
            throw exception(FULFILLMENT_DUPLICATE_TRACKING_NUMBER);
        }
        ShipmentPackageDO shipmentPackage = new ShipmentPackageDO()
                .setTenantId(command.getTenantId())
                .setShipmentId(command.getShipmentId())
                .setPackageNo(command.getPackageNo().trim())
                .setPackageType(normalizeRequiredVocabulary(command.getPackageType(), PACKAGE_TYPES))
                .setCarrierId(command.getCarrierId())
                .setTrackingNumber(trackingNumber)
                .setWeight(command.getWeight())
                .setWeightUnit(normalizeOptionalVocabulary(command.getWeightUnit(), WEIGHT_UNITS))
                .setLength(command.getLength())
                .setWidth(command.getWidth())
                .setHeight(command.getHeight())
                .setDimensionUnit(normalizeOptionalVocabulary(command.getDimensionUnit(), DIMENSION_UNITS))
                .setStatus(ShipmentStatusEnum.DRAFT.name())
                .setVersion(0);
        try {
            packageMapper.insert(shipmentPackage);
        } catch (DuplicateKeyException duplicate) {
            if (isTrackingConstraintViolation(duplicate)) {
                throw exception(FULFILLMENT_DUPLICATE_TRACKING_NUMBER);
            }
            throw duplicate;
        }
        incrementShipmentVersion(command.getTenantId(), shipment);
        return shipmentPackage.getId();
    }

    private Long addLegInTenant(AddShipmentLegCommand command) {
        ShipmentDO shipment = lockShipment(command.getTenantId(), command.getShipmentId());
        requireStatus(shipment, ShipmentStatusEnum.DRAFT);
        validateVersion(shipment, command.getExpectedVersion());
        requireEnabledCarrier(command.getTenantId(), command.getCarrierId());
        requireEnabledProvider(command.getTenantId(), command.getProviderId());
        if (command.getPackageId() != null) {
            ShipmentPackageDO shipmentPackage = packageMapper.selectByIdAndTenantId(
                    command.getPackageId(), command.getTenantId());
            if (shipmentPackage == null || !command.getShipmentId().equals(shipmentPackage.getShipmentId())) {
                throw exception(FULFILLMENT_DISPATCH_INCOMPLETE);
            }
        }
        ShipmentLegDO leg = new ShipmentLegDO()
                .setTenantId(command.getTenantId())
                .setShipmentId(command.getShipmentId())
                .setPackageId(command.getPackageId())
                .setSequenceNo(command.getSequenceNo())
                .setLegType(normalizeRequiredVocabulary(command.getLegType(), LEG_TYPES))
                .setCarrierId(command.getCarrierId())
                .setProviderId(command.getProviderId())
                .setServiceLevel(normalizeOptional(command.getServiceLevel()))
                .setTrackingNumber(normalizeOptional(command.getTrackingNumber()))
                .setProNumber(normalizeOptional(command.getProNumber()))
                .setBolNumber(normalizeOptional(command.getBolNumber()))
                .setOriginLocation(FulfillmentPersistenceTextPolicy.location(command.getOriginLocation()))
                .setDestinationLocation(FulfillmentPersistenceTextPolicy.location(command.getDestinationLocation()))
                .setStatus(ShipmentStatusEnum.DRAFT.name())
                .setVersion(0);
        legMapper.insert(leg);
        incrementShipmentVersion(command.getTenantId(), shipment);
        return leg.getId();
    }

    private Long dispatchInTenant(DispatchShipmentCommand command) {
        Long tenantId = command.getTenantId();
        ShipmentDO shipment = lockShipment(tenantId, command.getShipmentId());
        validateVersion(shipment, command.getExpectedVersion());
        requireStatus(shipment, ShipmentStatusEnum.READY_TO_SHIP);
        DispatchAggregate aggregate = loadAndValidateCompleteness(tenantId, shipment);
        LocalDateTime nowUtc = LocalDateTime.now(Clock.systemUTC());
        requireTransition(shipment, ShipmentStatusEnum.HANDED_TO_CARRIER, nowUtc);

        for (PackageDispatchPlan plan : aggregate.packagePlans()) {
            ShipmentPackageDO shipmentPackage = plan.shipmentPackage();
            int updated = packageMapper.updateStatusByIdAndVersion(tenantId, shipmentPackage.getId(),
                    shipmentPackage.getVersion(), ShipmentStatusEnum.HANDED_TO_CARRIER.name());
            if (updated != 1) {
                throw exception(FULFILLMENT_VERSION_CONFLICT);
            }
        }
        Map<Long, ShipmentLegDO> selectedLegs = new LinkedHashMap<>();
        for (PackageDispatchPlan plan : aggregate.packagePlans()) {
            selectedLegs.putIfAbsent(plan.activeLeg().getId(), plan.activeLeg());
        }
        for (ShipmentLegDO activeLeg : selectedLegs.values()) {
            int legUpdated = legMapper.updateStatusByIdAndVersion(tenantId, activeLeg.getId(), activeLeg.getVersion(),
                    ShipmentStatusEnum.HANDED_TO_CARRIER.name(), nowUtc);
            if (legUpdated != 1) {
                throw exception(FULFILLMENT_VERSION_CONFLICT);
            }
        }
        updateShipmentStatus(tenantId, shipment, ShipmentStatusEnum.HANDED_TO_CARRIER, nowUtc);

        List<ShipmentDO> orderShipments = shipmentMapper.selectListByOrderId(tenantId, shipment.getOrderId());
        List<ShipmentDO> activeShipments = orderShipments.stream()
                .filter(candidate -> !ShipmentStatusEnum.CANCELED.name().equals(candidate.getStatus()))
                .sorted(Comparator.comparing(ShipmentDO::getId))
                .toList();
        boolean allDispatched = !activeShipments.isEmpty() && activeShipments.stream()
                .allMatch(candidate -> candidate.getId().equals(shipment.getId())
                        || isDispatchedStatus(candidate.getStatus()));
        updateDispatchSummary(tenantId, shipment.getOrderId(), activeShipments, allDispatched);

        LegacyProjection projection = findFirstActiveLegacyProjection(tenantId, activeShipments, shipment.getId());
        if (projection != null) {
            projectLegacyOrder(shipment.getOrderId(), projection, allDispatched, nowUtc);
        }

        for (PackageDispatchPlan plan : aggregate.packagePlans()) {
            ShipmentPackageDO dispatchedPackage = plan.shipmentPackage();
            outboxMapper.insert(new FulfillmentOutboxEventDO()
                    .setTenantId(tenantId)
                    .setEventId(UUID.randomUUID().toString())
                    .setAggregateType(RESOURCE_SHIPMENT)
                    .setAggregateId(shipment.getId())
                    .setEventType("PACKAGE_DISPATCHED")
                    .setPayload(Map.of(
                            "tenantId", tenantId,
                            "orderId", shipment.getOrderId(),
                            "shipmentId", shipment.getId(),
                            "packageId", dispatchedPackage.getId()))
                    .setStatus("PENDING")
                    .setAttemptCount(0)
                    .setNextAttemptAt(nowUtc));
            scheduleTrackingRegistration(tenantId, shipment.getId(), dispatchedPackage, plan.activeLeg(),
                    aggregate.providersById());
        }
        return shipment.getId();
    }

    private DispatchAggregate loadAndValidateCompleteness(Long tenantId, ShipmentDO shipment) {
        List<ShipmentItemDO> items = shipmentItemMapper.selectListByShipmentId(tenantId, shipment.getId());
        List<ShipmentPackageDO> selectedPackageRows = packageMapper.selectListByShipmentId(tenantId, shipment.getId());
        List<ShipmentPackageDO> packageRows = selectedPackageRows == null ? List.of() : selectedPackageRows;
        List<ShipmentLegDO> selectedLegRows = legMapper.selectListByShipmentId(tenantId, shipment.getId());
        List<ShipmentLegDO> legRows = selectedLegRows == null ? List.of() : selectedLegRows;
        List<ShipmentPackageDO> packages = packageRows.stream()
                .filter(shipmentPackage -> !ShipmentStatusEnum.CANCELED.name().equals(shipmentPackage.getStatus()))
                .sorted(Comparator.comparing(ShipmentPackageDO::getId)).toList();
        List<ShipmentLegDO> legs = legRows.stream()
                .filter(leg -> !ShipmentStatusEnum.CANCELED.name().equals(leg.getStatus()))
                .sorted(Comparator.comparing(ShipmentLegDO::getSequenceNo).thenComparing(ShipmentLegDO::getId))
                .toList();
        if (items == null || items.isEmpty() || packages == null || packages.isEmpty()
                || legs == null || legs.isEmpty()) {
            throw exception(FULFILLMENT_DISPATCH_INCOMPLETE);
        }
        Map<Long, CarrierDO> carriersById = new HashMap<>();
        for (ShipmentPackageDO shipmentPackage : packages) {
            if (shipmentPackage.getCarrierId() == null) {
                throw exception(FULFILLMENT_DISPATCH_INCOMPLETE);
            }
            carriersById.put(shipmentPackage.getCarrierId(),
                    requireEnabledCarrier(tenantId, shipmentPackage.getCarrierId()));
            if (ShipmentTypeEnum.PARCEL.name().equals(shipment.getShipmentType())
                    && normalizeOptional(shipmentPackage.getTrackingNumber()) == null) {
                throw exception(FULFILLMENT_DISPATCH_INCOMPLETE);
            }
        }
        Map<Long, LogisticsProviderDO> providersById = new HashMap<>();
        for (ShipmentLegDO leg : legs) {
            if (leg.getCarrierId() == null || leg.getProviderId() == null) {
                throw exception(FULFILLMENT_DISPATCH_INCOMPLETE);
            }
            carriersById.put(leg.getCarrierId(), requireEnabledCarrier(tenantId, leg.getCarrierId()));
            LogisticsProviderDO provider = requireEnabledProvider(tenantId, leg.getProviderId());
            providersById.put(provider.getId(), provider);
            requireProviderClient(provider.getCode());
            if (ShipmentTypeEnum.LTL.name().equals(shipment.getShipmentType())
                    && normalizeOptional(leg.getProNumber()) == null && normalizeOptional(leg.getBolNumber()) == null) {
                throw exception(FULFILLMENT_DISPATCH_INCOMPLETE);
            }
        }
        Set<Long> activePackageIds = packages.stream().map(ShipmentPackageDO::getId)
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> allPackageIds = packageRows.stream().map(ShipmentPackageDO::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (legRows.stream().anyMatch(leg -> leg.getPackageId() != null
                && !allPackageIds.contains(leg.getPackageId()))) {
            throw exception(FULFILLMENT_DISPATCH_INCOMPLETE);
        }
        List<ShipmentLegDO> activeLegs = legRows.stream().filter(FulfillmentCommandServiceImpl::isActiveLeg)
                .sorted(Comparator.comparing(ShipmentLegDO::getSequenceNo).thenComparing(ShipmentLegDO::getId))
                .toList();
        if (activeLegs.stream().anyMatch(leg -> leg.getPackageId() != null
                && !activePackageIds.contains(leg.getPackageId()))) {
            throw exception(FULFILLMENT_DISPATCH_INCOMPLETE);
        }
        List<PackageDispatchPlan> packagePlans = buildPackageDispatchPlans(packages, activeLegs);
        return new DispatchAggregate(items, packages, legs, packagePlans, carriersById, providersById);
    }

    private List<PackageDispatchPlan> buildPackageDispatchPlans(List<ShipmentPackageDO> packages,
                                                                 List<ShipmentLegDO> legs) {
        return packages.stream().map(shipmentPackage -> {
            ShipmentLegDO activeLeg = legs.stream()
                    .filter(leg -> leg.getPackageId() == null
                            || shipmentPackage.getId().equals(leg.getPackageId()))
                    .min(Comparator.comparing(ShipmentLegDO::getSequenceNo).thenComparing(ShipmentLegDO::getId))
                    .orElseThrow(() -> exception(FULFILLMENT_DISPATCH_INCOMPLETE));
            return new PackageDispatchPlan(shipmentPackage, activeLeg);
        }).toList();
    }

    private void updateDispatchSummary(Long tenantId, Long orderId, List<ShipmentDO> activeShipments,
                                       boolean allDispatched) {
        OrderFulfillmentSummaryDO summary = summaryMapper.selectByOrderId(tenantId, orderId);
        if (summary == null) {
            throw exception(FULFILLMENT_VERSION_CONFLICT);
        }
        String status = allDispatched ? OrderFulfillmentStatusEnum.SHIPPED.name()
                : OrderFulfillmentStatusEnum.PARTIALLY_SHIPPED.name();
        int deliveredCount = summary.getDeliveredShipmentCount() == null ? 0 : summary.getDeliveredShipmentCount();
        int updated = summaryMapper.updateCountsAndStatusByIdAndVersion(tenantId, summary.getId(),
                summary.getVersion(), status, activeShipments.size(), deliveredCount);
        if (updated != 1) {
            throw exception(FULFILLMENT_VERSION_CONFLICT);
        }
    }

    private LegacyProjection findFirstActiveLegacyProjection(Long tenantId, List<ShipmentDO> activeShipments,
                                                              Long dispatchedShipmentId) {
        for (ShipmentDO candidateShipment : activeShipments.stream()
                .filter(candidate -> candidate.getId().equals(dispatchedShipmentId)
                        || isDispatchedStatus(candidate.getStatus()))
                .toList()) {
            List<ShipmentPackageDO> packages = packageMapper.selectListByShipmentId(tenantId, candidateShipment.getId());
            if (packages == null) {
                continue;
            }
            for (ShipmentPackageDO candidatePackage : packages.stream()
                    .filter(candidate -> !ShipmentStatusEnum.CANCELED.name().equals(candidate.getStatus()))
                    .sorted(Comparator.comparing(ShipmentPackageDO::getId)).toList()) {
                String trackingNumber = normalizeOptional(candidatePackage.getTrackingNumber());
                if (candidatePackage.getCarrierId() == null || trackingNumber == null) {
                    return null;
                }
                CarrierDO carrier = requireEnabledCarrier(tenantId, candidatePackage.getCarrierId());
                if (carrier.getLegacyExpressId() == null) {
                    return null;
                }
                return new LegacyProjection(carrier.getLegacyExpressId(), trackingNumber);
            }
        }
        return null;
    }

    private void projectLegacyOrder(Long orderId, LegacyProjection projection, boolean allDispatched,
                                    LocalDateTime nowUtc) {
        TradeOrderDO order = tradeOrderMapper.selectByIdForUpdate(orderId);
        if (order == null || !TradeOrderStatusEnum.UNDELIVERED.getStatus().equals(order.getStatus())) {
            throw exception(FULFILLMENT_VERSION_CONFLICT);
        }
        boolean emptyProjection = (order.getLogisticsId() == null
                || TradeOrderDO.LOGISTICS_ID_NULL.equals(order.getLogisticsId()))
                && normalizeOptional(order.getLogisticsNo()) == null;
        boolean sameProjection = projection.legacyExpressId().equals(order.getLogisticsId())
                && projection.trackingNumber().equals(normalizeOptional(order.getLogisticsNo()));
        if (!emptyProjection && !sameProjection) {
            throw exception(FULFILLMENT_VERSION_CONFLICT);
        }
        if (sameProjection && !allDispatched) {
            return;
        }
        int updated = tradeOrderMapper.updateFulfillmentProjectionByIdAndStatus(orderId,
                TradeOrderStatusEnum.UNDELIVERED.getStatus(), projection.legacyExpressId(),
                projection.trackingNumber(), allDispatched, allDispatched ? nowUtc : null);
        if (updated != 1) {
            throw exception(FULFILLMENT_VERSION_CONFLICT);
        }
    }

    private void scheduleTrackingRegistration(Long tenantId, Long shipmentId, ShipmentPackageDO shipmentPackage,
                                              ShipmentLegDO activeLeg,
                                              Map<Long, LogisticsProviderDO> providersById) {
        String activeLegTrackingNumber = normalizeOptional(activeLeg.getTrackingNumber());
        final String registrationTrackingNumber;
        final Long registrationCarrierId;
        if (activeLegTrackingNumber != null) {
            registrationTrackingNumber = activeLegTrackingNumber;
            registrationCarrierId = activeLeg.getCarrierId();
        } else {
            if (!Objects.equals(shipmentPackage.getCarrierId(), activeLeg.getCarrierId())) {
                return;
            }
            registrationTrackingNumber = normalizeOptional(shipmentPackage.getTrackingNumber());
            registrationCarrierId = shipmentPackage.getCarrierId();
            if (registrationTrackingNumber == null) {
                return;
            }
        }
        LogisticsProviderDO provider = providersById.get(activeLeg.getProviderId());
        if (provider == null) {
            throw exception(FULFILLMENT_PROVIDER_NOT_AVAILABLE);
        }
        LogisticsProviderClient client = requireProviderClient(provider.getCode());
        if (!client.getCapabilities().contains(ProviderCapability.TRACKING_REGISTRATION)) {
            return;
        }
        CarrierDO carrier = requireEnabledCarrier(tenantId, registrationCarrierId);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Dispatch requires an active transaction synchronization");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                boolean retryRequired;
                try {
                    TrackingRegistrationResult result = client.registerTracking(new TrackingRegistrationCommand()
                            .setCarrierCode(carrier.getCode())
                            .setTrackingNumber(registrationTrackingNumber));
                    retryRequired = result == null || !result.isRegistered();
                } catch (RuntimeException registrationFailure) {
                    retryRequired = true;
                }
                if (retryRequired) {
                    registrationFailureService.recordRetry(tenantId, shipmentId, shipmentPackage.getId(),
                            provider.getId());
                }
            }
        });
    }

    private LogisticsProviderClient requireProviderClient(String providerCode) {
        LogisticsProviderClient client = providerRegistry.getClient(providerCode);
        if (client == null) {
            throw exception(FULFILLMENT_PROVIDER_NOT_AVAILABLE);
        }
        return client;
    }

    private CarrierDO requireEnabledCarrier(Long tenantId, Long carrierId) {
        CarrierDO carrier = carrierMapper.selectByIdAndTenantId(carrierId, tenantId);
        if (carrier == null || carrier.getStatus() == null || carrier.getStatus() != 0) {
            throw exception(FULFILLMENT_DISPATCH_INCOMPLETE);
        }
        return carrier;
    }

    private LogisticsProviderDO requireEnabledProvider(Long tenantId, Long providerId) {
        LogisticsProviderDO provider = providerMapper.selectByIdAndTenantId(providerId, tenantId);
        if (provider == null || provider.getStatus() == null || provider.getStatus() != 0) {
            throw exception(FULFILLMENT_PROVIDER_NOT_AVAILABLE);
        }
        return provider;
    }

    private Long executeMutation(String idempotencyKey, Long tenantId, String operation, String resourceType,
                                 String requestHash, Supplier<Long> action) {
        String keyHash = FulfillmentHashing.hmacSha256Hex(properties.getIdempotencyHmacKey(), idempotencyKey);
        AtomicReference<Long> result = new AtomicReference<>();
        TenantUtils.execute(tenantId, () -> result.set(executeMutationInTenant(
                tenantId, operation, resourceType, keyHash, requestHash, action)));
        return result.get();
    }

    private Long executeMutationInTenant(Long tenantId, String operation, String resourceType, String keyHash,
                                         String requestHash, Supplier<Long> action) {
        LocalDateTime expiresAt = LocalDateTime.now(Clock.systemUTC()).plusHours(24);
        FulfillmentIdempotencyDO idempotency = new FulfillmentIdempotencyDO()
                .setTenantId(tenantId)
                .setOperation(operation)
                .setIdempotencyKeyHash(keyHash)
                .setRequestHash(requestHash)
                .setResourceType(resourceType)
                .setStatus(IDEMPOTENCY_PROCESSING)
                .setExpiresAt(expiresAt);
        try {
            idempotencyMapper.insert(idempotency);
        } catch (DuplicateKeyException duplicate) {
            return resolveMutationDuplicate(tenantId, operation, keyHash, requestHash);
        }
        Long resourceId = action.get();
        int completed = idempotencyMapper.completeProcessingById(tenantId, idempotency.getId(), requestHash,
                resourceId, expiresAt);
        if (completed != 1) {
            throw exception(FULFILLMENT_IDEMPOTENCY_CONFLICT);
        }
        return resourceId;
    }

    private Long resolveMutationDuplicate(Long tenantId, String operation, String keyHash, String requestHash) {
        FulfillmentIdempotencyDO existing = idempotencyMapper.selectByOperationAndKeyHash(tenantId, operation, keyHash);
        if (existing != null && FulfillmentHashing.constantTimeEquals(existing.getRequestHash(), requestHash)
                && IDEMPOTENCY_COMPLETED.equals(existing.getStatus()) && existing.getResourceId() != null) {
            return existing.getResourceId();
        }
        throw exception(FULFILLMENT_IDEMPOTENCY_CONFLICT);
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

    private ShipmentDO lockShipment(Long tenantId, Long shipmentId) {
        ShipmentDO shipment = shipmentMapper.selectByIdForUpdate(tenantId, shipmentId);
        if (shipment == null) {
            throw exception(FULFILLMENT_SHIPMENT_NOT_FOUND);
        }
        return shipment;
    }

    private static void validateVersion(ShipmentDO shipment, Integer expectedVersion) {
        if (!expectedVersion.equals(shipment.getVersion())) {
            throw exception(FULFILLMENT_VERSION_CONFLICT);
        }
    }

    private static void requireStatus(ShipmentDO shipment, ShipmentStatusEnum required) {
        if (!required.name().equals(shipment.getStatus())) {
            throw exception(FULFILLMENT_INVALID_STATUS_TRANSITION);
        }
    }

    private void requireTransition(ShipmentDO shipment, ShipmentStatusEnum target, LocalDateTime occurredAt) {
        ShipmentStateMachine.TransitionDecision decision = stateMachine.decide(
                ShipmentStatusEnum.valueOf(shipment.getStatus()), target, shipment.getLastEventOccurredAt(), occurredAt);
        if (decision != ShipmentStateMachine.TransitionDecision.APPLY) {
            throw exception(FULFILLMENT_INVALID_STATUS_TRANSITION);
        }
    }

    private void updateShipmentStatus(Long tenantId, ShipmentDO shipment, ShipmentStatusEnum status,
                                      LocalDateTime occurredAt) {
        int updated = shipmentMapper.updateStatusByIdAndVersion(tenantId, shipment.getId(), shipment.getVersion(),
                status.name(), occurredAt);
        if (updated != 1) {
            throw exception(FULFILLMENT_VERSION_CONFLICT);
        }
    }

    private void incrementShipmentVersion(Long tenantId, ShipmentDO shipment) {
        int updated = shipmentMapper.incrementVersionByIdAndVersion(tenantId, shipment.getId(), shipment.getVersion());
        if (updated != 1) {
            throw exception(FULFILLMENT_VERSION_CONFLICT);
        }
    }

    private static boolean isDispatchedStatus(String status) {
        try {
            return DISPATCHED_STATUSES.contains(ShipmentStatusEnum.valueOf(status));
        } catch (IllegalArgumentException invalidStatus) {
            return false;
        }
    }

    private static boolean isActiveLeg(ShipmentLegDO leg) {
        try {
            return !COMPLETED_LEG_STATUSES.contains(ShipmentStatusEnum.valueOf(leg.getStatus()));
        } catch (IllegalArgumentException | NullPointerException invalidStatus) {
            throw exception(FULFILLMENT_DISPATCH_INCOMPLETE);
        }
    }

    private static void validatePackageCommand(UpsertPackageCommand command) {
        if (command == null || command.getTenantId() == null || command.getShipmentId() == null) {
            throw exception(FULFILLMENT_SHIPMENT_NOT_FOUND);
        }
        validateExpectedVersion(command.getExpectedVersion());
        if (command.getPackageNo() == null || command.getPackageNo().isBlank()
                || command.getPackageType() == null || command.getPackageType().isBlank()) {
            throw exception(FULFILLMENT_DISPATCH_INCOMPLETE);
        }
        normalizeRequiredVocabulary(command.getPackageType(), PACKAGE_TYPES);
        String weightUnit = normalizeOptionalVocabulary(command.getWeightUnit(), WEIGHT_UNITS);
        String dimensionUnit = normalizeOptionalVocabulary(command.getDimensionUnit(), DIMENSION_UNITS);
        validateNonNegative(command.getWeight());
        validateNonNegative(command.getLength());
        validateNonNegative(command.getWidth());
        validateNonNegative(command.getHeight());
        if ((command.getWeight() == null) != (weightUnit == null)) {
            throw exception(FULFILLMENT_DISPATCH_INCOMPLETE);
        }
        boolean hasDimensions = command.getLength() != null || command.getWidth() != null || command.getHeight() != null;
        if (hasDimensions != (dimensionUnit != null)) {
            throw exception(FULFILLMENT_DISPATCH_INCOMPLETE);
        }
    }

    private static void validateLegCommand(AddShipmentLegCommand command) {
        if (command == null || command.getTenantId() == null || command.getShipmentId() == null) {
            throw exception(FULFILLMENT_SHIPMENT_NOT_FOUND);
        }
        validateExpectedVersion(command.getExpectedVersion());
        if (command.getSequenceNo() == null || command.getSequenceNo() <= 0
                || command.getLegType() == null || command.getLegType().isBlank()
                || command.getCarrierId() == null || command.getProviderId() == null) {
            throw exception(FULFILLMENT_DISPATCH_INCOMPLETE);
        }
        normalizeRequiredVocabulary(command.getLegType(), LEG_TYPES);
        FulfillmentPersistenceTextPolicy.location(command.getOriginLocation());
        FulfillmentPersistenceTextPolicy.location(command.getDestinationLocation());
    }

    private static void validateMutationIdentity(Long tenantId, Long shipmentId, Integer expectedVersion) {
        if (tenantId == null || shipmentId == null) {
            throw exception(FULFILLMENT_SHIPMENT_NOT_FOUND);
        }
        validateExpectedVersion(expectedVersion);
    }

    private static void validateExpectedVersion(Integer expectedVersion) {
        if (expectedVersion == null || expectedVersion < 0) {
            throw exception(FULFILLMENT_VERSION_CONFLICT);
        }
    }

    private static void validateNonNegative(BigDecimal value) {
        if (value != null && value.signum() < 0) {
            throw exception(FULFILLMENT_DISPATCH_INCOMPLETE);
        }
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeRequiredVocabulary(String value, Set<String> allowedValues) {
        String normalized = normalizeOptional(value);
        if (normalized == null || !allowedValues.contains(normalized.toUpperCase(Locale.ROOT))) {
            throw exception(FULFILLMENT_DISPATCH_INCOMPLETE);
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private static String normalizeOptionalVocabulary(String value, Set<String> allowedValues) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }
        String canonical = normalized.toUpperCase(Locale.ROOT);
        if (!allowedValues.contains(canonical)) {
            throw exception(FULFILLMENT_DISPATCH_INCOMPLETE);
        }
        return canonical;
    }

    private static boolean isTrackingConstraintViolation(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 12; depth++, current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(TRACKING_UNIQUE_CONSTRAINT)) {
                return true;
            }
            try {
                Object constraintName = current.getClass().getMethod("getConstraintName").invoke(current);
                if (constraintName != null && constraintName.toString().toLowerCase(Locale.ROOT)
                        .contains(TRACKING_UNIQUE_CONSTRAINT)) {
                    return true;
                }
            } catch (ReflectiveOperationException | SecurityException ignored) {
                // Most JDBC exceptions expose the constraint only in their message.
            }
        }
        return false;
    }

    private record DispatchAggregate(List<ShipmentItemDO> items, List<ShipmentPackageDO> packages,
                                     List<ShipmentLegDO> legs, List<PackageDispatchPlan> packagePlans,
                                     Map<Long, CarrierDO> carriersById,
                                     Map<Long, LogisticsProviderDO> providersById) {
    }

    private record PackageDispatchPlan(ShipmentPackageDO shipmentPackage, ShipmentLegDO activeLeg) {
    }

    private record LegacyProjection(Long legacyExpressId, String trackingNumber) {
    }

}
