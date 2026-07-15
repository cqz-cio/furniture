package cn.iocoder.yudao.module.trade.framework.fulfillment.core.dto;

import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class ProviderTrackingEvent {

    private ShipmentStatusEnum status;
    private LocalDateTime occurredAt;
    private String location;
    private String description;

}
