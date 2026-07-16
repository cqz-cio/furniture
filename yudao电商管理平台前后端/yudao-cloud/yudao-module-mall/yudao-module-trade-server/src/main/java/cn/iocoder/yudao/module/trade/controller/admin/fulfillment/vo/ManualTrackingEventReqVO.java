package cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo;

import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.ToString;

import java.time.Instant;

@Schema(description = "管理后台 - 人工物流轨迹修正 Request VO")
@Data
public class ManualTrackingEventReqVO {

    @Positive
    private Long packageId;

    @NotNull @Positive
    private Long shipmentLegId;

    @NotNull
    private ShipmentStatusEnum requestedStatus;

    @NotNull
    private Instant occurredAt;

    @NotNull @PositiveOrZero
    private Integer expectedVersion;

    @NotBlank @Size(min = 5, max = 500) @ToString.Exclude
    @Schema(description = "人工修正原因；禁止填写地址、电话、姓名等个人信息", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reason;
}
