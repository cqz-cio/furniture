package cn.iocoder.yudao.module.statistics.service.dashboard;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.statistics.dal.dataobject.dashboard.TrafficDailyDO;
import cn.iocoder.yudao.module.statistics.dal.dataobject.product.ProductStatisticsDO;
import cn.iocoder.yudao.module.statistics.dal.mysql.dashboard.*;
import cn.iocoder.yudao.module.statistics.dal.mysql.product.ProductStatisticsMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.Resource;
import java.time.*;
import java.util.*;
@Service
public class DashboardAggregationServiceImpl implements DashboardAggregationService {
 private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
 @Resource private DashboardAggregationMapper aggregationMapper; @Resource private TrafficDailyMapper trafficMapper;
 @Resource private ProductStatisticsMapper productMapper; @Resource private DashboardMetricCalculator calculator;
 @Override @Transactional(rollbackFor=Exception.class)
 public void recomputeDay(long tenantId,LocalDate day){
  TenantUtils.execute(tenantId,()->{
   Integer versions=aggregationMapper.countHashVersions(tenantId,day); if(versions!=null&&versions>1)throw new IllegalStateException("multiple HMAC versions in tenant/day");
   LocalDateTime begin=day.atStartOfDay(),end=day.plusDays(1).atStartOfDay(),now=LocalDateTime.now(ZONE);
   TrafficDailyDO site=aggregationMapper.selectTrafficDaily(tenantId,day,begin,end);
   if(site==null)site=new TrafficDailyDO().setDay(day).setCurrencyCode("USD").setTrafficDataStatus(day.equals(LocalDate.now(ZONE))?2:1);
   completeSite(site,now);
   List<ProductStatisticsDO> products=aggregationMapper.selectProductDaily(tenantId,day,begin,end); if(products==null)products=Collections.emptyList();
   for(ProductStatisticsDO p:products)completeProduct(p,now);
   trafficMapper.physicalDeleteByTenantAndDay(tenantId,day); productMapper.physicalDeleteByTenantAndDay(tenantId,day);
   trafficMapper.insert(site); if(!products.isEmpty())productMapper.insertBatch(products);
  });
 }
 private void completeSite(TrafficDailyDO d,LocalDateTime now){
  DashboardMetricCalculator.Result r=calculator.calculate(nvl(d.getPaidRevenue()),nvl(d.getRefundAmount()),nvl(d.getKnownCostAmount()),nvl(d.getMissingCostItemCount()),nvl(d.getExactCostItemCount()),nvl(d.getEstimatedCostItemCount()),d.getProductDetailPv(),nvl(d.getPaidOrderCount()));
  d.setNetRevenue(r.getNetRevenue()).setCostAmount(r.getCostAmount()).setGrossProfit(r.getGrossProfit()).setGrossMarginPercent(r.getGrossMarginPercent()).setProfitDataQuality(r.getProfitDataQuality()).setTrafficWatermark(now).setTradeWatermark(now).setRefundWatermark(now).setLastSuccessfulRunAt(now);
 }
 private void completeProduct(ProductStatisticsDO d,LocalDateTime now){
  DashboardMetricCalculator.Result r=calculator.calculate(nvl(d.getOrderPayPrice()),nvl(d.getAfterSaleRefundPrice()),nvl(d.getKnownCostAmount()),nvl(d.getMissingCostItemCount()),nvl(d.getExactCostItemCount()),nvl(d.getEstimatedCostItemCount()),d.getBrowseCount()==null?null:d.getBrowseCount().longValue(),d.getOrderCount()==null?0:d.getOrderCount());
  d.setCostAmount(r.getCostAmount()).setGrossProfit(r.getGrossProfit()).setGrossMarginPercent(r.getGrossMarginPercent()).setProfitDataQuality(r.getProfitDataQuality()).setTrafficWatermark(now).setTradeWatermark(now).setRefundWatermark(now);
  d.setBrowseConvertPercent(r.getBrowseOrderConversionPercent()==null?null:r.getBrowseOrderConversionPercent().setScale(0,java.math.RoundingMode.HALF_UP).intValue());
 }
 private long nvl(Number value){return value==null?0:value.longValue();}
}
