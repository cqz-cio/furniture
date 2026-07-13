package cn.iocoder.yudao.module.statistics.controller.admin.dashboard;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.http.HttpUtils;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo.*;
import cn.iocoder.yudao.module.statistics.service.dashboard.DashboardExportArtifact;
import cn.iocoder.yudao.module.statistics.service.dashboard.DashboardExportAuditService;
import cn.iocoder.yudao.module.statistics.service.dashboard.DashboardExportRateLimiter;
import cn.iocoder.yudao.module.statistics.service.dashboard.DashboardExportService;
import cn.iocoder.yudao.module.statistics.service.dashboard.DashboardQueryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.util.List;

import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/statistics/dashboard")
@Validated
public class DashboardStatisticsController {

    @Resource private DashboardQueryService service;
    @Resource private DashboardExportService exportService;
    @Resource private DashboardExportRateLimiter exportRateLimiter;
    @Resource private DashboardExportAuditService exportAuditService;
    @Resource private SecurityFrameworkService security;

    @GetMapping("/summary")
    @PreAuthorize("@ss.hasPermission('statistics:dashboard:query')")
    public CommonResult<DashboardSummaryRespVO> summary(@Valid DashboardQueryReqVO request) {
        return success(service.summary(request, profit()));
    }

    @GetMapping("/trend")
    @PreAuthorize("@ss.hasPermission('statistics:dashboard:query')")
    public CommonResult<List<DashboardTrendItemRespVO>> trend(@Valid DashboardQueryReqVO request) {
        return success(service.trend(request, profit()));
    }

    @GetMapping("/product-page")
    @PreAuthorize("@ss.hasPermission('statistics:dashboard:query')")
    public CommonResult<PageResult<DashboardProductRespVO>> products(@Valid DashboardQueryReqVO request) {
        return success(service.productPage(request, profit()));
    }

    @GetMapping("/stage-overview")
    @PreAuthorize("@ss.hasPermission('statistics:dashboard:query')")
    public CommonResult<DashboardStageOverviewRespVO> stage(@Valid DashboardQueryReqVO request) {
        return success(service.stageOverview(request));
    }

    @GetMapping("/attention")
    @PreAuthorize("@ss.hasPermission('statistics:dashboard:query')")
    public CommonResult<DashboardAttentionRespVO> attention(@Valid DashboardQueryReqVO request) {
        return success(service.attention(request, profit()));
    }

    @GetMapping("/export")
    @PreAuthorize("@ss.hasPermission('statistics:dashboard:export')")
    @ApiAccessLog(operateModule = "数据看板", operateName = "导出经营数据", operateType = EXPORT)
    public void export(@Valid DashboardQueryReqVO request, HttpServletResponse response) throws IOException {
        writeExport(request, response, false, "数据看板-商品经营.xlsx");
    }

    @GetMapping("/profit-export")
    @PreAuthorize("@ss.hasPermission('statistics:dashboard:profit-query') and @ss.hasPermission('statistics:dashboard:profit-export')")
    @ApiAccessLog(operateModule = "数据看板", operateName = "导出利润数据", operateType = EXPORT)
    public void profitExport(@Valid DashboardQueryReqVO request, HttpServletResponse response) throws IOException {
        writeExport(request, response, true, "数据看板-利润.xlsx");
    }

    private void writeExport(DashboardQueryReqVO request, HttpServletResponse response,
                             boolean includeProfit, String filename) throws IOException {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        try {
            exportRateLimiter.acquire(tenantId, userId);
            DashboardExportArtifact artifact = exportService.generate(request, includeProfit);
            exportAuditService.recordSuccess(tenantId, userId, request, includeProfit,
                    artifact.getRowCount(), artifact.getFileSha256());
            response.addHeader("Content-Disposition", "attachment;filename=" + HttpUtils.encodeUtf8(filename));
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.getOutputStream().write(artifact.getContent());
        } catch (RuntimeException exception) {
            auditFailure(tenantId, userId, request, includeProfit, exception);
            throw exception;
        } catch (IOException exception) {
            auditFailure(tenantId, userId, request, includeProfit, exception);
            throw exception;
        }
    }

    private void auditFailure(Long tenantId, Long userId, DashboardQueryReqVO request,
                              boolean includeProfit, Exception exception) {
        String failureCode = exception.getClass().getSimpleName()
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase();
        try {
            exportAuditService.recordFailure(tenantId, userId, request, includeProfit, failureCode);
        } catch (RuntimeException auditException) {
            exception.addSuppressed(auditException);
        }
    }

    private boolean profit() {
        return security.hasPermission("statistics:dashboard:profit-query");
    }
}
