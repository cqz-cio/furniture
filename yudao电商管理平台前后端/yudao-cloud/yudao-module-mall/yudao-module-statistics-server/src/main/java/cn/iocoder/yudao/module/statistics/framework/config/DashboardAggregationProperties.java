package cn.iocoder.yudao.module.statistics.framework.config;
import lombok.Data;import org.springframework.boot.context.properties.ConfigurationProperties;import org.springframework.stereotype.Component;import java.util.*;
@Data @Component @ConfigurationProperties(prefix="yudao.statistics.dashboard") public class DashboardAggregationProperties { private List<Long> enabledTenantIds=Collections.singletonList(121L); private int eventRetentionDays=180; }
