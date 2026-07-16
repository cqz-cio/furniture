package cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Schema(description = "管理后台 - 履约聚合版本 Request VO")
@Data
public class ShipmentVersionReqVO {

    @NotNull @PositiveOrZero
    private Integer expectedVersion;
}
