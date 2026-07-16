package cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo;

import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum;
import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 北美履约发货单详情 Response VO")
@Data
public class ShipmentDetailRespVO {

    private Long id;
    private Long orderId;
    private Long warehouseId;
    private Long providerId;
    private String shipmentNo;
    private String originCountry;
    private String destinationCountry;
    private String originTimezone;
    private String destinationTimezone;
    private ShipmentTypeEnum shipmentType;
    private ShipmentStatusEnum status;
    private LocalDateTime estimatedDeliveryAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer version;
    private List<ShipmentItemRespVO> items;
    private List<ShipmentPackageRespVO> packages;
    private List<ShipmentLegRespVO> legs;

}
