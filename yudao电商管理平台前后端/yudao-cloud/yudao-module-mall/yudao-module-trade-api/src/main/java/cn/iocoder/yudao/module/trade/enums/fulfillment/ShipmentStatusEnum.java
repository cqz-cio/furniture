package cn.iocoder.yudao.module.trade.enums.fulfillment;

public enum ShipmentStatusEnum {
    DRAFT,
    READY_TO_SHIP,
    HANDED_TO_CARRIER,
    IN_TRANSIT,
    AT_LOCAL_TERMINAL,
    APPOINTMENT_REQUIRED,
    APPOINTMENT_CONFIRMED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    DELIVERY_EXCEPTION,
    RETURNING,
    RETURNED,
    CANCELED
}
