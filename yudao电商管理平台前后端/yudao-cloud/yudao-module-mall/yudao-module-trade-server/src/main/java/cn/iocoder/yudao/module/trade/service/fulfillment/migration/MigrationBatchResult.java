package cn.iocoder.yudao.module.trade.service.fulfillment.migration;

import java.util.List;
import java.util.Objects;

public record MigrationBatchResult(boolean dryRun, int scanned, int wouldMigrate,
                                   int migrated, int alreadyMigrated, int rejected,
                                   Long nextAfterOrderId, boolean hasMore,
                                   List<MigrationOrderResult> orders) {

    public MigrationBatchResult {
        Objects.requireNonNull(nextAfterOrderId, "nextAfterOrderId");
        orders = List.copyOf(orders);
    }
}
