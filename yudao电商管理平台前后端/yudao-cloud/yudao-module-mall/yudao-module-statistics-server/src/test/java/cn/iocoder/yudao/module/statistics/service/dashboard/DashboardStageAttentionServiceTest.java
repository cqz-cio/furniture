package cn.iocoder.yudao.module.statistics.service.dashboard;

import cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo.DashboardAttentionRespVO;
import cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo.DashboardQueryReqVO;
import cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo.DashboardStageOverviewRespVO;
import cn.iocoder.yudao.module.statistics.dal.dataobject.dashboard.TrafficDailyDO;
import cn.iocoder.yudao.module.statistics.dal.dataobject.product.ProductStatisticsDO;
import cn.iocoder.yudao.module.statistics.dal.mysql.dashboard.TrafficDailyMapper;
import cn.iocoder.yudao.module.statistics.dal.mysql.product.ProductStatisticsMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DashboardStageAttentionServiceTest {

    @Test
    void stageOverview_exposesScopeSpecificStagesWithoutInventingConversions() {
        TrafficDailyMapper traffic = mock(TrafficDailyMapper.class);
        when(traffic.selectBetween(any(), any())).thenReturn(Collections.singletonList(new TrafficDailyDO()
                .setHomeUv(80L).setProductDetailUv(50L).setAddCartUserCount(20L)
                .setCheckoutStartCount(12L).setPaidBuyerCount(8L).setTrafficDataStatus(1)));
        DashboardStageOverviewRespVO site = service(traffic, mock(ProductStatisticsMapper.class))
                .stageOverview(new DashboardQueryReqVO().setScope("SITE"));
        assertFalse(site.getCohortAligned());
        assertEquals(5, site.getItems().size());
        assertEquals(Long.valueOf(80), site.getItems().get(0).getValue());

        ProductStatisticsMapper products = mock(ProductStatisticsMapper.class);
        when(products.selectBetween(any(), any())).thenReturn(Collections.singletonList(new ProductStatisticsDO()
                .setBrowseUserCount(30).setCartCount(9).setOrderCount(4)));
        DashboardStageOverviewRespVO product = service(mock(TrafficDailyMapper.class), products)
                .stageOverview(new DashboardQueryReqVO().setScope("PRODUCT"));
        assertEquals("NOT_APPLICABLE", product.getItems().get(0).getApplicability());
        assertNull(product.getItems().get(0).getValue());
        assertEquals("APPLICABLE", product.getItems().get(1).getApplicability());
    }

    @Test
    void attention_matchesAllRulesAndReportsSkippedRules() {
        ProductStatisticsMapper products = mock(ProductStatisticsMapper.class);
        when(products.selectBetween(any(), any())).thenReturn(Arrays.asList(
                product(1L, 200, 1, 20, 200000, 30000, -1L, 0L, 1, 1),
                product(2L, 200, 20, 4, 50000, 0, null, 2L, 1, 4),
                product(3L, 200, 20, 4, 50000, 0, 10000L, 0L, 2, 1)));
        DashboardAttentionRespVO result = service(mock(TrafficDailyMapper.class), products)
                .attention(new DashboardQueryReqVO().setScope("PRODUCT"), true);
        assertTrue(result.getItems().stream().anyMatch(i -> "HIGH_TRAFFIC_LOW_CONVERSION".equals(i.getRiskType())));
        assertTrue(result.getItems().stream().anyMatch(i -> "HIGH_REFUND".equals(i.getRiskType())));
        assertTrue(result.getItems().stream().anyMatch(i -> "LOW_OR_NEGATIVE_MARGIN".equals(i.getRiskType())));
        assertTrue(result.getItems().stream().anyMatch(i -> "MISSING_COST".equals(i.getRiskType())));
        assertTrue(result.getNotEvaluated().stream().anyMatch(i -> "TRAFFIC_INCOMPLETE".equals(i.getReasonCode())));
        assertTrue(result.getNotEvaluated().stream().anyMatch(i -> "PROFIT_INCOMPLETE".equals(i.getReasonCode())));
        assertEquals("rule hint, not an automatic diagnosis", result.getDisclaimer());
    }

    @Test
    void attention_withoutProfitPermissionSuppressesCostAndMarginRules() {
        ProductStatisticsMapper products = mock(ProductStatisticsMapper.class);
        when(products.selectBetween(any(), any())).thenReturn(Collections.singletonList(
                product(1L, 200, 1, 20, 200000, 30000, -1L, 2L, 1, 4)));
        DashboardAttentionRespVO result = service(mock(TrafficDailyMapper.class), products)
                .attention(new DashboardQueryReqVO().setScope("SITE"), false);
        assertTrue(result.getItems().stream().noneMatch(i -> i.getRiskType().contains("MARGIN") || i.getRiskType().contains("COST")));
        assertTrue(result.getNotEvaluated().stream().noneMatch(i -> i.getRiskType().contains("MARGIN") || i.getRiskType().contains("COST")));
    }

    private ProductStatisticsDO product(Long spuId, int pv, int orders, int paidOrders, int revenue,
                                        int refund, Long grossProfit, Long missingCost, int traffic, int profit) {
        return new ProductStatisticsDO().setSpuId(spuId).setBrowseCount(pv).setOrderCount(orders)
                .setOrderPayCount(paidOrders).setOrderPayPrice(revenue).setAfterSaleRefundPrice(refund)
                .setGrossProfit(grossProfit).setGrossMarginPercent(grossProfit == null ? null : java.math.BigDecimal.valueOf(5))
                .setMissingCostItemCount(missingCost).setTrafficDataStatus(traffic).setProfitDataQuality(profit);
    }

    private DashboardQueryServiceImpl service(TrafficDailyMapper traffic, ProductStatisticsMapper products) {
        DashboardQueryServiceImpl service = new DashboardQueryServiceImpl();
        ReflectionTestUtils.setField(service, "trafficMapper", traffic);
        ReflectionTestUtils.setField(service, "productMapper", products);
        return service;
    }
}
