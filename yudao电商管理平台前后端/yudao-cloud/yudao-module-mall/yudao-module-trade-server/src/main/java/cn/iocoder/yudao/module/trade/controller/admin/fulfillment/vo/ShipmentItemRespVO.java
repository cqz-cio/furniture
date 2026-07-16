package cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 发货单商品 Response VO")
@Data
public class ShipmentItemRespVO {

    private Long id;
    private Long orderItemId;
    private Long skuId;
    private BigDecimal quantity;

}
