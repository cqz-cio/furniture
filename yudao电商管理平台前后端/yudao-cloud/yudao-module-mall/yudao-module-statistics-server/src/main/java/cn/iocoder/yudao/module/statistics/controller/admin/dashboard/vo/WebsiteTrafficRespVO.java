package cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Explicit allow-list: no sales, order, customer, cost or profit fields. */
@Data
public class WebsiteTrafficRespVO {
    private Long homePv;
    private Long homeUv;
    private String trafficDataStatus;
    private String freshnessStatus;
    private LocalDateTime asOf;
    private LocalDateTime trafficWatermark;
    private LocalDate trafficDataAvailableFrom;
    private LocalDate day;
}
