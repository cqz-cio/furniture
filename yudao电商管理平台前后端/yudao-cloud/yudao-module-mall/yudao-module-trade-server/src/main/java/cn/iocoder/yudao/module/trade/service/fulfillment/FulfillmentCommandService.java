package cn.iocoder.yudao.module.trade.service.fulfillment;

import cn.iocoder.yudao.module.trade.service.fulfillment.command.CreateShipmentCommand;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.AddShipmentLegCommand;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.DispatchShipmentCommand;
import cn.iocoder.yudao.module.trade.service.fulfillment.command.UpsertPackageCommand;

public interface FulfillmentCommandService {

    Long createShipment(String idempotencyKey, CreateShipmentCommand command);

    Long addPackage(String idempotencyKey, UpsertPackageCommand command);

    Long addLeg(String idempotencyKey, AddShipmentLegCommand command);

    void markReady(String idempotencyKey, Long tenantId, Long shipmentId, Integer expectedVersion);

    void dispatch(String idempotencyKey, DispatchShipmentCommand command);

}
