package cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo;

import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 履约运单创建 Request VO")
@Data
public class ShipmentCreateReqVO {

    @Schema(description = "订单编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull @Positive
    private Long orderId;

    @Schema(description = "运单类型", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private ShipmentTypeEnum shipmentType;

    @Schema(description = "始发国家，仅支持 US 或 CA", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Pattern(regexp = "US|CA") @Size(max = 2)
    private String originCountry;

    @Schema(description = "目的国家，仅支持 US 或 CA", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Pattern(regexp = "US|CA") @Size(max = 2)
    private String destinationCountry;

    @Schema(description = "始发地 IANA 时区", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Size(max = 64)
    private String originTimezone;

    @Schema(description = "目的地 IANA 时区", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Size(max = 64)
    private String destinationTimezone;

    @Schema(description = "仓库编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull @Positive
    private Long warehouseId;

    @Schema(description = "默认物流服务商编号")
    @Positive
    private Long providerId;

    @Schema(description = "运单商品", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty @Valid
    private List<ShipmentCreateItemReqVO> items;
}
