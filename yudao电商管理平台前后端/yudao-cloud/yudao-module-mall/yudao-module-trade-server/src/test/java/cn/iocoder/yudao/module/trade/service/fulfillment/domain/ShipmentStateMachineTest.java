package cn.iocoder.yudao.module.trade.service.fulfillment.domain;

import cn.iocoder.yudao.module.trade.enums.fulfillment.ShipmentStatusEnum;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

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
import static cn.iocoder.yudao.module.trade.service.fulfillment.domain.ShipmentStateMachine.TransitionDecision.APPLY;
import static cn.iocoder.yudao.module.trade.service.fulfillment.domain.ShipmentStateMachine.TransitionDecision.REJECT;
import static cn.iocoder.yudao.module.trade.service.fulfillment.domain.ShipmentStateMachine.TransitionDecision.TIMELINE_ONLY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShipmentStateMachineTest {

    private final ShipmentStateMachine stateMachine = new ShipmentStateMachine();

    @Test
    void everyApprovedTransitionIsApplied() {
        List<Transition> transitions = List.of(
                transition(DRAFT, READY_TO_SHIP),
                transition(DRAFT, CANCELED),
                transition(READY_TO_SHIP, HANDED_TO_CARRIER),
                transition(READY_TO_SHIP, CANCELED),
                transition(HANDED_TO_CARRIER, IN_TRANSIT),
                transition(HANDED_TO_CARRIER, DELIVERY_EXCEPTION),
                transition(HANDED_TO_CARRIER, CANCELED),
                transition(IN_TRANSIT, AT_LOCAL_TERMINAL),
                transition(IN_TRANSIT, APPOINTMENT_REQUIRED),
                transition(IN_TRANSIT, OUT_FOR_DELIVERY),
                transition(IN_TRANSIT, DELIVERY_EXCEPTION),
                transition(IN_TRANSIT, RETURNING),
                transition(AT_LOCAL_TERMINAL, APPOINTMENT_REQUIRED),
                transition(AT_LOCAL_TERMINAL, APPOINTMENT_CONFIRMED),
                transition(AT_LOCAL_TERMINAL, OUT_FOR_DELIVERY),
                transition(AT_LOCAL_TERMINAL, DELIVERY_EXCEPTION),
                transition(AT_LOCAL_TERMINAL, RETURNING),
                transition(APPOINTMENT_REQUIRED, APPOINTMENT_CONFIRMED),
                transition(APPOINTMENT_REQUIRED, DELIVERY_EXCEPTION),
                transition(APPOINTMENT_REQUIRED, RETURNING),
                transition(APPOINTMENT_CONFIRMED, OUT_FOR_DELIVERY),
                transition(APPOINTMENT_CONFIRMED, APPOINTMENT_REQUIRED),
                transition(APPOINTMENT_CONFIRMED, DELIVERY_EXCEPTION),
                transition(APPOINTMENT_CONFIRMED, RETURNING),
                transition(OUT_FOR_DELIVERY, DELIVERED),
                transition(OUT_FOR_DELIVERY, APPOINTMENT_REQUIRED),
                transition(OUT_FOR_DELIVERY, DELIVERY_EXCEPTION),
                transition(OUT_FOR_DELIVERY, RETURNING),
                transition(DELIVERY_EXCEPTION, IN_TRANSIT),
                transition(DELIVERY_EXCEPTION, AT_LOCAL_TERMINAL),
                transition(DELIVERY_EXCEPTION, APPOINTMENT_REQUIRED),
                transition(DELIVERY_EXCEPTION, APPOINTMENT_CONFIRMED),
                transition(DELIVERY_EXCEPTION, OUT_FOR_DELIVERY),
                transition(DELIVERY_EXCEPTION, DELIVERED),
                transition(DELIVERY_EXCEPTION, RETURNING),
                transition(RETURNING, RETURNED),
                transition(RETURNING, DELIVERY_EXCEPTION));

        for (Transition transition : transitions) {
            assertEquals(APPLY, stateMachine.decide(transition.current(), transition.incoming(),
                    time("2026-07-15T10:00:00"), time("2026-07-15T11:00:00")), transition.toString());
        }
    }

    @Test
    void sameStatusOnlyUpdatesTimeline() {
        for (ShipmentStatusEnum status : ShipmentStatusEnum.values()) {
            assertEquals(TIMELINE_ONLY, stateMachine.decide(status, status,
                    time("2026-07-15T10:00:00"), time("2026-07-15T11:00:00")), status.name());
        }
    }

    @Test
    void terminalStatusesNeverRegress() {
        for (ShipmentStatusEnum terminal : List.of(DELIVERED, RETURNED, CANCELED)) {
            assertEquals(TIMELINE_ONLY, stateMachine.decide(terminal, IN_TRANSIT,
                    time("2026-07-15T10:00:00"), time("2026-07-15T11:00:00")), terminal.name());
        }
    }

    @Test
    void deliveredNeverRegressesToInTransit() {
        assertEquals(TIMELINE_ONLY, stateMachine.decide(
                DELIVERED, IN_TRANSIT, time("2026-07-15T10:00:00"), time("2026-07-15T11:00:00")));
    }

    @Test
    void lateEventIsStoredWithoutChangingCurrentStatus() {
        assertEquals(TIMELINE_ONLY, stateMachine.decide(
                OUT_FOR_DELIVERY, AT_LOCAL_TERMINAL,
                time("2026-07-15T10:00:00"), time("2026-07-15T09:00:00")));
    }

    @Test
    void exceptionCanRecoverToOutForDelivery() {
        assertEquals(APPLY, stateMachine.decide(
                DELIVERY_EXCEPTION, OUT_FOR_DELIVERY,
                time("2026-07-15T10:00:00"), time("2026-07-15T11:00:00")));
    }

    @Test
    void missingCurrentOccurredAtSkipsLateEventCheck() {
        assertEquals(APPLY, stateMachine.decide(DRAFT, READY_TO_SHIP,
                null, time("2026-07-15T11:00:00")));
    }

    @Test
    void incomingOccurredAtIsRequired() {
        assertThrows(NullPointerException.class,
                () -> stateMachine.decide(DRAFT, READY_TO_SHIP, time("2026-07-15T10:00:00"), null));
    }

    @Test
    void unapprovedTransitionIsRejected() {
        assertEquals(REJECT, stateMachine.decide(DRAFT, DELIVERED,
                time("2026-07-15T10:00:00"), time("2026-07-15T11:00:00")));
    }

    private static Transition transition(ShipmentStatusEnum current, ShipmentStatusEnum incoming) {
        return new Transition(current, incoming);
    }

    private static LocalDateTime time(String value) {
        return LocalDateTime.parse(value);
    }

    private record Transition(ShipmentStatusEnum current, ShipmentStatusEnum incoming) {
    }

}
