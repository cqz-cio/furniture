package cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo;
import lombok.Data;import java.math.BigDecimal;import java.time.LocalDate;
@Data public class DashboardTrendItemRespVO {private LocalDate day,referenceDay;private Long homePv,productDetailPv,addCartCount,paidOrderCount,paidRevenue,refundAmount,netRevenue,costAmount,grossProfit;private BigDecimal grossMarginPercent;private String trafficDataStatus;private DashboardTrendItemRespVO reference;}
