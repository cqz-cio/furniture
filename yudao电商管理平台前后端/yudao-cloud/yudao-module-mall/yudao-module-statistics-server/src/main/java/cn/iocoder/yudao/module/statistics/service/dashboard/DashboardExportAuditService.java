package cn.iocoder.yudao.module.statistics.service.dashboard;

import cn.hutool.crypto.SecureUtil;
import cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo.DashboardQueryReqVO;
import cn.iocoder.yudao.module.statistics.dal.dataobject.dashboard.DashboardExportAuditDO;
import cn.iocoder.yudao.module.statistics.dal.mysql.dashboard.DashboardExportAuditMapper;
import org.springframework.stereotype.Service;

@Service
public class DashboardExportAuditService {
    private final DashboardExportAuditMapper mapper;

    public DashboardExportAuditService(DashboardExportAuditMapper mapper) {
        this.mapper = mapper;
    }

    public void recordSuccess(Long tenantId, Long userId, DashboardQueryReqVO request,
                              boolean includeProfit, int rowCount, String fileSha256) {
        insert(tenantId, userId, request, includeProfit, rowCount, fileSha256, "SUCCESS", null);
    }

    public void recordFailure(Long tenantId, Long userId, DashboardQueryReqVO request,
                              boolean includeProfit, String failureCode) {
        insert(tenantId, userId, request, includeProfit, 0, null, "FAILURE", safeFailureCode(failureCode));
    }

    private void insert(Long tenantId, Long userId, DashboardQueryReqVO request, boolean includeProfit,
                        int rowCount, String fileSha256, String result, String failureCode) {
        DashboardExportAuditDO audit = new DashboardExportAuditDO();
        audit.setTenantId(tenantId);
        audit.setUserId(userId);
        audit.setExportType(includeProfit ? "PROFIT" : "NORMAL");
        audit.setFilterHash(filterHash(request));
        audit.setRowCount(rowCount);
        audit.setFileSha256(fileSha256);
        audit.setResult(result);
        audit.setFailureCode(failureCode);
        mapper.insert(audit);
    }

    private String filterHash(DashboardQueryReqVO request) {
        String canonical = value(request.getScope()) + '|' + value(request.getStartDate()) + '|'
                + value(request.getEndDate()) + '|' + value(request.getCategoryId()) + '|'
                + value(request.getSpuId()) + '|' + value(request.getRiskType()) + '|'
                + value(request.getSortField()) + '|' + value(request.getSortOrder());
        return SecureUtil.sha256(canonical);
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String safeFailureCode(String code) {
        if (code == null || code.trim().isEmpty()) return "UNKNOWN";
        String normalized = code.replaceAll("[^A-Z0-9_]", "_");
        return normalized.substring(0, Math.min(normalized.length(), 64));
    }
}
