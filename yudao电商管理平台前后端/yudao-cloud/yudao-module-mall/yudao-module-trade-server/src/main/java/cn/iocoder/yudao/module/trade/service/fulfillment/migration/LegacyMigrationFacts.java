package cn.iocoder.yudao.module.trade.service.fulfillment.migration;

import java.time.LocalDateTime;

public record LegacyMigrationFacts(String originCountry, String destinationCountry,
                                   String originTimezone, String destinationTimezone,
                                   Long warehouseId, Long migrationProviderId,
                                   Long approvedBy, LocalDateTime approvedAt,
                                   String sourceReference) {
}
