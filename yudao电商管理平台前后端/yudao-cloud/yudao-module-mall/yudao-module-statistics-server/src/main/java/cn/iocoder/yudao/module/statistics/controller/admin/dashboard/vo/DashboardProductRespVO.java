package cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo;
import lombok.Data;import java.math.BigDecimal;
@Data public class DashboardProductRespVO {private Long spuId,categoryId;private String productName,picUrl;private Integer browseCount,browseUserCount,cartCount,orderCount,orderPayCount,orderPayPrice,afterSaleCount,afterSaleRefundPrice,browseConvertPercent;private Long knownCostAmount,costAmount,grossProfit,missingCostItemCount;private BigDecimal grossMarginPercent;private String trafficDataStatus,profitDataQuality;}
