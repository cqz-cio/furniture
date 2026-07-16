package cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo;

import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum;
import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 北美履约发货单分页项 Response VO")
@Data
public class ShipmentPageItemRespVO {

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

}
