package cn.iocoder.yudao.module.statistics.service.dashboard;
import lombok.*;
import java.math.*;
import org.springframework.stereotype.Component;
@Component
public class DashboardMetricCalculator {
 public Result calculate(long paidRevenue,long refundAmount,long knownCost,long missingCostRows,long exactRows,long estimatedRows,Long views,long paidOrders){
  long net=paidRevenue-refundAmount; Long cost=missingCostRows>0?null:knownCost; Long profit=cost==null?null:net-cost;
  BigDecimal margin=profit==null||net<=0?null:percent(profit,net);
  BigDecimal conversion=views==null||views==0?null:percent(paidOrders,views);
  int quality=missingCostRows>0?4:(exactRows==0&&estimatedRows==0?5:(exactRows>0&&estimatedRows>0?2:(estimatedRows>0?3:1)));
  return new Result(net,cost,profit,margin,conversion,quality);
 }
 private BigDecimal percent(long numerator,long denominator){return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(denominator),4,RoundingMode.HALF_UP);}
 @Value public static class Result { long netRevenue; Long costAmount; Long grossProfit; BigDecimal grossMarginPercent; BigDecimal browseOrderConversionPercent; int profitDataQuality; }
}
