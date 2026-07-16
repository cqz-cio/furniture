package cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 履约包裹创建 Request VO")
@Data
public class ShipmentPackageCreateReqVO {

    @NotNull @PositiveOrZero
    private Integer expectedVersion;

    @NotBlank @Size(max = 32)
    private String packageNo;

    @NotBlank @Pattern(regexp = "PARCEL|CARTON|PALLET|FURNITURE_ITEM") @Size(max = 20)
    private String packageType;

    @Positive
    private Long carrierId;

    @Size(max = 64) @ToString.Exclude
    private String trackingNumber;

    @PositiveOrZero @Digits(integer = 12, fraction = 6)
    private BigDecimal weight;

    @Pattern(regexp = "LB|KG") @Size(max = 4)
    private String weightUnit;

    @PositiveOrZero @Digits(integer = 12, fraction = 6)
    private BigDecimal length;

    @PositiveOrZero @Digits(integer = 12, fraction = 6)
    private BigDecimal width;

    @PositiveOrZero @Digits(integer = 12, fraction = 6)
    private BigDecimal height;

    @Pattern(regexp = "IN|CM") @Size(max = 4)
    private String dimensionUnit;
}
