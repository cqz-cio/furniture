package cn.iocoder.yudao.module.trade.service.fulfillment;

import cn.iocoder.yudao.module.trade.service.fulfillment.command.CreateShipmentCommand;

public interface FulfillmentCommandService {

    Long createShipment(String idempotencyKey, CreateShipmentCommand command);

}
