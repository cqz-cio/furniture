package cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum;
import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 北美履约发货单分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ShipmentPageReqVO extends PageParam {

    @Schema(description = "订单编号")
    @Positive
    private Long orderId;

    @Schema(description = "发货单号")
    @Size(max = 32)
    private String shipmentNo;

    @Schema(description = "发货单类型")
    private ShipmentTypeEnum shipmentType;

    @Schema(description = "发货单状态")
    private ShipmentStatusEnum status;

    @Schema(description = "始发国家代码")
    @Pattern(regexp = "US|CA")
    @Size(max = 2)
    private String originCountry;

    @Schema(description = "目的国家代码")
    @Pattern(regexp = "US|CA")
    @Size(max = 2)
    private String destinationCountry;

    @Schema(description = "创建时间范围")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
