package cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class DashboardMetricChangeRespVO {
    private Long referenceValue;
    private Long changeAmount;
    private BigDecimal changePercent;
    private BigDecimal referenceRate;
    private BigDecimal changePercentagePoints;
}
