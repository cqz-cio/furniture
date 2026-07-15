package cn.iocoder.yudao.module.trade.service.fulfillment.command;

import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentTypeEnum;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class CreateShipmentCommand {

    private Long tenantId;
    private Long orderId;
    private ShipmentTypeEnum shipmentType;
    private String originCountry;
    private String destinationCountry;
    private String originTimezone;
    private String destinationTimezone;
    private Long warehouseId;
    private Long providerId;
    private List<CreateShipmentItemCommand> items;

}
