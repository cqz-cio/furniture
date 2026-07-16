package cn.iocoder.yudao.module.trade.service.fulfillment.migration;

import java.util.Objects;

public record MigrationOrderResult(Long orderId, MigrationOutcome outcome, String reasonCode) {

    public MigrationOrderResult {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(reasonCode, "reasonCode");
    }

    public static MigrationOrderResult of(Long orderId, MigrationOutcome outcome) {
        return new MigrationOrderResult(orderId, outcome, outcome.name());
    }
}
