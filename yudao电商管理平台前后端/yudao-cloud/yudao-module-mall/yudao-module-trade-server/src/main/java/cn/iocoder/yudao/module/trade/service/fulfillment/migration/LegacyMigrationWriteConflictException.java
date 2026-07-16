package cn.iocoder.yudao.module.trade.service.fulfillment.migration;

/** Internal signal raised inside the per-order transaction and mapped only after rollback. */
final class LegacyMigrationWriteConflictException extends RuntimeException {

    private final Long orderId;
    private final MigrationOutcome outcome;

    LegacyMigrationWriteConflictException(Long orderId, MigrationOutcome outcome, Throwable cause) {
        super("Legacy migration write conflict", cause);
        this.orderId = orderId;
        this.outcome = outcome;
    }

    MigrationOrderResult toResult() {
        return MigrationOrderResult.of(orderId, outcome);
    }
}
