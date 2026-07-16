package cn.iocoder.yudao.module.trade.service.fulfillment.migration;

import cn.iocoder.yudao.module.trade.dal.dataobject.fulfillment.LegacyMigrationFactDO;
import cn.iocoder.yudao.module.trade.dal.mysql.fulfillment.LegacyMigrationFactMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LegacyMigrationFactSourceImpl implements LegacyMigrationFactSource {

    private final LegacyMigrationFactMapper factMapper;

    @Override
    public Optional<LegacyMigrationFacts> findApprovedFacts(Long tenantId, Long orderId) {
        LegacyMigrationFactDO fact = factMapper.selectActiveByOrderId(tenantId, orderId);
        if (fact == null) {
            return Optional.empty();
        }
        return Optional.of(new LegacyMigrationFacts(fact.getOriginCountry(), fact.getDestinationCountry(),
                fact.getOriginTimezone(), fact.getDestinationTimezone(), fact.getWarehouseId(),
                fact.getMigrationProviderId(), fact.getApprovedBy(), fact.getApprovedAt(),
                fact.getSourceReference()));
    }
}
