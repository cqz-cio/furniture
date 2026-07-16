package cn.iocoder.yudao.module.trade.service.fulfillment.command;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DispatchShipmentCommand {

    private Long tenantId;
    private Long shipmentId;
    private Integer expectedVersion;

}
