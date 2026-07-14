package cn.iocoder.yudao.module.statistics.service.dashboard;
import cn.iocoder.yudao.module.statistics.dal.dataobject.dashboard.TrafficDailyDO;
import cn.iocoder.yudao.module.statistics.dal.dataobject.product.ProductStatisticsDO;
import cn.iocoder.yudao.module.statistics.dal.mysql.dashboard.*;
import cn.iocoder.yudao.module.statistics.dal.mysql.product.ProductStatisticsMapper;
import org.junit.jupiter.api.Test;import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;import java.util.Collections;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.ArgumentMatchers.*;import static org.mockito.Mockito.*;
class DashboardAggregationServiceImplTest {
 @Test void publicTrafficGapDoesNotEraseTrustedServerCartMetrics() throws Exception {try(java.io.InputStream stream=getClass().getResourceAsStream("/mapper/dashboard/DashboardAggregationMapper.xml")){assertNotNull(stream);String xml=new String(readAll(stream),StandardCharsets.UTF_8);assertFalse(xml.contains("CASE WHEN coverage.gap_count&gt;0 THEN NULL ELSE SUM(CASE WHEN event_type=3"));assertFalse(xml.contains("CASE WHEN coverage.gap_count&gt;0 THEN NULL ELSE COUNT(DISTINCT CASE WHEN event_type=3"));}}
 @Test void recompute_physicallyReplacesOneTenantDayAndPreservesUnknownProfit(){
  DashboardAggregationMapper aggregate=mock(DashboardAggregationMapper.class);TrafficDailyMapper traffic=mock(TrafficDailyMapper.class);ProductStatisticsMapper product=mock(ProductStatisticsMapper.class);
  LocalDate day=LocalDate.of(2026,7,12);TrafficDailyDO site=new TrafficDailyDO().setDay(day).setPaidRevenue(1000L).setRefundAmount(0L).setKnownCostAmount(500L).setMissingCostItemCount(1L);
  ProductStatisticsDO row=new ProductStatisticsDO().setTime(day).setSpuId(10L).setOrderPayPrice(1000L).setAfterSaleRefundPrice(0L).setKnownCostAmount(500L).setMissingCostItemCount(1L);
  when(aggregate.countHashVersions(121L,day)).thenReturn(1);when(aggregate.selectTrafficDaily(eq(121L),eq(day),any(),any())).thenReturn(site);when(aggregate.selectProductDaily(eq(121L),eq(day),any(),any())).thenReturn(Collections.singletonList(row));
  DashboardAggregationServiceImpl service=new DashboardAggregationServiceImpl();ReflectionTestUtils.setField(service,"aggregationMapper",aggregate);ReflectionTestUtils.setField(service,"trafficMapper",traffic);ReflectionTestUtils.setField(service,"productMapper",product);ReflectionTestUtils.setField(service,"calculator",new DashboardMetricCalculator());
  service.recomputeDay(121L,day);
  verify(traffic).physicalDeleteByTenantAndDay(121L,day);verify(product).physicalDeleteByTenantAndDay(121L,day);verify(traffic).insert(site);verify(product).insertBatch(anyCollection());
  assertNull(site.getCostAmount());assertNull(site.getGrossProfit());assertEquals(4,site.getProfitDataQuality());assertNull(row.getGrossProfit());
  assertNotNull(row.getTrafficWatermark());assertEquals(row.getTrafficWatermark(),row.getTradeWatermark());assertEquals(row.getTrafficWatermark(),row.getRefundWatermark());
 }
 @Test void multipleHashVersions_abortBeforeDelete(){DashboardAggregationMapper aggregate=mock(DashboardAggregationMapper.class);TrafficDailyMapper traffic=mock(TrafficDailyMapper.class);ProductStatisticsMapper product=mock(ProductStatisticsMapper.class);LocalDate day=LocalDate.now();when(aggregate.countHashVersions(121L,day)).thenReturn(2);DashboardAggregationServiceImpl service=new DashboardAggregationServiceImpl();ReflectionTestUtils.setField(service,"aggregationMapper",aggregate);ReflectionTestUtils.setField(service,"trafficMapper",traffic);ReflectionTestUtils.setField(service,"productMapper",product);ReflectionTestUtils.setField(service,"calculator",new DashboardMetricCalculator());assertThrows(IllegalStateException.class,()->service.recomputeDay(121L,day));verifyNoInteractions(traffic,product);}
 private byte[] readAll(java.io.InputStream stream) throws java.io.IOException {java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];for(int length;(length=stream.read(buffer))!=-1;)output.write(buffer,0,length);return output.toByteArray();}
}
