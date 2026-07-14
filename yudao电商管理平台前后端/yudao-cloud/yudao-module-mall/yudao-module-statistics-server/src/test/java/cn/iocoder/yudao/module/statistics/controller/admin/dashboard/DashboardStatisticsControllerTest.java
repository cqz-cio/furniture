package cn.iocoder.yudao.module.statistics.controller.admin.dashboard;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo.DashboardProductExcelVO;
import cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo.DashboardQueryReqVO;
import cn.iocoder.yudao.module.statistics.service.dashboard.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DashboardStatisticsControllerTest {
    private DashboardExportService exportService;
    private DashboardExportRateLimiter rateLimiter;
    private DashboardExportAuditService auditService;
    private DashboardStatisticsController controller;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(121L);
        LoginUser user = new LoginUser();
        user.setId(110L);
        user.setTenantId(121L);
        user.setUserType(1);
        SecurityFrameworkUtils.setLoginUser(user, new MockHttpServletRequest());
        exportService = mock(DashboardExportService.class);
        rateLimiter = mock(DashboardExportRateLimiter.class);
        auditService = mock(DashboardExportAuditService.class);
        controller = new DashboardStatisticsController();
        ReflectionTestUtils.setField(controller, "exportService", exportService);
        ReflectionTestUtils.setField(controller, "exportRateLimiter", rateLimiter);
        ReflectionTestUtils.setField(controller, "exportAuditService", auditService);
    }

    @AfterEach
    void clean() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void profitExportRequiresProfitQueryAndProfitExportPermissions() throws Exception {
        PreAuthorize guard = DashboardStatisticsController.class
                .getMethod("profitExport", DashboardQueryReqVO.class, javax.servlet.http.HttpServletResponse.class)
                .getAnnotation(PreAuthorize.class);
        assertNotNull(guard);
        assertTrue(guard.value().contains("statistics:dashboard:profit-query"));
        assertTrue(guard.value().contains("statistics:dashboard:profit-export"));
    }

    @Test
    void normalExportWritesGeneratedArtifactAndCompleteAudit() throws Exception {
        DashboardQueryReqVO request = new DashboardQueryReqVO().setScope("PRODUCT");
        byte[] bytes = new byte[]{1, 2, 3};
        when(exportService.generate(request, false)).thenReturn(new DashboardExportArtifact(
                bytes, "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", 1,
                Collections.singletonList(new DashboardProductExcelVO().setSpuId(1L))));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(request, response);

        assertArrayEquals(bytes, response.getContentAsByteArray());
        verify(rateLimiter).acquire(121L, 110L);
        verify(auditService).recordSuccess(eq(121L), eq(110L), same(request), eq(false), eq(1), anyString());
        verify(auditService, never()).recordFailure(anyLong(), anyLong(), any(), anyBoolean(), anyString());
    }

    @Test
    void rejectedExportIsAuditedWithoutSensitiveErrorMessage() {
        DashboardQueryReqVO request = new DashboardQueryReqVO().setScope("PRODUCT");
        doThrow(new IllegalStateException("redis contains secret detail")).when(rateLimiter).acquire(121L, 110L);

        assertThrows(IllegalStateException.class, () -> controller.export(request, new MockHttpServletResponse()));

        verify(auditService).recordFailure(121L, 110L, request, false, "ILLEGAL_STATE_EXCEPTION");
        verifyNoInteractions(exportService);
    }
}
