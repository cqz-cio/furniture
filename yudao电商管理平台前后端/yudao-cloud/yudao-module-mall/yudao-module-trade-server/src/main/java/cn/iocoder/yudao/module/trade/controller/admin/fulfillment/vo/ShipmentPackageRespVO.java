package cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo;

import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 发货包裹 Response VO")
@Data
public class ShipmentPackageRespVO {

    private Long id;
    private Long carrierId;
    private String packageNo;
    private String packageType;
    private String trackingNumberMasked;
    private String weightUnit;
    private String dimensionUnit;
    private BigDecimal weight;
    private BigDecimal length;
    private BigDecimal width;
    private BigDecimal height;
    private ShipmentStatusEnum status;
    private Integer version;

}
