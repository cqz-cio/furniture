package cn.iocoder.yudao.module.trade.service.fulfillment;

import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.TrackingStatusMappingDO;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.TrackingStatusMappingMapper;
import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum;
import cn.iocoder.yudao.module.trade.service.fulfillment.support.TrackingEventCanonicalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class VersionedTrackingStatusMapper {

    static final int UNKNOWN_IN_TRANSIT_PRIORITY = 30;

    private final TrackingStatusMappingMapper mappingMapper;

    public Resolution resolve(Long tenantId, String providerCode, String carrierCode, String rawStatus,
                              LocalDateTime receivedAtUtc, String replayMappingVersion) {
        String normalizedStatus = TrackingEventCanonicalizer.normalizeUpper(rawStatus);
        if (normalizedStatus == null) {
            throw new IllegalArgumentException("providerStatus is required");
        }
        TrackingStatusMappingDO mapping = replayMappingVersion == null
                ? mappingMapper.selectActive(tenantId, providerCode, carrierCode, normalizedStatus, receivedAtUtc)
                : mappingMapper.selectAtVersion(tenantId, providerCode, carrierCode, normalizedStatus,
                        replayMappingVersion);
        if (mapping == null) {
            if (replayMappingVersion != null) {
                throw new IllegalArgumentException("Requested tracking mapping version was not found");
            }
            return new Resolution(false, ShipmentStatusEnum.IN_TRANSIT, UNKNOWN_IN_TRANSIT_PRIORITY,
                    null, null, normalizedStatus);
        }
        if (mapping.getStatusPriority() == null || mapping.getStatusPriority() <= 0) {
            throw new IllegalArgumentException("Tracking status mapping priority must be positive");
        }
        ShipmentStatusEnum candidate;
        try {
            candidate = ShipmentStatusEnum.valueOf(mapping.getStandardStatus());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Tracking status mapping has an invalid standard status", ex);
        }
        return new Resolution(true, candidate, mapping.getStatusPriority(), mapping.getMappingVersion(),
                mapping.getEffectiveAt(), normalizedStatus);
    }

    public record Resolution(boolean known, ShipmentStatusEnum candidateStatus, int statusPriority,
                             String mappingVersion, LocalDateTime mappingEffectiveAt, String normalizedRawStatus) {
    }
}
