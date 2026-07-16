package cn.iocoder.yudao.module.trade.service.fulfillment;

import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.CarrierDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.FulfillmentIdempotencyDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.FulfillmentOutboxEventDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.LogisticsProviderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.OrderFulfillmentSummaryDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentLegDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentPackageDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.TrackingEventDO;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.CarrierMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.FulfillmentIdempotencyMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.FulfillmentOutboxEventMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.LogisticsProviderMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.OrderFulfillmentSummaryMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentLegMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentPackageMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.TrackingEventMapper;
import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentProperties;
import cn.iocoder.yudao.module.trade.framework.fulfillment.core.dto.ProviderTrackingEvent;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.ApplyManualTrackingEventCommand;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.ApplyTrackingEventCommand;
import cn.iocoder.yudao.module.trade.service.fulfillment.domain.OrderFulfillmentSummaryCalculator;
import cn.iocoder.yudao.module.trade.service.fulfillment.domain.ShipmentStateMachine;
import cn.iocoder.yudao.module.trade.service.fulfillment.support.FulfillmentHashing;
import cn.iocoder.yudao.module.trade.service.fulfillment.support.TrackingEventCanonicalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_IDEMPOTENCY_CONFLICT;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_PROVIDER_NOT_AVAILABLE;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_SHIPMENT_NOT_FOUND;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_VERSION_CONFLICT;

@Service
@RequiredArgsConstructor
public class FulfillmentTrackingServiceImpl implements FulfillmentTrackingService {

    private static final int MAX_ATTEMPTS = 3;
    private static final String OUTBOX_PENDING = "PENDING";
    private static final String OPERATION_MANUAL_TRACKING = "MANUAL_TRACKING_EVENT";
    private static final String IDEMPOTENCY_PROCESSING = "PROCESSING";
    private static final String IDEMPOTENCY_COMPLETED = "COMPLETED";

    private final ShipmentMapper shipmentMapper;
    private final ShipmentPackageMapper packageMapper;
    private final ShipmentLegMapper legMapper;
    private final LogisticsProviderMapper providerMapper;
    private final CarrierMapper carrierMapper;
    private final TrackingEventMapper eventMapper;
    private final VersionedTrackingStatusMapper statusMapper;
    private final OrderFulfillmentSummaryMapper summaryMapper;
    private final FulfillmentOutboxEventMapper outboxMapper;
    private final FulfillmentIdempotencyMapper idempotencyMapper;
    private final FulfillmentProperties properties;
    private final PlatformTransactionManager transactionManager;

    private final ShipmentStateMachine stateMachine = new ShipmentStateMachine();
    private final OrderFulfillmentSummaryCalculator summaryCalculator = new OrderFulfillmentSummaryCalculator();

    @Override
    public TrackingApplyResult applyEvent(ApplyTrackingEventCommand command) {
        validateCommand(command);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return inNewTransaction(() -> applyOnce(command));
            } catch (DuplicateKeyException duplicate) {
                if (!isTrackingIdentityDuplicate(duplicate)) {
                    throw duplicate;
                }
                TrackingApplyResult existing = inNewTransaction(() -> loadDuplicateResult(command));
                if (existing != null) {
                    return existing;
                }
                throw duplicate;
            } catch (OptimisticTrackingConflictException conflict) {
                if (attempt == MAX_ATTEMPTS) {
                    throw exception(FULFILLMENT_VERSION_CONFLICT);
                }
            }
        }
        throw exception(FULFILLMENT_VERSION_CONFLICT);
    }

    @Override
    public TrackingApplyResult applyManualEvent(String idempotencyKey, ApplyManualTrackingEventCommand command) {
        ManualAudit audit = validateManualCommand(idempotencyKey, command);
        try {
            return inNewTransaction(() -> applyManualOnce(idempotencyKey, command, audit));
        } catch (OptimisticTrackingConflictException conflict) {
            throw exception(FULFILLMENT_VERSION_CONFLICT);
        }
    }

    private TrackingApplyResult applyManualOnce(String idempotencyKey, ApplyManualTrackingEventCommand command,
                                                 ManualAudit audit) {
        Long tenantId = command.getTenantId();
        ShipmentLegDO leg = legMapper.selectByIdAndTenantId(command.getShipmentLegId(), tenantId);
        if (leg == null || !command.getShipmentId().equals(leg.getShipmentId())) {
            throw exception(FULFILLMENT_SHIPMENT_NOT_FOUND);
        }
        ShipmentPackageDO shipmentPackage = resolveManualPackage(command, leg);
        if (leg.getProviderId() == null) {
            throw exception(FULFILLMENT_PROVIDER_NOT_AVAILABLE);
        }
        Instant occurredInstant = TrackingEventCanonicalizer.truncateToMicros(command.getOccurredAt());
        LocalDateTime occurredAt = LocalDateTime.ofInstant(occurredInstant, ZoneOffset.UTC);
        String keyHash = FulfillmentHashing.hmacSha256Hex(properties.getIdempotencyHmacKey(),
                OPERATION_MANUAL_TRACKING + ":" + idempotencyKey);
        String requestHash = FulfillmentHashing.sha256ManualTracking(tenantId, command.getShipmentId(),
                shipmentPackage == null ? null : shipmentPackage.getId(), leg.getId(),
                command.getRequestedStatus().name(), occurredInstant, command.getExpectedShipmentVersion(),
                command.getOperatorId(), audit.reason());
        LocalDateTime receivedAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        FulfillmentIdempotencyDO idempotency = new FulfillmentIdempotencyDO()
                .setTenantId(tenantId)
                .setOperation(OPERATION_MANUAL_TRACKING)
                .setIdempotencyKeyHash(keyHash)
                .setRequestHash(requestHash)
                .setResourceType("TRACKING_EVENT")
                .setStatus(IDEMPOTENCY_PROCESSING)
                .setExpiresAt(receivedAt.plusHours(24));
        try {
            idempotencyMapper.insert(idempotency);
        } catch (DuplicateKeyException duplicate) {
            return resolveManualDuplicate(tenantId, keyHash, requestHash);
        }

        ShipmentDO shipment = shipmentMapper.selectByIdForUpdate(tenantId, command.getShipmentId());
        if (shipment == null) {
            throw exception(FULFILLMENT_SHIPMENT_NOT_FOUND);
        }
        if (!command.getExpectedShipmentVersion().equals(shipment.getVersion())) {
            throw exception(FULFILLMENT_VERSION_CONFLICT);
        }
        int manualPriority = manualPriority(command.getRequestedStatus());
        VersionedTrackingStatusMapper.Resolution resolution = new VersionedTrackingStatusMapper.Resolution(false,
                command.getRequestedStatus(), manualPriority, null, null, "MANUAL");
        TrackingEventDO event = new TrackingEventDO()
                .setTenantId(tenantId)
                .setShipmentId(shipment.getId())
                .setPackageId(shipmentPackage == null ? null : shipmentPackage.getId())
                .setShipmentLegId(leg.getId())
                .setProviderId(leg.getProviderId())
                .setExternalEventId(keyHash)
                .setStandardStatus(command.getRequestedStatus().name())
                .setProviderStatus("MANUAL")
                .setProviderStatusNormalized("MANUAL")
                .setMappingKnown(false)
                .setTransitionDecision(ShipmentStateMachine.TransitionDecision.TIMELINE_ONLY.name())
                .setOccurredAt(occurredAt)
                .setReceivedAt(receivedAt)
                .setSource("MANUAL")
                .setManualOperatorId(command.getOperatorId())
                .setManualReason(audit.reason())
                .setRequestTraceId(audit.traceId());
        eventMapper.insert(event);

        String shipmentPreviousStatus = shipment.getStatus();
        int shipmentVersionBefore = shipment.getVersion();
        boolean packageWasCanceled = shipmentPackage != null
                && ShipmentStatusEnum.CANCELED.name().equals(shipmentPackage.getStatus());
        TransitionOutcome packageOutcome = shipmentPackage == null ? TransitionOutcome.noop(null)
                : applyPackageTransition(tenantId, shipmentPackage, resolution, occurredAt, event.getId());
        TransitionOutcome legOutcome = applyLegTransition(tenantId, leg, resolution, occurredAt, event.getId());
        TransitionOutcome shipmentDriver = shipmentPackage == null ? legOutcome
                : packageWasCanceled ? TransitionOutcome.noop(shipmentPackage.getStatus()) : packageOutcome;
        TransitionOutcome shipmentOutcome = applyShipmentTransition(tenantId, shipment, shipmentPackage,
                shipmentDriver, resolution, occurredAt, event.getId());
        if (shipment.getVersion() == shipmentVersionBefore) {
            if (shipmentMapper.incrementVersionByIdAndVersion(tenantId, shipment.getId(), shipmentVersionBefore) != 1) {
                throw new OptimisticTrackingConflictException();
            }
            shipment.setVersion(shipmentVersionBefore + 1);
        }
        boolean stateChanged = packageOutcome.stateChanged() || legOutcome.stateChanged()
                || shipmentOutcome.stateChanged();
        String shipmentResultStatus = shipmentOutcome.resultStatus() == null
                ? shipment.getStatus() : shipmentOutcome.resultStatus();
        event.setTransitionDecision(combinedDecision(packageOutcome, legOutcome, shipmentOutcome, stateChanged).name())
                .setPreviousStatus(shipmentPreviousStatus)
                .setResultStatus(shipmentResultStatus);
        if (eventMapper.updateById(event) != 1) {
            throw new OptimisticTrackingConflictException();
        }

        recalculateSummary(tenantId, shipment.getOrderId());
        insertOutbox(tenantId, shipment, shipmentPackage, leg, event, "TRACKING_UPDATED", receivedAt);
        String statusEventType = shipmentOutcome.stateChanged()
                ? statusEventType(ShipmentStatusEnum.valueOf(shipmentOutcome.resultStatus())) : null;
        if (statusEventType != null) {
            insertOutbox(tenantId, shipment, shipmentPackage, leg, event, statusEventType, receivedAt);
        }
        if (idempotencyMapper.completeProcessingById(tenantId, idempotency.getId(), requestHash, event.getId(),
                receivedAt.plusHours(24)) != 1) {
            throw exception(FULFILLMENT_IDEMPOTENCY_CONFLICT);
        }
        return new TrackingApplyResult(true, stateChanged, shipmentPreviousStatus, shipmentResultStatus);
    }

    private ShipmentPackageDO resolveManualPackage(ApplyManualTrackingEventCommand command, ShipmentLegDO leg) {
        if (leg.getPackageId() == null) {
            if (command.getPackageId() != null) {
                throw exception(FULFILLMENT_SHIPMENT_NOT_FOUND);
            }
            return null;
        }
        if (command.getPackageId() != null && !leg.getPackageId().equals(command.getPackageId())) {
            throw exception(FULFILLMENT_SHIPMENT_NOT_FOUND);
        }
        ShipmentPackageDO shipmentPackage = packageMapper.selectByIdAndTenantId(leg.getPackageId(),
                command.getTenantId());
        if (shipmentPackage == null || !command.getShipmentId().equals(shipmentPackage.getShipmentId())) {
            throw exception(FULFILLMENT_SHIPMENT_NOT_FOUND);
        }
        return shipmentPackage;
    }

    private TrackingApplyResult resolveManualDuplicate(Long tenantId, String keyHash, String requestHash) {
        FulfillmentIdempotencyDO existing = idempotencyMapper.selectByOperationAndKeyHash(tenantId,
                OPERATION_MANUAL_TRACKING, keyHash);
        if (existing == null || !FulfillmentHashing.constantTimeEquals(existing.getRequestHash(), requestHash)
                || !IDEMPOTENCY_COMPLETED.equals(existing.getStatus()) || existing.getResourceId() == null) {
            throw exception(FULFILLMENT_IDEMPOTENCY_CONFLICT);
        }
        TrackingEventDO event = eventMapper.selectByIdAndTenantId(tenantId, existing.getResourceId());
        if (event == null) {
            throw exception(FULFILLMENT_IDEMPOTENCY_CONFLICT);
        }
        return duplicateResult(event, event.getResultStatus());
    }

    private TrackingApplyResult applyOnce(ApplyTrackingEventCommand command) {
        Long tenantId = command.getTenantId();
        ShipmentDO shipment = shipmentMapper.selectByIdForUpdate(tenantId, command.getShipmentId());
        if (shipment == null) {
            throw exception(FULFILLMENT_SHIPMENT_NOT_FOUND);
        }
        ShipmentLegDO leg = resolveLeg(command, shipment);
        ShipmentPackageDO shipmentPackage = resolvePackage(command, shipment, leg);
        LogisticsProviderDO provider = providerMapper.selectByIdAndTenantId(command.getProviderId(), tenantId);
        if (provider == null || !Integer.valueOf(0).equals(provider.getStatus())) {
            throw exception(FULFILLMENT_PROVIDER_NOT_AVAILABLE);
        }
        if (leg != null && !provider.getId().equals(leg.getProviderId())) {
            throw exception(FULFILLMENT_PROVIDER_NOT_AVAILABLE);
        }
        if (leg == null && (shipment.getProviderId() == null
                || !provider.getId().equals(shipment.getProviderId()))) {
            throw exception(FULFILLMENT_PROVIDER_NOT_AVAILABLE);
        }
        CarrierAndTracking carrierAndTracking = resolveCarrierAndTracking(tenantId, shipmentPackage, leg);

        ProviderTrackingEvent providerEvent = command.getProviderEvent();
        Instant occurredInstant = TrackingEventCanonicalizer.truncateToMicros(providerEvent.occurredAt());
        Instant receivedInstant = TrackingEventCanonicalizer.truncateToMicros(command.getReceivedAt());
        LocalDateTime occurredAt = LocalDateTime.ofInstant(occurredInstant, ZoneOffset.UTC);
        LocalDateTime receivedAt = LocalDateTime.ofInstant(receivedInstant, ZoneOffset.UTC);
        String externalEventId = TrackingEventCanonicalizer.normalize(providerEvent.externalEventId());
        String eventHash = externalEventId == null
                ? TrackingEventCanonicalizer.stableHash(carrierAndTracking.carrierCode(),
                        carrierAndTracking.trackingNumber(), providerEvent.providerStatus(), occurredInstant,
                        providerEvent.location(), providerEvent.description())
                : null;

        TrackingEventDO preexisting = findExistingEvent(tenantId, provider.getId(), externalEventId, eventHash);
        if (preexisting != null) {
            return duplicateResult(preexisting, shipment.getStatus());
        }
        VersionedTrackingStatusMapper.Resolution resolution = statusMapper.resolve(tenantId, provider.getCode(),
                carrierAndTracking.carrierCode(), providerEvent.providerStatus(), receivedAt,
                TrackingEventCanonicalizer.normalize(command.getReplayMappingVersion()));

        TrackingEventDO event = new TrackingEventDO()
                .setTenantId(tenantId)
                .setShipmentId(shipment.getId())
                .setPackageId(shipmentPackage == null ? null : shipmentPackage.getId())
                .setShipmentLegId(leg == null ? null : leg.getId())
                .setProviderId(provider.getId())
                .setExternalEventId(externalEventId)
                .setEventHash(eventHash)
                .setStandardStatus(resolution.candidateStatus().name())
                .setProviderStatus(providerEvent.providerStatus())
                .setProviderStatusNormalized(resolution.normalizedRawStatus())
                .setMappingVersion(resolution.mappingVersion())
                .setMappingEffectiveAt(resolution.mappingEffectiveAt())
                .setMappingKnown(resolution.known())
                .setTransitionDecision(ShipmentStateMachine.TransitionDecision.TIMELINE_ONLY.name())
                .setDescription(TrackingEventCanonicalizer.normalize(providerEvent.description()))
                .setLocation(TrackingEventCanonicalizer.normalize(providerEvent.location()))
                .setOccurredAt(occurredAt)
                .setOccurredTimezone(TrackingEventCanonicalizer.normalize(providerEvent.occurredTimezone()))
                .setReceivedAt(receivedAt)
                .setRawPayloadRef(TrackingEventCanonicalizer.normalize(providerEvent.rawPayloadRef()))
                .setSource(command.getSource().name());
        eventMapper.insert(event);

        String shipmentPreviousStatus = shipment.getStatus();
        boolean packageWasCanceled = shipmentPackage != null
                && ShipmentStatusEnum.CANCELED.name().equals(shipmentPackage.getStatus());
        TransitionOutcome packageOutcome = shipmentPackage == null ? TransitionOutcome.noop(null)
                : applyPackageTransition(tenantId, shipmentPackage, resolution, occurredAt, event.getId());
        TransitionOutcome legOutcome = leg == null ? TransitionOutcome.noop(null) : applyLegTransition(tenantId,
                leg, resolution, occurredAt, event.getId());
        TransitionOutcome shipmentDriver = shipmentPackage == null ? legOutcome
                : packageWasCanceled ? TransitionOutcome.noop(shipmentPackage.getStatus()) : packageOutcome;
        TransitionOutcome shipmentOutcome = applyShipmentTransition(tenantId, shipment, shipmentPackage,
                shipmentDriver, resolution, occurredAt, event.getId());
        boolean stateChanged = packageOutcome.stateChanged() || legOutcome.stateChanged()
                || shipmentOutcome.stateChanged();
        ShipmentStateMachine.TransitionDecision eventDecision = combinedDecision(packageOutcome, legOutcome,
                shipmentOutcome, stateChanged);
        String shipmentResultStatus = shipmentOutcome.resultStatus() == null
                ? shipment.getStatus() : shipmentOutcome.resultStatus();
        event.setTransitionDecision(eventDecision.name())
                .setPreviousStatus(shipmentPreviousStatus)
                .setResultStatus(shipmentResultStatus);
        if (eventMapper.updateById(event) != 1) {
            throw new OptimisticTrackingConflictException();
        }

        recalculateSummary(tenantId, shipment.getOrderId());
        insertOutbox(tenantId, shipment, shipmentPackage, leg, event, "TRACKING_UPDATED", receivedAt);
        if (!resolution.known()) {
            insertOutbox(tenantId, shipment, shipmentPackage, leg, event,
                    "TRACKING_STATUS_MAPPING_UNKNOWN", receivedAt);
        }
        String statusEventType = shipmentOutcome.stateChanged()
                ? statusEventType(ShipmentStatusEnum.valueOf(shipmentOutcome.resultStatus())) : null;
        if (statusEventType != null) {
            insertOutbox(tenantId, shipment, shipmentPackage, leg, event, statusEventType, receivedAt);
        }
        return new TrackingApplyResult(true, stateChanged, shipmentPreviousStatus, shipmentResultStatus);
    }

    private ShipmentLegDO resolveLeg(ApplyTrackingEventCommand command, ShipmentDO shipment) {
        if (command.getShipmentLegId() == null) {
            return null;
        }
        ShipmentLegDO leg = legMapper.selectByIdAndTenantId(command.getShipmentLegId(), command.getTenantId());
        if (leg == null || !shipment.getId().equals(leg.getShipmentId())) {
            throw exception(FULFILLMENT_SHIPMENT_NOT_FOUND);
        }
        return leg;
    }

    private ShipmentPackageDO resolvePackage(ApplyTrackingEventCommand command, ShipmentDO shipment,
                                               ShipmentLegDO leg) {
        Long effectivePackageId;
        if (leg == null) {
            effectivePackageId = Objects.requireNonNull(command.getPackageId(), "packageId");
        } else if (leg.getPackageId() == null) {
            if (command.getPackageId() != null) {
                throw exception(FULFILLMENT_SHIPMENT_NOT_FOUND);
            }
            return null;
        } else {
            if (command.getPackageId() != null && !leg.getPackageId().equals(command.getPackageId())) {
                throw exception(FULFILLMENT_SHIPMENT_NOT_FOUND);
            }
            effectivePackageId = leg.getPackageId();
        }
        ShipmentPackageDO shipmentPackage = packageMapper.selectByIdAndTenantId(effectivePackageId,
                command.getTenantId());
        if (shipmentPackage == null || !shipment.getId().equals(shipmentPackage.getShipmentId())) {
            throw exception(FULFILLMENT_SHIPMENT_NOT_FOUND);
        }
        return shipmentPackage;
    }

    private CarrierAndTracking resolveCarrierAndTracking(Long tenantId, ShipmentPackageDO shipmentPackage,
                                                          ShipmentLegDO leg) {
        Long carrierId = leg == null ? shipmentPackage.getCarrierId() : leg.getCarrierId();
        CarrierDO carrier = carrierId == null ? null : carrierMapper.selectByIdAndTenantId(carrierId, tenantId);
        if (carrier == null || !Integer.valueOf(0).equals(carrier.getStatus())) {
            throw exception(FULFILLMENT_PROVIDER_NOT_AVAILABLE);
        }
        String trackingNumber = leg == null ? TrackingEventCanonicalizer.normalize(shipmentPackage.getTrackingNumber())
                : TrackingEventCanonicalizer.normalize(leg.getTrackingNumber());
        if (trackingNumber == null && leg != null && shipmentPackage != null
                && Objects.equals(leg.getCarrierId(), shipmentPackage.getCarrierId())) {
            trackingNumber = TrackingEventCanonicalizer.normalize(shipmentPackage.getTrackingNumber());
        }
        if (trackingNumber == null) {
            throw exception(FULFILLMENT_PROVIDER_NOT_AVAILABLE);
        }
        return new CarrierAndTracking(TrackingEventCanonicalizer.normalizeUpper(carrier.getCode()), trackingNumber);
    }

    private TransitionOutcome applyPackageTransition(Long tenantId, ShipmentPackageDO shipmentPackage,
                                                      VersionedTrackingStatusMapper.Resolution resolution,
                                                      LocalDateTime occurredAt, Long eventId) {
        ShipmentStatusEnum current = ShipmentStatusEnum.valueOf(shipmentPackage.getStatus());
        if (!isNewer(occurredAt, resolution.statusPriority(), eventId, shipmentPackage.getLastEventOccurredAt(),
                shipmentPackage.getLastEventStatusPriority(), shipmentPackage.getLastEventId())) {
            return TransitionOutcome.noop(current.name());
        }
        ShipmentStateMachine.TransitionDecision decision = stateMachine.decide(current,
                resolution.candidateStatus(), shipmentPackage.getLastEventOccurredAt(), occurredAt);
        boolean applyWatermark = decision == ShipmentStateMachine.TransitionDecision.APPLY
                || decision == ShipmentStateMachine.TransitionDecision.TIMELINE_ONLY
                && current == resolution.candidateStatus();
        String resultStatus = decision == ShipmentStateMachine.TransitionDecision.APPLY
                ? resolution.candidateStatus().name() : current.name();
        if (applyWatermark && packageMapper.updateTrackingStateByIdAndVersion(tenantId, shipmentPackage.getId(),
                shipmentPackage.getVersion(), resultStatus, occurredAt, resolution.statusPriority(), eventId) != 1) {
            throw new OptimisticTrackingConflictException();
        }
        if (applyWatermark) {
            shipmentPackage.setStatus(resultStatus).setLastEventOccurredAt(occurredAt)
                    .setLastEventStatusPriority(resolution.statusPriority()).setLastEventId(eventId)
                    .setVersion(shipmentPackage.getVersion() + 1);
        }
        return new TransitionOutcome(decision, decision == ShipmentStateMachine.TransitionDecision.APPLY,
                resultStatus, applyWatermark);
    }

    private TransitionOutcome applyLegTransition(Long tenantId, ShipmentLegDO leg,
                                                  VersionedTrackingStatusMapper.Resolution resolution,
                                                  LocalDateTime occurredAt, Long eventId) {
        ShipmentStatusEnum current = ShipmentStatusEnum.valueOf(leg.getStatus());
        if (!isNewer(occurredAt, resolution.statusPriority(), eventId, leg.getLastEventOccurredAt(),
                leg.getLastEventStatusPriority(), leg.getLastEventId())) {
            return TransitionOutcome.noop(current.name());
        }
        ShipmentStateMachine.TransitionDecision decision = stateMachine.decide(current,
                resolution.candidateStatus(), leg.getLastEventOccurredAt(), occurredAt);
        boolean applyWatermark = decision == ShipmentStateMachine.TransitionDecision.APPLY
                || decision == ShipmentStateMachine.TransitionDecision.TIMELINE_ONLY
                && current == resolution.candidateStatus();
        String resultStatus = decision == ShipmentStateMachine.TransitionDecision.APPLY
                ? resolution.candidateStatus().name() : current.name();
        if (applyWatermark && legMapper.updateTrackingStateByIdAndVersion(tenantId, leg.getId(), leg.getVersion(),
                resultStatus, occurredAt, resolution.statusPriority(), eventId) != 1) {
            throw new OptimisticTrackingConflictException();
        }
        if (applyWatermark) {
            leg.setStatus(resultStatus).setLastEventOccurredAt(occurredAt)
                    .setLastEventStatusPriority(resolution.statusPriority()).setLastEventId(eventId)
                    .setVersion(leg.getVersion() + 1);
        }
        return new TransitionOutcome(decision, decision == ShipmentStateMachine.TransitionDecision.APPLY,
                resultStatus, applyWatermark);
    }

    private TransitionOutcome applyShipmentTransition(Long tenantId, ShipmentDO shipment,
                                                       ShipmentPackageDO targetPackage, TransitionOutcome driver,
                                                       VersionedTrackingStatusMapper.Resolution resolution,
                                                       LocalDateTime occurredAt, Long eventId) {
        if (!driver.effective()) {
            return TransitionOutcome.noop(shipment.getStatus());
        }
        if (!isNewer(occurredAt, resolution.statusPriority(), eventId, shipment.getLastEventOccurredAt(),
                shipment.getLastEventStatusPriority(), shipment.getLastEventId())) {
            return TransitionOutcome.noop(shipment.getStatus());
        }
        ShipmentStatusEnum current = ShipmentStatusEnum.valueOf(shipment.getStatus());
        ShipmentStatusEnum aggregateCandidate;
        if (targetPackage == null) {
            aggregateCandidate = ShipmentStatusEnum.valueOf(driver.resultStatus());
        } else {
            List<ShipmentPackageDO> packages = packageMapper.selectListByShipmentId(tenantId, shipment.getId());
            List<ShipmentStatusEnum> activeStatuses = new ArrayList<>();
            for (ShipmentPackageDO candidate : packages == null ? List.<ShipmentPackageDO>of() : packages) {
                String status = candidate.getStatus();
                if (!ShipmentStatusEnum.CANCELED.name().equals(status)) {
                    activeStatuses.add(ShipmentStatusEnum.valueOf(status));
                }
            }
            aggregateCandidate = aggregateShipmentCandidate(activeStatuses,
                    ShipmentStatusEnum.valueOf(driver.resultStatus()), current);
        }
        ShipmentStateMachine.TransitionDecision decision = stateMachine.decide(current, aggregateCandidate,
                shipment.getLastEventOccurredAt(), occurredAt);
        boolean applyWatermark = decision == ShipmentStateMachine.TransitionDecision.APPLY
                || decision == ShipmentStateMachine.TransitionDecision.TIMELINE_ONLY && current == aggregateCandidate;
        String resultStatus = decision == ShipmentStateMachine.TransitionDecision.APPLY
                ? aggregateCandidate.name() : current.name();
        LocalDateTime deliveredAt = aggregateCandidate == ShipmentStatusEnum.DELIVERED
                && decision == ShipmentStateMachine.TransitionDecision.APPLY ? occurredAt : null;
        if (applyWatermark && shipmentMapper.updateTrackingStateByIdAndVersion(tenantId, shipment.getId(),
                shipment.getVersion(), resultStatus, occurredAt, resolution.statusPriority(), eventId, deliveredAt) != 1) {
            throw new OptimisticTrackingConflictException();
        }
        if (applyWatermark) {
            shipment.setStatus(resultStatus).setLastEventOccurredAt(occurredAt)
                    .setLastEventStatusPriority(resolution.statusPriority()).setLastEventId(eventId)
                    .setVersion(shipment.getVersion() + 1);
            if (deliveredAt != null) {
                shipment.setDeliveredAt(deliveredAt);
            }
        }
        return new TransitionOutcome(decision, decision == ShipmentStateMachine.TransitionDecision.APPLY,
                resultStatus, applyWatermark);
    }

    private ShipmentStatusEnum aggregateShipmentCandidate(List<ShipmentStatusEnum> statuses,
                                                            ShipmentStatusEnum effectiveTargetStatus,
                                                            ShipmentStatusEnum current) {
        if (statuses.isEmpty() && effectiveTargetStatus == ShipmentStatusEnum.CANCELED) {
            return ShipmentStatusEnum.CANCELED;
        }
        if (!statuses.isEmpty() && statuses.stream().allMatch(status -> status == ShipmentStatusEnum.DELIVERED)) {
            return ShipmentStatusEnum.DELIVERED;
        }
        if (!statuses.isEmpty() && statuses.stream().allMatch(status -> status == ShipmentStatusEnum.RETURNED)) {
            return ShipmentStatusEnum.RETURNED;
        }
        if (statuses.stream().anyMatch(status -> status == ShipmentStatusEnum.RETURNING
                || status == ShipmentStatusEnum.RETURNED)) {
            return ShipmentStatusEnum.RETURNING;
        }
        if (statuses.stream().anyMatch(status -> status == ShipmentStatusEnum.DELIVERY_EXCEPTION)) {
            return ShipmentStatusEnum.DELIVERY_EXCEPTION;
        }
        if (effectiveTargetStatus == ShipmentStatusEnum.DELIVERED
                || effectiveTargetStatus == ShipmentStatusEnum.RETURNED
                || effectiveTargetStatus == ShipmentStatusEnum.RETURNING
                || effectiveTargetStatus == ShipmentStatusEnum.CANCELED) {
            return current;
        }
        return effectiveTargetStatus;
    }

    private void recalculateSummary(Long tenantId, Long orderId) {
        List<ShipmentDO> shipments = shipmentMapper.selectListByOrderId(tenantId, orderId);
        OrderFulfillmentSummaryCalculator.Calculation calculated = summaryCalculator.calculate(shipments);
        OrderFulfillmentSummaryDO summary = summaryMapper.selectByOrderId(tenantId, orderId);
        if (summary == null) {
            throw new OptimisticTrackingConflictException();
        }
        if (calculated.status().name().equals(summary.getStatus())
                && calculated.shipmentCount() == summary.getShipmentCount()
                && calculated.deliveredShipmentCount() == summary.getDeliveredShipmentCount()) {
            return;
        }
        int updated = summaryMapper.updateCountsAndStatusByIdAndVersion(tenantId, summary.getId(),
                summary.getVersion(), calculated.status().name(), calculated.shipmentCount(),
                calculated.deliveredShipmentCount());
        if (updated != 1) {
            throw new OptimisticTrackingConflictException();
        }
    }

    private void insertOutbox(Long tenantId, ShipmentDO shipment, ShipmentPackageDO shipmentPackage,
                              ShipmentLegDO leg, TrackingEventDO event, String eventType,
                              LocalDateTime receivedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", tenantId);
        payload.put("orderId", shipment.getOrderId());
        payload.put("shipmentId", shipment.getId());
        if (shipmentPackage != null) {
            payload.put("packageId", shipmentPackage.getId());
        }
        if (leg != null) {
            payload.put("shipmentLegId", leg.getId());
        }
        payload.put("providerId", event.getProviderId());
        payload.put("trackingEventId", event.getId());
        payload.put("status", event.getResultStatus());
        outboxMapper.insert(new FulfillmentOutboxEventDO()
                .setTenantId(tenantId)
                .setEventId(UUID.randomUUID().toString())
                .setAggregateType("SHIPMENT")
                .setAggregateId(shipment.getId())
                .setEventType(eventType)
                .setPayload(payload)
                .setStatus(OUTBOX_PENDING)
                .setAttemptCount(0)
                .setNextAttemptAt(receivedAt));
    }

    private TrackingApplyResult loadDuplicateResult(ApplyTrackingEventCommand command) {
        ProviderTrackingEvent providerEvent = command.getProviderEvent();
        String externalId = TrackingEventCanonicalizer.normalize(providerEvent.externalEventId());
        ShipmentLegDO leg = command.getShipmentLegId() == null ? null
                : legMapper.selectByIdAndTenantId(command.getShipmentLegId(), command.getTenantId());
        Long effectivePackageId = leg != null && leg.getPackageId() != null ? leg.getPackageId() : command.getPackageId();
        ShipmentPackageDO shipmentPackage = effectivePackageId == null ? null
                : packageMapper.selectByIdAndTenantId(effectivePackageId, command.getTenantId());
        CarrierAndTracking facts = leg == null && shipmentPackage == null ? null
                : resolveCarrierAndTracking(command.getTenantId(), shipmentPackage, leg);
        String hash = externalId != null || facts == null ? null
                : TrackingEventCanonicalizer.stableHash(facts.carrierCode(), facts.trackingNumber(),
                        providerEvent.providerStatus(), providerEvent.occurredAt(), providerEvent.location(),
                        providerEvent.description());
        TrackingEventDO existing = findExistingEvent(command.getTenantId(), command.getProviderId(), externalId, hash);
        if (existing == null) {
            return null;
        }
        ShipmentDO shipment = shipmentMapper.selectByIdForUpdate(command.getTenantId(), command.getShipmentId());
        return duplicateResult(existing, shipment == null ? existing.getResultStatus() : shipment.getStatus());
    }

    private TrackingEventDO findExistingEvent(Long tenantId, Long providerId, String externalId, String eventHash) {
        TrackingEventDO existing = externalId == null ? null
                : eventMapper.selectByExternalEventId(tenantId, providerId, externalId);
        return existing != null || eventHash == null ? existing
                : eventMapper.selectByEventHash(tenantId, providerId, eventHash);
    }

    private TrackingApplyResult duplicateResult(TrackingEventDO existing, String fallbackStatus) {
        String current = existing.getResultStatus() == null ? fallbackStatus : existing.getResultStatus();
        String previous = existing.getPreviousStatus() == null ? current : existing.getPreviousStatus();
        return new TrackingApplyResult(false, false, previous, current);
    }

    private ShipmentStateMachine.TransitionDecision combinedDecision(TransitionOutcome packageOutcome,
                                                                      TransitionOutcome legOutcome,
                                                                      TransitionOutcome shipmentOutcome,
                                                                      boolean stateChanged) {
        if (stateChanged) {
            return ShipmentStateMachine.TransitionDecision.APPLY;
        }
        if (packageOutcome.decision() == ShipmentStateMachine.TransitionDecision.REJECT
                || legOutcome.decision() == ShipmentStateMachine.TransitionDecision.REJECT
                || shipmentOutcome.decision() == ShipmentStateMachine.TransitionDecision.REJECT) {
            return ShipmentStateMachine.TransitionDecision.REJECT;
        }
        return ShipmentStateMachine.TransitionDecision.TIMELINE_ONLY;
    }

    private static boolean isNewer(LocalDateTime incomingAt, int incomingPriority, Long incomingId,
                                   LocalDateTime currentAt, Integer currentPriority, Long currentId) {
        if (currentAt == null) {
            return true;
        }
        int time = incomingAt.compareTo(currentAt);
        if (time != 0) {
            return time > 0;
        }
        int priority = Integer.compare(incomingPriority, currentPriority == null ? Integer.MIN_VALUE : currentPriority);
        if (priority != 0) {
            return priority > 0;
        }
        return incomingId != null && (currentId == null || incomingId > currentId);
    }

    private static boolean isTrackingIdentityDuplicate(DuplicateKeyException duplicate) {
        Throwable cause = duplicate.getMostSpecificCause();
        String message = cause == null ? duplicate.getMessage() : cause.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toUpperCase(java.util.Locale.ROOT);
        return normalized.contains("UK_TRACKING_EVENT_EXTERNAL")
                || normalized.contains("UK_TRACKING_EVENT_HASH");
    }

    private static String statusEventType(ShipmentStatusEnum status) {
        return switch (status) {
            case DELIVERY_EXCEPTION -> "DELIVERY_EXCEPTION";
            case OUT_FOR_DELIVERY -> "OUT_FOR_DELIVERY";
            case DELIVERED -> "DELIVERED";
            case RETURNING -> "RETURN_STARTED";
            case RETURNED -> "RETURNED";
            default -> null;
        };
    }

    private <T> T inNewTransaction(Supplier<T> action) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> action.get());
    }

    private static void validateCommand(ApplyTrackingEventCommand command) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.getTenantId(), "tenantId");
        Objects.requireNonNull(command.getShipmentId(), "shipmentId");
        Objects.requireNonNull(command.getProviderId(), "providerId");
        Objects.requireNonNull(command.getProviderEvent(), "providerEvent");
        Objects.requireNonNull(command.getProviderEvent().occurredAt(), "occurredAt");
        Objects.requireNonNull(command.getReceivedAt(), "receivedAt");
        Objects.requireNonNull(command.getSource(), "source");
    }

    private static ManualAudit validateManualCommand(String idempotencyKey,
                                                      ApplyManualTrackingEventCommand command) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.getTenantId(), "tenantId");
        Objects.requireNonNull(command.getShipmentId(), "shipmentId");
        Objects.requireNonNull(command.getShipmentLegId(), "shipmentLegId");
        Objects.requireNonNull(command.getRequestedStatus(), "requestedStatus");
        Objects.requireNonNull(command.getOccurredAt(), "occurredAt");
        Objects.requireNonNull(command.getExpectedShipmentVersion(), "expectedShipmentVersion");
        Objects.requireNonNull(command.getOperatorId(), "operatorId");
        if (command.getExpectedShipmentVersion() < 0) {
            throw new IllegalArgumentException("expectedShipmentVersion must not be negative");
        }
        String traceId = command.getRequestTraceId() == null ? "" : command.getRequestTraceId().strip();
        if (traceId.isBlank() || codePointLength(traceId) > 64) {
            throw new IllegalArgumentException("requestTraceId must contain 1-64 characters");
        }
        String reason = command.getReason() == null ? "" : command.getReason().strip();
        int reasonLength = codePointLength(reason);
        if (reason.isBlank() || reasonLength < 5 || reasonLength > 500) {
            throw new IllegalArgumentException("reason must contain 5-500 characters");
        }
        return new ManualAudit(reason, traceId);
    }

    static int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    private static int manualPriority(ShipmentStatusEnum status) {
        return switch (status) {
            case DRAFT -> 1;
            case READY_TO_SHIP -> 10;
            case HANDED_TO_CARRIER -> 20;
            case IN_TRANSIT -> 30;
            case AT_LOCAL_TERMINAL -> 40;
            case APPOINTMENT_REQUIRED -> 45;
            case APPOINTMENT_CONFIRMED -> 50;
            case DELIVERY_EXCEPTION -> 60;
            case OUT_FOR_DELIVERY -> 80;
            case DELIVERED -> 90;
            case RETURNING -> 95;
            case RETURNED -> 100;
            case CANCELED -> 110;
        };
    }

    private record CarrierAndTracking(String carrierCode, String trackingNumber) {
        @Override
        public String toString() {
            return "CarrierAndTracking[carrierCode=" + carrierCode + ", trackingNumber=REDACTED]";
        }
    }

    private record ManualAudit(String reason, String traceId) {
    }

    private record TransitionOutcome(ShipmentStateMachine.TransitionDecision decision, boolean stateChanged,
                                     String resultStatus, boolean effective) {
        private static TransitionOutcome noop(String status) {
            return new TransitionOutcome(ShipmentStateMachine.TransitionDecision.TIMELINE_ONLY, false, status,
                    false);
        }
    }

    private static final class OptimisticTrackingConflictException extends RuntimeException {
    }
}
