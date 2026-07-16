package cn.iocoder.yudao.module.trade.service.fulfillment.migration;

import java.util.Optional;

public interface LegacyMigrationFactSource {

    Optional<LegacyMigrationFacts> findApprovedFacts(Long tenantId, Long orderId);

    Optional<LegacyMigrationFacts> findApprovedFactsForUpdate(Long tenantId, Long orderId);

}
