package cn.iocoder.yudao.module.trade.service.fulfillment;

import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentLegDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentPackageDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.TrackingEventDO;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentLegMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentPackageMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.TrackingEventMapper;
import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum;
import cn.iocoder.yudao.module.trade.framework.delivery.core.client.dto.ExpressTrackRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class FulfillmentLegacyProjectionServiceImpl implements FulfillmentLegacyProjectionService {

    private static final String CANCELED = ShipmentStatusEnum.CANCELED.name();

    private final ShipmentMapper shipmentMapper;
    private final ShipmentPackageMapper packageMapper;
    private final ShipmentLegMapper legMapper;
    private final TrackingEventMapper eventMapper;

    @Override
    public FulfillmentLegacyProjectionResult project(Long tenantId, Long orderId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(orderId, "orderId");
        List<ShipmentDO> shipments = new ArrayList<>(shipmentMapper.selectListByOrderId(tenantId, orderId));
        shipments.sort(Comparator.comparing(ShipmentDO::getId));
        for (ShipmentDO shipment : shipments) {
            if (CANCELED.equals(shipment.getStatus())) {
                continue;
            }
            // The legacy endpoint represents one delivery stream. Once the first active shipment is
            // selected, never blend in a later sibling shipment when that subject has no events.
            return projectShipment(tenantId, shipment);
        }
        return FulfillmentLegacyProjectionResult.fallback();
    }

    private FulfillmentLegacyProjectionResult projectShipment(Long tenantId, ShipmentDO shipment) {
        List<ShipmentPackageDO> packages = new ArrayList<>(
                packageMapper.selectListByShipmentId(tenantId, shipment.getId()));
        packages.sort(Comparator.comparing(ShipmentPackageDO::getId));
        for (ShipmentPackageDO shipmentPackage : packages) {
            if (CANCELED.equals(shipmentPackage.getStatus())) {
                continue;
            }
            List<TrackingEventDO> events = eventMapper.selectLegacySubjectEvents(
                    tenantId, shipment.getId(), shipmentPackage.getId(), null);
            if (!events.isEmpty()) {
                return projectEvents(events);
            }
        }

        List<ShipmentLegDO> legs = new ArrayList<>(legMapper.selectListByShipmentId(tenantId, shipment.getId()));
        legs.sort(Comparator.comparing(ShipmentLegDO::getSequenceNo)
                .thenComparing(ShipmentLegDO::getId));
        for (ShipmentLegDO leg : legs) {
            if (leg.getPackageId() != null || CANCELED.equals(leg.getStatus())) {
                continue;
            }
            List<TrackingEventDO> events = eventMapper.selectLegacySubjectEvents(
                    tenantId, shipment.getId(), null, leg.getId());
            if (!events.isEmpty()) {
                return projectEvents(events);
            }
        }
        return FulfillmentLegacyProjectionResult.fallback();
    }

    private static FulfillmentLegacyProjectionResult projectEvents(List<TrackingEventDO> events) {
        List<ExpressTrackRespDTO> projected = events.stream()
                .filter(event -> event.getOccurredAt() != null)
                .filter(event -> isSafeStatus(event.getStandardStatus()))
                .sorted(Comparator.comparing(TrackingEventDO::getOccurredAt)
                        .thenComparing(TrackingEventDO::getId))
                .map(event -> {
                    ExpressTrackRespDTO response = new ExpressTrackRespDTO();
                    response.setTime(event.getOccurredAt());
                    response.setContent(ShipmentStatusEnum.valueOf(event.getStandardStatus()).name());
                    return response;
                })
                .toList();
        return FulfillmentLegacyProjectionResult.authoritative(projected);
    }

    private static boolean isSafeStatus(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        try {
            ShipmentStatusEnum.valueOf(status);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

}
