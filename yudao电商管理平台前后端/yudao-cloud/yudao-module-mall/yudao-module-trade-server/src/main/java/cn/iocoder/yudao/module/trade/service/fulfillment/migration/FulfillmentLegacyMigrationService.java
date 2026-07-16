package cn.iocoder.yudao.module.trade.service.fulfillment.migration;

public interface FulfillmentLegacyMigrationService {

    MigrationBatchResult migrateActiveOrders(Long tenantId, Long afterOrderId, int limit, boolean dryRun);

}
