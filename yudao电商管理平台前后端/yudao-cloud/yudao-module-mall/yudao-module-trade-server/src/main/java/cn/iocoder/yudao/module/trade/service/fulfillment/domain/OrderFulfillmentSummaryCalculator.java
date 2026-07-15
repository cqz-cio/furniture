package cn.iocoder.yudao.module.trade.service.fulfillment.domain;

import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.ShipmentDO;
import cn.iocoder.yudao.module.trade.enums.fulfillment.OrderFulfillmentStatusEnum;
import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum;

import java.util.List;

public final class OrderFulfillmentSummaryCalculator {

    public Calculation calculate(List<ShipmentDO> shipments) {
        List<ShipmentStatusEnum> active = shipments.stream()
                .map(ShipmentDO::getStatus)
                .map(ShipmentStatusEnum::valueOf)
                .filter(status -> status != ShipmentStatusEnum.CANCELED)
                .toList();
        int delivered = (int) active.stream().filter(status -> status == ShipmentStatusEnum.DELIVERED).count();
        if (active.isEmpty()) {
            return new Calculation(OrderFulfillmentStatusEnum.NOT_SHIPPED, 0, 0);
        }
        if (active.stream().allMatch(status -> status == ShipmentStatusEnum.RETURNED)) {
            return new Calculation(OrderFulfillmentStatusEnum.RETURNED, active.size(), delivered);
        }
        if (active.stream().anyMatch(status -> status == ShipmentStatusEnum.RETURNING
                || status == ShipmentStatusEnum.RETURNED)) {
            return new Calculation(OrderFulfillmentStatusEnum.RETURNING, active.size(), delivered);
        }
        if (active.stream().anyMatch(status -> status == ShipmentStatusEnum.DELIVERY_EXCEPTION)) {
            return new Calculation(OrderFulfillmentStatusEnum.DELIVERY_EXCEPTION, active.size(), delivered);
        }
        if (delivered == active.size()) {
            return new Calculation(OrderFulfillmentStatusEnum.DELIVERED, active.size(), delivered);
        }
        if (delivered > 0) {
            return new Calculation(OrderFulfillmentStatusEnum.PARTIALLY_DELIVERED, active.size(), delivered);
        }
        long dispatched = active.stream().filter(OrderFulfillmentSummaryCalculator::isDispatched).count();
        OrderFulfillmentStatusEnum status = dispatched == 0 ? OrderFulfillmentStatusEnum.NOT_SHIPPED
                : dispatched < active.size() ? OrderFulfillmentStatusEnum.PARTIALLY_SHIPPED
                : OrderFulfillmentStatusEnum.SHIPPED;
        return new Calculation(status, active.size(), delivered);
    }

    private static boolean isDispatched(ShipmentStatusEnum status) {
        return status != ShipmentStatusEnum.DRAFT && status != ShipmentStatusEnum.READY_TO_SHIP;
    }

    public record Calculation(OrderFulfillmentStatusEnum status, int shipmentCount, int deliveredShipmentCount) {
    }
}
