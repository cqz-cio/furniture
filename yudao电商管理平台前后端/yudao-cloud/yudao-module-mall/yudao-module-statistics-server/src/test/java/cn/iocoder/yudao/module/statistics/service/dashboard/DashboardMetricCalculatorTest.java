package cn.iocoder.yudao.module.statistics.service.dashboard;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class DashboardMetricCalculatorTest {
 private final DashboardMetricCalculator c=new DashboardMetricCalculator();
 @Test void missingCost_neverFabricatesZeroProfit(){DashboardMetricCalculator.Result r=c.calculate(10000,1000,5000,1,2,0,100L,5);assertNull(r.getCostAmount());assertNull(r.getGrossProfit());assertNull(r.getGrossMarginPercent());assertEquals(4,r.getProfitDataQuality());}
 @Test void zeroOrUnavailableViews_returnsNullConversion(){assertNull(c.calculate(0,0,0,0,0,0,0L,0).getBrowseOrderConversionPercent());assertNull(c.calculate(0,0,0,0,0,0,null,0).getBrowseOrderConversionPercent());}
 @Test void allowsNegativeNetRevenueWithoutClamping(){DashboardMetricCalculator.Result r=c.calculate(100,500,0,0,0,0,10L,1);assertEquals(-400,r.getNetRevenue());assertEquals(Long.valueOf(-400),r.getGrossProfit());assertNull(r.getGrossMarginPercent());assertEquals(new BigDecimal("10.0000"),r.getBrowseOrderConversionPercent());}
}
