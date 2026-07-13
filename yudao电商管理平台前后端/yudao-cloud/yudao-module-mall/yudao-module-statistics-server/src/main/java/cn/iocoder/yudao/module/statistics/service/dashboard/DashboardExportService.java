package cn.iocoder.yudao.module.statistics.service.dashboard;

import cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo.DashboardProductExcelVO;
import cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo.DashboardQueryReqVO;

import java.util.List;

public interface DashboardExportService {
    List<DashboardProductExcelVO> build(DashboardQueryReqVO request, boolean includeProfit);
}
