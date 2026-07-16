package cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo;

import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 发货运输段 Response VO")
@Data
public class ShipmentLegRespVO {

    private Long id;
    private Long packageId;
    private Long carrierId;
    private Long providerId;
    private Integer sequenceNo;
    private Integer version;
    private String legType;
    private String serviceLevel;
    private String trackingNumberMasked;
    private String proNumberMasked;
    private String bolNumberMasked;
    private ShipmentStatusEnum status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

}
