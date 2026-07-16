package cn.iocoder.yudao.module.trade.service.fulfillment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo.ShipmentDetailRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo.ShipmentItemRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo.ShipmentLegRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo.ShipmentPackageRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo.ShipmentPageItemRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo.ShipmentPageReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo.TrackingEventRespVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentItemDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentLegDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentPackageDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.TrackingEventDO;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentItemMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentLegMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.ShipmentPackageMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.TrackingEventMapper;
import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum;
import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentTypeEnum;
import cn.iocoder.yudao.module.trade.framework.fulfillment.config.FulfillmentFeatureGuard;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.FULFILLMENT_SHIPMENT_NOT_FOUND;

@Service
public class FulfillmentQueryServiceImpl implements FulfillmentQueryService {

    @Resource
    private ShipmentMapper shipmentMapper;
    @Resource
    private ShipmentItemMapper itemMapper;
    @Resource
    private ShipmentPackageMapper packageMapper;
    @Resource
    private ShipmentLegMapper legMapper;
    @Resource
    private TrackingEventMapper trackingEventMapper;
    @Resource
    private FulfillmentFeatureGuard featureGuard;

    @Override
    public ShipmentDetailRespVO getShipment(Long tenantId, Long shipmentId) {
        featureGuard.requireReadEnabled();
        ShipmentDO shipment = requireShipment(tenantId, shipmentId);
        ShipmentDetailRespVO response = mapDetail(shipment);
        response.setItems(itemMapper.selectListByShipmentId(tenantId, shipmentId)
                .stream().map(this::mapItem).toList());
        response.setPackages(packageMapper.selectListByShipmentId(tenantId, shipmentId)
                .stream().map(this::mapPackage).toList());
        response.setLegs(legMapper.selectListByShipmentId(tenantId, shipmentId)
                .stream().map(this::mapLeg).toList());
        return response;
    }

    @Override
    public List<TrackingEventRespVO> getTimeline(Long tenantId, Long shipmentId) {
        featureGuard.requireReadEnabled();
        requireShipment(tenantId, shipmentId);
        return trackingEventMapper.selectListByShipmentId(tenantId, shipmentId)
                .stream().map(this::mapTrackingEvent).toList();
    }

    @Override
    public PageResult<ShipmentPageItemRespVO> getShipmentPage(Long tenantId, ShipmentPageReqVO reqVO) {
        featureGuard.requireReadEnabled();
        PageResult<ShipmentDO> page = shipmentMapper.selectPage(tenantId, reqVO);
        return new PageResult<>(page.getList().stream().map(this::mapPageItem).toList(), page.getTotal());
    }

    private ShipmentDO requireShipment(Long tenantId, Long shipmentId) {
        ShipmentDO shipment = shipmentMapper.selectByIdAndTenantId(shipmentId, tenantId);
        if (shipment == null) {
            throw exception(FULFILLMENT_SHIPMENT_NOT_FOUND);
        }
        return shipment;
    }

    static String maskIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return "***" + value.substring(Math.max(0, value.length() - 4));
    }

    private ShipmentPageItemRespVO mapPageItem(ShipmentDO shipment) {
        ShipmentPageItemRespVO response = new ShipmentPageItemRespVO();
        mapShipment(shipment, response);
        return response;
    }

    private ShipmentDetailRespVO mapDetail(ShipmentDO shipment) {
        ShipmentDetailRespVO response = new ShipmentDetailRespVO();
        response.setId(shipment.getId());
        response.setOrderId(shipment.getOrderId());
        response.setWarehouseId(shipment.getWarehouseId());
        response.setProviderId(shipment.getProviderId());
        response.setShipmentNo(shipment.getShipmentNo());
        response.setOriginCountry(shipment.getOriginCountry());
        response.setDestinationCountry(shipment.getDestinationCountry());
        response.setOriginTimezone(shipment.getOriginTimezone());
        response.setDestinationTimezone(shipment.getDestinationTimezone());
        response.setShipmentType(ShipmentTypeEnum.valueOf(shipment.getShipmentType()));
        response.setStatus(ShipmentStatusEnum.valueOf(shipment.getStatus()));
        response.setEstimatedDeliveryAt(shipment.getEstimatedDeliveryAt());
        response.setDeliveredAt(shipment.getDeliveredAt());
        response.setCreateTime(shipment.getCreateTime());
        response.setUpdateTime(shipment.getUpdateTime());
        response.setVersion(shipment.getVersion());
        return response;
    }

    private void mapShipment(ShipmentDO shipment, ShipmentPageItemRespVO response) {
        response.setId(shipment.getId());
        response.setOrderId(shipment.getOrderId());
        response.setWarehouseId(shipment.getWarehouseId());
        response.setProviderId(shipment.getProviderId());
        response.setShipmentNo(shipment.getShipmentNo());
        response.setOriginCountry(shipment.getOriginCountry());
        response.setDestinationCountry(shipment.getDestinationCountry());
        response.setOriginTimezone(shipment.getOriginTimezone());
        response.setDestinationTimezone(shipment.getDestinationTimezone());
        response.setShipmentType(ShipmentTypeEnum.valueOf(shipment.getShipmentType()));
        response.setStatus(ShipmentStatusEnum.valueOf(shipment.getStatus()));
        response.setEstimatedDeliveryAt(shipment.getEstimatedDeliveryAt());
        response.setDeliveredAt(shipment.getDeliveredAt());
        response.setCreateTime(shipment.getCreateTime());
        response.setUpdateTime(shipment.getUpdateTime());
        response.setVersion(shipment.getVersion());
    }

    private ShipmentItemRespVO mapItem(ShipmentItemDO item) {
        ShipmentItemRespVO response = new ShipmentItemRespVO();
        response.setId(item.getId());
        response.setOrderItemId(item.getOrderItemId());
        response.setSkuId(item.getSkuId());
        response.setQuantity(item.getQuantity());
        return response;
    }

    private ShipmentPackageRespVO mapPackage(ShipmentPackageDO shipmentPackage) {
        ShipmentPackageRespVO response = new ShipmentPackageRespVO();
        response.setId(shipmentPackage.getId());
        response.setCarrierId(shipmentPackage.getCarrierId());
        response.setPackageNo(shipmentPackage.getPackageNo());
        response.setPackageType(shipmentPackage.getPackageType());
        response.setTrackingNumberMasked(maskIdentifier(shipmentPackage.getTrackingNumber()));
        response.setWeightUnit(shipmentPackage.getWeightUnit());
        response.setDimensionUnit(shipmentPackage.getDimensionUnit());
        response.setWeight(shipmentPackage.getWeight());
        response.setLength(shipmentPackage.getLength());
        response.setWidth(shipmentPackage.getWidth());
        response.setHeight(shipmentPackage.getHeight());
        response.setStatus(ShipmentStatusEnum.valueOf(shipmentPackage.getStatus()));
        response.setVersion(shipmentPackage.getVersion());
        return response;
    }

    private ShipmentLegRespVO mapLeg(ShipmentLegDO leg) {
        ShipmentLegRespVO response = new ShipmentLegRespVO();
        response.setId(leg.getId());
        response.setPackageId(leg.getPackageId());
        response.setCarrierId(leg.getCarrierId());
        response.setProviderId(leg.getProviderId());
        response.setSequenceNo(leg.getSequenceNo());
        response.setVersion(leg.getVersion());
        response.setLegType(leg.getLegType());
        response.setServiceLevel(leg.getServiceLevel());
        response.setTrackingNumberMasked(maskIdentifier(leg.getTrackingNumber()));
        response.setProNumberMasked(maskIdentifier(leg.getProNumber()));
        response.setBolNumberMasked(maskIdentifier(leg.getBolNumber()));
        response.setStatus(ShipmentStatusEnum.valueOf(leg.getStatus()));
        response.setStartedAt(leg.getStartedAt());
        response.setCompletedAt(leg.getCompletedAt());
        return response;
    }

    private TrackingEventRespVO mapTrackingEvent(TrackingEventDO event) {
        TrackingEventRespVO response = new TrackingEventRespVO();
        response.setId(event.getId());
        response.setPackageId(event.getPackageId());
        response.setShipmentLegId(event.getShipmentLegId());
        response.setStandardStatus(ShipmentStatusEnum.valueOf(event.getStandardStatus()));
        response.setProviderStatusNormalized(event.getProviderStatusNormalized());
        response.setMappingVersion(event.getMappingVersion());
        response.setTransitionDecision(event.getTransitionDecision());
        response.setPreviousStatus(event.getPreviousStatus());
        response.setResultStatus(event.getResultStatus());
        response.setOccurredTimezone(event.getOccurredTimezone());
        response.setSource(event.getSource());
        response.setMappingKnown(event.getMappingKnown());
        response.setOccurredAt(event.getOccurredAt());
        response.setReceivedAt(event.getReceivedAt());
        return response;
    }

}
