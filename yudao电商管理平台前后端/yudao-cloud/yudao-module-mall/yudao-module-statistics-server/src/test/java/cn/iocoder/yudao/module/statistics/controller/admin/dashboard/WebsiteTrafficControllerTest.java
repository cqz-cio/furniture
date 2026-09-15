package cn.iocoder.yudao.module.statistics.controller.admin.dashboard;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo.*;
import cn.iocoder.yudao.module.statistics.service.dashboard.DashboardQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebsiteTrafficControllerTest {
    @Test void returnsOnlyTrafficAndIgnoresProductFinancialParameters() {
        DashboardQueryService service = mock(DashboardQueryService.class);
        WebsiteTrafficController controller = new WebsiteTrafficController();
        ReflectionTestUtils.setField(controller, "service", service);
        ReflectionTestUtils.setField(controller, "website", mock(cn.iocoder.yudao.module.statistics.service.dashboard.WebsiteTrafficService.class));
        when(service.summary(any(), eq(false))).thenReturn(new DashboardSummaryRespVO()
                .setHomePv(40L).setHomeUv(12L).setPaidRevenue(999L).setGrossProfit(888L)
                .setCostAmount(777L).setPaidOrderCount(9L).setTrafficDataStatus("PARTIAL"));
        DashboardQueryReqVO request = new DashboardQueryReqVO().setScope("PRODUCT")
                .setSpuId(55L).setCompare(true).setStartDate(LocalDate.of(2026, 9, 1));
        WebsiteTrafficRespVO result = controller.summary(request).getData();
        assertEquals(40L, result.getHomePv());
        assertEquals(12L, result.getHomeUv());
        String json = JsonUtils.toJsonString(result);
        for (String denied : List.of("paidRevenue", "grossProfit", "costAmount", "paidOrderCount", "reference")) {
            assertFalse(json.contains(denied));
        }
        ArgumentCaptor<DashboardQueryReqVO> query = ArgumentCaptor.forClass(DashboardQueryReqVO.class);
        verify(service).summary(query.capture(), eq(false));
        assertEquals("SITE", query.getValue().getScope());
        assertNull(query.getValue().getSpuId());
        assertFalse(query.getValue().getCompare());
        assertEquals(request.getStartDate(), query.getValue().getStartDate());
    }

    @Test void trendAlsoUsesTrafficAllowListAndDedicatedPermission() throws Exception {
        DashboardQueryService service = mock(DashboardQueryService.class);
        WebsiteTrafficController controller = new WebsiteTrafficController();
        ReflectionTestUtils.setField(controller, "service", service);
        ReflectionTestUtils.setField(controller, "website", mock(cn.iocoder.yudao.module.statistics.service.dashboard.WebsiteTrafficService.class));
        DashboardTrendItemRespVO row = new DashboardTrendItemRespVO();
        row.setDay(LocalDate.of(2026, 9, 1));
        row.setHomePv(3L);
        row.setPaidRevenue(999L);
        when(service.trend(any(), eq(false))).thenReturn(List.of(row));
        var rows = controller.trend(new DashboardQueryReqVO()).getData();
        assertEquals(row.getDay(), rows.get(0).getDay());
        assertFalse(JsonUtils.toJsonString(rows).contains("paidRevenue"));
        for (String method : List.of("summary", "trend")) {
            assertEquals("@ss.hasPermission('statistics:website:query')", WebsiteTrafficController.class
                    .getMethod(method, DashboardQueryReqVO.class).getAnnotation(PreAuthorize.class).value());
        }
    }
}
