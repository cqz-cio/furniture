package cn.iocoder.yudao.module.statistics.service.dashboard;
import java.time.LocalDate;
public interface DashboardAggregationService { void recomputeDay(long tenantId, LocalDate day); }
