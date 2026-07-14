package cn.iocoder.yudao.module.statistics.service.dashboard;
import cn.iocoder.yudao.module.statistics.framework.config.DashboardAggregationProperties;import org.springframework.stereotype.Component;import javax.annotation.Resource;import java.time.LocalDate;
@Component public class DashboardTenantExecutor { @Resource private DashboardAggregationProperties properties; @Resource private DashboardAggregationService service; public void recompute(LocalDate day){for(Long tenant:properties.getEnabledTenantIds())service.recomputeDay(tenant,day);} }
