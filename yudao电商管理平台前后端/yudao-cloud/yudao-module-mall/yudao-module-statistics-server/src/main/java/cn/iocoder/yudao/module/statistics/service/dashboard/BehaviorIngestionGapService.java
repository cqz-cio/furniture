package cn.iocoder.yudao.module.statistics.service.dashboard;
import java.time.LocalDateTime;
public interface BehaviorIngestionGapService { void recordRejected(LocalDateTime receivedAt, String reasonCode); }
