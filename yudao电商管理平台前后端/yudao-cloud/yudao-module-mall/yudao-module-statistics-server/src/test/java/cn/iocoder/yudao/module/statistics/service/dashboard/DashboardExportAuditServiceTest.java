package cn.iocoder.yudao.module.statistics.service.dashboard;

import cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo.DashboardQueryReqVO;
import cn.iocoder.yudao.module.statistics.dal.dataobject.dashboard.DashboardExportAuditDO;
import cn.iocoder.yudao.module.statistics.dal.mysql.dashboard.DashboardExportAuditMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardExportAuditServiceTest {

    @Test
    void recordsTenantUserNormalizedFilterRowsFileHashAndResult() {
        DashboardExportAuditMapper mapper = mock(DashboardExportAuditMapper.class);
        DashboardExportAuditService service = new DashboardExportAuditService(mapper);
        DashboardQueryReqVO first = request().setPageNo(1).setPageSize(10);
        DashboardQueryReqVO second = request().setPageNo(99).setPageSize(100);

        String fileHash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        service.recordSuccess(121L, 110L, first, true, 7, fileHash);
        service.recordFailure(121L, 110L, second, true, "RATE_LIMITED");

        ArgumentCaptor<DashboardExportAuditDO> captor = ArgumentCaptor.forClass(DashboardExportAuditDO.class);
        verify(mapper, times(2)).insert(captor.capture());
        DashboardExportAuditDO success = captor.getAllValues().get(0);
        DashboardExportAuditDO failure = captor.getAllValues().get(1);
        assertEquals(121L, success.getTenantId());
        assertEquals(110L, success.getUserId());
        assertEquals("PROFIT", success.getExportType());
        assertEquals(7, success.getRowCount());
        assertEquals(fileHash, success.getFileSha256());
        assertEquals("SUCCESS", success.getResult());
        assertNull(success.getFailureCode());
        assertEquals("FAILURE", failure.getResult());
        assertEquals("RATE_LIMITED", failure.getFailureCode());
        assertEquals(success.getFilterHash(), failure.getFilterHash(), "pagination must not change export filter audit hash");
        assertEquals(64, success.getFilterHash().length());
    }

    private DashboardQueryReqVO request() {
        return new DashboardQueryReqVO().setScope("PRODUCT")
                .setStartDate(LocalDate.of(2026, 7, 1)).setEndDate(LocalDate.of(2026, 7, 12))
                .setCategoryId(8L).setRiskType("HIGH_REFUND")
                .setSortField("paidRevenue").setSortOrder("desc");
    }
}
