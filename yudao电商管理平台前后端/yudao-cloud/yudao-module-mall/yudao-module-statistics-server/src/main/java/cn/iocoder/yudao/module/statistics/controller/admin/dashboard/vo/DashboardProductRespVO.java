package cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo;
import lombok.Data;import java.math.BigDecimal;
@Data public class DashboardProductRespVO {private Long spuId,categoryId;private String productName,picUrl;private Long browseCount,browseUserCount,cartCount,orderCount,orderPayCount,orderPayPrice,afterSaleCount,afterSaleRefundPrice,netRevenue;private BigDecimal browseConvertPercent;private Long knownCostAmount,costAmount,grossProfit,missingCostItemCount;private BigDecimal grossMarginPercent;private String trafficDataStatus,profitDataQuality;}
