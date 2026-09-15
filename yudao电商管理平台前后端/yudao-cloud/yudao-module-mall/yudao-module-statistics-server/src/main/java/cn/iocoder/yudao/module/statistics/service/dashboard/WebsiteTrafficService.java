package cn.iocoder.yudao.module.statistics.service.dashboard;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo.*;
import cn.iocoder.yudao.module.statistics.dal.mysql.dashboard.WebsiteTrafficEventMapper;
import cn.iocoder.yudao.module.statistics.framework.config.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
@Service
public class WebsiteTrafficService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    @Resource private WebsiteTrafficProperties website;
    @Resource private BehaviorTrackingProperties behavior;
    @Resource private WebsiteTrafficEventMapper events;
    public boolean configured() { return website.getEnabledFrom().containsKey(String.valueOf(tenant())); }
    public boolean enabled() { return configured() && behavior.isEnabled() && behavior.getEnabledTenantIds().contains(tenant()); }
    public Map<String, Object> config() {
        return Map.of("enabled", enabled(), "consentRequired", true, "policyVersion", behavior.getConsentPolicyVersion());
    }
    public WebsiteTrafficRespVO summary(DashboardQueryReqVO query) {
        Range range = range(query);
        if (!enabled() || range.end().isBefore(availableFrom())) return metadata(new WebsiteTrafficRespVO(), "UNAVAILABLE");
        LocalDate collectedStart = range.start().isBefore(availableFrom()) ? availableFrom() : range.start();
        var result = events.summary(tenant(), collectedStart, range.end());
        if (result == null) result = new WebsiteTrafficRespVO().setHomePv(0L).setHomeUv(0L);
        boolean partial = range.start().isBefore(availableFrom()) || !events.gaps(tenant(), range.start(), range.end()).isEmpty();
        return metadata(result, partial ? "PARTIAL" : "COMPLETE");
    }
    public List<WebsiteTrafficRespVO> trend(DashboardQueryReqVO query) {
        Range range = range(query);
        if (!enabled()) return List.of();
        Map<LocalDate, WebsiteTrafficRespVO> rows = events.trend(tenant(), range.start(), range.end()).stream().collect(Collectors.toMap(WebsiteTrafficRespVO::getDay, x -> x));
        Set<LocalDate> gaps = new HashSet<>(events.gaps(tenant(), range.start(), range.end()));
        List<WebsiteTrafficRespVO> result = new ArrayList<>();
        for (LocalDate day = range.start(); !day.isAfter(range.end()); day = day.plusDays(1)) {
            boolean available = !day.isBefore(availableFrom());
            var row = available ? rows.getOrDefault(day, new WebsiteTrafficRespVO().setDay(day).setHomePv(0L).setHomeUv(0L))
                : new WebsiteTrafficRespVO().setDay(day);
            result.add(metadata(row, !available ? "UNAVAILABLE" : gaps.contains(day) ? "PARTIAL" : "COMPLETE"));
        }
        return result;
    }
    private WebsiteTrafficRespVO metadata(WebsiteTrafficRespVO row, String status) {
        return row.setTrafficDataStatus(status).setFreshnessStatus(enabled() ? "FRESH" : "STALE")
            .setAsOf(LocalDateTime.now(ZONE)).setTrafficDataAvailableFrom(availableFrom());
    }
    private LocalDate availableFrom() { return website.getEnabledFrom().get(String.valueOf(tenant())); }
    private Long tenant() { return TenantContextHolder.getRequiredTenantId(); }
    private Range range(DashboardQueryReqVO query) {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate end = query.getEndDate() == null ? today : query.getEndDate();
        LocalDate start = query.getStartDate() == null ? end.minusDays(29) : query.getStartDate();
        if (start.isAfter(end) || end.isAfter(today) || start.isBefore(today.minusDays(89)) || ChronoUnit.DAYS.between(start, end) > 89)
            throw new IllegalArgumentException("Website traffic supports the most recent 90 days");
        return new Range(start, end);
    }
    private record Range(LocalDate start, LocalDate end) {}
}
