package cn.iocoder.yudao.module.statistics.service.dashboard;
import java.time.LocalDate;
public interface BehaviorHmacDayVersionService { int activeVersion(Long tenantId, LocalDate day); }
