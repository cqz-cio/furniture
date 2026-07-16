package cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.ToString;

@Schema(description = "管理后台 - 履约运输段创建 Request VO")
@Data
public class ShipmentLegCreateReqVO {

    @NotNull @PositiveOrZero
    private Integer expectedVersion;

    @Positive
    private Long packageId;

    @NotNull @Min(1)
    private Integer sequenceNo;

    @NotBlank @Pattern(regexp = "FIRST_MILE|LINEHAUL|LAST_MILE") @Size(max = 20)
    private String legType;

    @NotNull @Positive
    private Long carrierId;

    @NotNull @Positive
    private Long providerId;

    @Size(max = 64)
    private String serviceLevel;

    @Size(max = 64) @ToString.Exclude
    private String trackingNumber;

    @Size(max = 64) @ToString.Exclude
    private String proNumber;

    @Size(max = 64) @ToString.Exclude
    private String bolNumber;

    @Size(max = 256) @ToString.Exclude
    private String originLocation;

    @Size(max = 256) @ToString.Exclude
    private String destinationLocation;
}
