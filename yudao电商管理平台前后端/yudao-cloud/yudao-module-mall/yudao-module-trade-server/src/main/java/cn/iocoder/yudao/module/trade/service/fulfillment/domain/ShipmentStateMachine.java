package cn.iocoder.yudao.module.trade.service.fulfillment.domain;

import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum.APPOINTMENT_CONFIRMED;
import static cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum.APPOINTMENT_REQUIRED;
import static cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum.AT_LOCAL_TERMINAL;
import static cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum.CANCELED;
import static cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum.DELIVERED;
import static cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum.DELIVERY_EXCEPTION;
import static cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum.DRAFT;
import static cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum.HANDED_TO_CARRIER;
import static cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum.IN_TRANSIT;
import static cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum.OUT_FOR_DELIVERY;
import static cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum.READY_TO_SHIP;
import static cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum.RETURNED;
import static cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum.RETURNING;

public final class ShipmentStateMachine {

    private static final EnumMap<ShipmentStatusEnum, Set<ShipmentStatusEnum>> ALLOWED_TRANSITIONS =
            createAllowedTransitions();
    private static final Set<ShipmentStatusEnum> TERMINAL_STATUSES = Set.of(DELIVERED, RETURNED, CANCELED);

    public TransitionDecision decide(ShipmentStatusEnum current, ShipmentStatusEnum incoming,
                                     LocalDateTime currentOccurredAt, LocalDateTime incomingOccurredAt) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(incoming, "incoming");
        Objects.requireNonNull(incomingOccurredAt, "incomingOccurredAt");

        if (current == incoming) {
            return TransitionDecision.TIMELINE_ONLY;
        }
        if (TERMINAL_STATUSES.contains(current)) {
            return TransitionDecision.TIMELINE_ONLY;
        }
        if (currentOccurredAt != null && incomingOccurredAt.isBefore(currentOccurredAt)) {
            return TransitionDecision.TIMELINE_ONLY;
        }
        if (ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(incoming)) {
            return TransitionDecision.APPLY;
        }
        return TransitionDecision.REJECT;
    }

    private static EnumMap<ShipmentStatusEnum, Set<ShipmentStatusEnum>> createAllowedTransitions() {
        EnumMap<ShipmentStatusEnum, Set<ShipmentStatusEnum>> transitions = new EnumMap<>(ShipmentStatusEnum.class);
        transitions.put(DRAFT, Set.of(READY_TO_SHIP, CANCELED));
        transitions.put(READY_TO_SHIP, Set.of(HANDED_TO_CARRIER, CANCELED));
        transitions.put(HANDED_TO_CARRIER, Set.of(IN_TRANSIT, DELIVERY_EXCEPTION, CANCELED));
        transitions.put(IN_TRANSIT,
                Set.of(AT_LOCAL_TERMINAL, APPOINTMENT_REQUIRED, OUT_FOR_DELIVERY, DELIVERY_EXCEPTION, RETURNING));
        transitions.put(AT_LOCAL_TERMINAL,
                Set.of(APPOINTMENT_REQUIRED, APPOINTMENT_CONFIRMED, OUT_FOR_DELIVERY, DELIVERY_EXCEPTION, RETURNING));
        transitions.put(APPOINTMENT_REQUIRED, Set.of(APPOINTMENT_CONFIRMED, DELIVERY_EXCEPTION, RETURNING));
        transitions.put(APPOINTMENT_CONFIRMED,
                Set.of(OUT_FOR_DELIVERY, APPOINTMENT_REQUIRED, DELIVERY_EXCEPTION, RETURNING));
        transitions.put(OUT_FOR_DELIVERY, Set.of(DELIVERED, APPOINTMENT_REQUIRED, DELIVERY_EXCEPTION, RETURNING));
        transitions.put(DELIVERY_EXCEPTION,
                Set.of(IN_TRANSIT, AT_LOCAL_TERMINAL, APPOINTMENT_REQUIRED, APPOINTMENT_CONFIRMED,
                        OUT_FOR_DELIVERY, DELIVERED, RETURNING));
        transitions.put(RETURNING, Set.of(RETURNED, DELIVERY_EXCEPTION));
        return transitions;
    }

    public enum TransitionDecision {
        APPLY,
        TIMELINE_ONLY,
        REJECT
    }

}
