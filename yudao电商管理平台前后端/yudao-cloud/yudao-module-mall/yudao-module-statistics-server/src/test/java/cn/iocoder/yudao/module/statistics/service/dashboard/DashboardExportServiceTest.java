package cn.iocoder.yudao.module.statistics.service.dashboard;

import cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo.DashboardProductExcelVO;
import cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo.DashboardProductRespVO;
import cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo.DashboardQueryReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardExportServiceTest {

    @Test
    void normalExportNeverContainsProfitFields() {
        DashboardQueryService query = mock(DashboardQueryService.class);
        when(query.products(any(), eq(false))).thenReturn(Collections.singletonList(new DashboardProductRespVO()
                .setSpuId(1L).setKnownCostAmount(99L).setGrossProfit(20L)));
        List<DashboardProductExcelVO> rows = service(query).build(new DashboardQueryReqVO().setScope("PRODUCT"), false);
        assertEquals(1, rows.size());
        assertNull(rows.get(0).getKnownCostAmount());
        assertNull(rows.get(0).getGrossProfit());
        verify(query).products(any(), eq(false));
    }

    @Test
    void formulaPrefixesAreEscaped() {
        for (String value : new String[]{"=cmd", "+cmd", "-cmd", "@cmd", "\tcmd", "\rcmd"}) {
            assertEquals("'" + value, DashboardExportServiceImpl.escapeFormula(value));
        }
        assertEquals("chair", DashboardExportServiceImpl.escapeFormula("chair"));
    }

    @Test
    void exportRejectsMoreThanTenThousandRows() {
        DashboardQueryService query = mock(DashboardQueryService.class);
        List<DashboardProductRespVO> rows = new ArrayList<>();
        for (int i = 0; i < 10001; i++) rows.add(new DashboardProductRespVO().setSpuId((long) i));
        when(query.products(any(), anyBoolean())).thenReturn(rows);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service(query).build(new DashboardQueryReqVO().setScope("PRODUCT"), true));
        assertTrue(error.getMessage().contains("10,000"));
    }

    @Test
    void generatedArtifactContainsAllRowsAndStableSha() {
        DashboardQueryService query = mock(DashboardQueryService.class);
        when(query.products(any(), eq(false))).thenReturn(Collections.singletonList(new DashboardProductRespVO()
                .setSpuId(1L).setCategoryId(8L).setProductName("=dangerous chair").setBrowseCount(12L)));
        DashboardExportArtifact artifact = service(query).generate(
                new DashboardQueryReqVO().setScope("PRODUCT").setPageNo(9).setPageSize(1), false);
        assertEquals(1, artifact.getRowCount());
        assertTrue(artifact.getContent().length > 0);
        assertEquals(64, artifact.getFileSha256().length());
        assertEquals("'=dangerous chair", artifact.getRows().get(0).getProductName());
    }

    private DashboardExportServiceImpl service(DashboardQueryService query) {
        DashboardExportServiceImpl service = new DashboardExportServiceImpl();
        ReflectionTestUtils.setField(service, "queryService", query);
        return service;
    }
}
