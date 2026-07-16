package cn.iocoder.yudao.module.trade.service.fulfillment.migration;

public interface FulfillmentLegacyMigrationWriter {

    MigrationOrderResult migrateOne(Long tenantId, Long orderId);

}
