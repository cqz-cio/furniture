package cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 履约运单商品创建 Request VO")
@Data
public class ShipmentCreateItemReqVO {

    @NotNull @Positive
    private Long orderItemId;

    @NotNull @Positive
    private Long skuId;

    @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 18, fraction = 6)
    private BigDecimal quantity;
}
