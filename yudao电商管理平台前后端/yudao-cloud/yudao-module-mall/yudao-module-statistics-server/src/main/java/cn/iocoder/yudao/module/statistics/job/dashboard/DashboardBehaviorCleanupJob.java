package cn.iocoder.yudao.module.statistics.job.dashboard;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.statistics.dal.mysql.dashboard.BehaviorEventMapper;
import cn.iocoder.yudao.module.statistics.dal.mysql.dashboard.ConsentEvidenceMapper;
import cn.iocoder.yudao.module.statistics.framework.config.BehaviorTrackingProperties;
import cn.iocoder.yudao.module.statistics.framework.config.DashboardAggregationProperties;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Component
public class DashboardBehaviorCleanupJob {
    @Resource
    private DashboardAggregationProperties properties;
    @Resource
    private BehaviorTrackingProperties behaviorProperties;
    @Resource
    private BehaviorEventMapper eventMapper;
    @Resource
    private ConsentEvidenceMapper consentMapper;

    @XxlJob("dashboardBehaviorCleanupJob")
    public void execute() {
        LocalDateTime eventCutoff = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
                .minusDays(properties.getEventRetentionDays());
        long consentCutoffEpoch = Instant.now()
                .minus(behaviorProperties.getConsentRecordRetentionDays(), ChronoUnit.DAYS)
                .getEpochSecond();
        for (Long tenant : properties.getEnabledTenantIds()) {
            int deleted;
            do {
                deleted = TenantUtils.execute(tenant,
                        () -> eventMapper.physicalDeleteAggregatedBatch(tenant, eventCutoff));
            } while (deleted == 10000);
            do {
                deleted = TenantUtils.execute(tenant,
                        () -> consentMapper.physicalDeleteExpiredBatch(tenant, consentCutoffEpoch));
            } while (deleted == 10000);
        }
    }
}
