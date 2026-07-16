package cn.iocoder.yudao.module.trade.service.fulfillment.migration;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

final class LegacyMigrationTestData {

    private LegacyMigrationTestData() {
    }

    static void seed(JdbcTemplate jdbc, long tenantId, long orderId, int status, String tracking) {
        long carrierId = orderId + 10;
        long warehouseId = orderId + 20;
        long providerId = orderId + 30;
        long legacyExpressId = orderId + 40;
        jdbc.update("INSERT INTO trade_order (id, no, type, terminal, user_id, user_ip, status, "
                        + "product_count, pay_status, discount_price, delivery_price, adjust_price, pay_price, "
                        + "delivery_type, logistics_id, logistics_no, delivery_time, receiver_name, receiver_mobile, "
                        + "receiver_area_id, receiver_detail_address, coupon_id, coupon_price, point_price, tenant_id) "
                        + "VALUES (?, ?, 0, 10, 200, '127.0.0.1', ?, 3, TRUE, 0, 0, 0, 3000, 1, ?, ?, ?, "
                        + "'Receiver', '5550000000', 1, 'controlled-test-address', 0, 0, 0, ?)",
                orderId, "ORDER-MIGRATION-" + orderId, status, legacyExpressId, tracking,
                LocalDateTime.of(2026, 7, 15, 12, 0), tenantId);
        jdbc.update("INSERT INTO trade_order_item (id, user_id, order_id, spu_id, spu_name, sku_id, count, price, "
                        + "discount_price, pay_price, after_sale_status, tenant_id) "
                        + "VALUES (?, 200, ?, 99001, 'Migration item one', ?, 2, 1000, 0, 2000, 0, ?)",
                orderId + 1, orderId, orderId + 101, tenantId);
        jdbc.update("INSERT INTO trade_order_item (id, user_id, order_id, spu_id, spu_name, sku_id, count, price, "
                        + "discount_price, pay_price, after_sale_status, tenant_id) "
                        + "VALUES (?, 200, ?, 99002, 'Migration item two', ?, 1, 1000, 0, 1000, 0, ?)",
                orderId + 2, orderId, orderId + 102, tenantId);
        jdbc.update("INSERT INTO trade_carrier (id, tenant_id, code, name, country_codes, legacy_express_id, status) "
                        + "VALUES (?, ?, ?, 'Migration Carrier', 'US', ?, 0)",
                carrierId, tenantId, "MIG-" + orderId, legacyExpressId);
        jdbc.update("INSERT INTO erp_warehouse (id, name, status, deleted, tenant_id) VALUES (?, ?, 0, FALSE, ?)",
                warehouseId, "Migration warehouse " + orderId, tenantId);
        jdbc.update("INSERT INTO trade_logistics_provider (id, tenant_id, code, name, capabilities, status) "
                        + "VALUES (?, ?, ?, 'Migration Provider', 'TRACKING_QUERY', 0)",
                providerId, tenantId, "provider-" + orderId);
        jdbc.update("INSERT INTO trade_fulfillment_legacy_migration_fact "
                        + "(tenant_id, order_id, origin_country, destination_country, origin_timezone, "
                        + "destination_timezone, warehouse_id, migration_provider_id, approved_by, approved_at, "
                        + "source_reference) VALUES (?, ?, 'US', 'US', 'America/New_York', 'America/Los_Angeles', "
                        + "?, ?, 701, ?, ?)", tenantId, orderId, warehouseId, providerId,
                LocalDateTime.of(2026, 7, 16, 9, 30), "approval-reference-" + orderId);
    }

}
