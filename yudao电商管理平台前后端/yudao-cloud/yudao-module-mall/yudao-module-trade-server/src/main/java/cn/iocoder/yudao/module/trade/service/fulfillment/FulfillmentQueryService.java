package cn.iocoder.yudao.module.trade.service.fulfillment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo.ShipmentDetailRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo.ShipmentPageItemRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo.ShipmentPageReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo.TrackingEventRespVO;

import java.util.List;

public interface FulfillmentQueryService {

    ShipmentDetailRespVO getShipment(Long tenantId, Long shipmentId);

    List<TrackingEventRespVO> getTimeline(Long tenantId, Long shipmentId);

    PageResult<ShipmentPageItemRespVO> getShipmentPage(Long tenantId, ShipmentPageReqVO reqVO);

}
