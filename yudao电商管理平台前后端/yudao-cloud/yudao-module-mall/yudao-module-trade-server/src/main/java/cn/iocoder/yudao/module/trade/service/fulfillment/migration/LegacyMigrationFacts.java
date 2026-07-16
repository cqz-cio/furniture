package cn.iocoder.yudao.module.trade.service.fulfillment.migration;

import java.time.LocalDateTime;

public record LegacyMigrationFacts(String originCountry, String destinationCountry,
                                   String originTimezone, String destinationTimezone,
                                   Long warehouseId, Long migrationProviderId,
                                   Long approvedBy, LocalDateTime approvedAt,
                                   String sourceReference) {

    @Override
    public String toString() {
        return "LegacyMigrationFacts[originCountry=" + originCountry
                + ", destinationCountry=" + destinationCountry
                + ", originTimezone=" + originTimezone
                + ", destinationTimezone=" + destinationTimezone
                + ", warehouseId=" + warehouseId
                + ", migrationProviderId=" + migrationProviderId
                + ", approvedBy=" + approvedBy
                + ", approvedAt=" + approvedAt
                + ", sourceReference=[REDACTED]]";
    }
}
