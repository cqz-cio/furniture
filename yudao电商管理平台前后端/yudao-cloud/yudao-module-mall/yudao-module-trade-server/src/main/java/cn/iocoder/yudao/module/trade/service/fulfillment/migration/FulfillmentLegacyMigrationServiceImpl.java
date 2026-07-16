package cn.iocoder.yudao.module.trade.service.fulfillment.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FulfillmentLegacyMigrationServiceImpl implements FulfillmentLegacyMigrationService {

    private static final int MAX_LIMIT = 100;

    private final TradeOrderMapper orderMapper;
    private final LegacyMigrationEligibilityEvaluator evaluator;

    @Override
    public MigrationBatchResult migrateActiveOrders(Long tenantId, Long afterOrderId, int limit, boolean dryRun) {
        Long contextTenantId = TenantContextHolder.getRequiredTenantId();
        if (tenantId == null || !tenantId.equals(contextTenantId)) {
            throw new IllegalArgumentException("tenantId must match the active tenant");
        }
        long cursor = afterOrderId == null ? 0L : afterOrderId;
        if (cursor < 0 || limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Migration cursor or limit is out of bounds");
        }
        if (!dryRun) {
            throw new IllegalStateException("Legacy fulfillment migration writer is not available");
        }

        List<TradeOrderDO> selected = orderMapper.selectLegacyMigrationCandidates(tenantId, cursor, limit + 1);
        boolean hasMore = selected.size() > limit;
        List<TradeOrderDO> page = hasMore ? selected.subList(0, limit) : selected;
        List<MigrationOrderResult> results = new ArrayList<>(page.size());
        for (TradeOrderDO order : page) {
            results.add(evaluator.evaluate(tenantId, order));
        }
        int wouldMigrate = (int) results.stream()
                .filter(result -> result.outcome() == MigrationOutcome.WOULD_MIGRATE).count();
        long nextCursor = page.isEmpty() ? cursor : page.get(page.size() - 1).getId();
        return new MigrationBatchResult(true, page.size(), wouldMigrate, 0, 0,
                page.size() - wouldMigrate, nextCursor, hasMore, results);
    }
}
