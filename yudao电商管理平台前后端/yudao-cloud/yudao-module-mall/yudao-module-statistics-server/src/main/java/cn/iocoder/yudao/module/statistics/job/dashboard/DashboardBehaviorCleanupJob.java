package cn.iocoder.yudao.module.statistics.job.dashboard;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;import cn.iocoder.yudao.module.statistics.dal.mysql.dashboard.BehaviorEventMapper;import cn.iocoder.yudao.module.statistics.framework.config.DashboardAggregationProperties;import com.xxl.job.core.handler.annotation.XxlJob;import org.springframework.stereotype.Component;import javax.annotation.Resource;import java.time.*;
@Component public class DashboardBehaviorCleanupJob { @Resource private DashboardAggregationProperties properties;@Resource private BehaviorEventMapper mapper;
 @XxlJob("dashboardBehaviorCleanupJob") public void execute(){LocalDateTime cutoff=LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusDays(properties.getEventRetentionDays());for(Long tenant:properties.getEnabledTenantIds()){int deleted;do{deleted=TenantUtils.execute(tenant,()->mapper.physicalDeleteAggregatedBatch(tenant,cutoff));}while(deleted==10000);}}
}
