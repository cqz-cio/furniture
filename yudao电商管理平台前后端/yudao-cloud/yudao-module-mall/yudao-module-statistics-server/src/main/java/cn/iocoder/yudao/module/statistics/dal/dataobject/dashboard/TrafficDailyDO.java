package cn.iocoder.yudao.module.statistics.dal.dataobject.dashboard;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;
@TableName("statistics_traffic_daily") @KeySequence("statistics_traffic_daily_seq")
@Data @EqualsAndHashCode(callSuper=true)
public class TrafficDailyDO extends TenantBaseDO {
 @TableId private Long id; private LocalDate day; private String currencyCode;
 private Long homePv; private Long homeUv; private Long productDetailPv; private Long productDetailUv;
 private Long addCartCount; private Long addCartUserCount; private Long checkoutStartCount;
 private Long paidOrderCount; private Long paidBuyerCount; private Long paidItemCount; private Long paidRevenue;
 private Long refundAmount; private Long netRevenue; private Long knownCostAmount; private Long costAmount; private Long grossProfit;
 private BigDecimal grossMarginPercent; private Long exactCostItemCount; private Long estimatedCostItemCount; private Long missingCostItemCount;
 private Integer profitDataQuality; private Long acceptedEventCount; private Long excludedEventCount; private Integer trafficDataStatus;
 private LocalDateTime trafficWatermark; private LocalDateTime tradeWatermark; private LocalDateTime refundWatermark; private LocalDateTime lastSuccessfulRunAt;
}
