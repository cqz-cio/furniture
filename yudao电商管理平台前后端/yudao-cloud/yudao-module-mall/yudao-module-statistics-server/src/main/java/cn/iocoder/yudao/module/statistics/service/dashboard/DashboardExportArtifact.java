package cn.iocoder.yudao.module.statistics.service.dashboard;

import cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo.DashboardProductExcelVO;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class DashboardExportArtifact {
    private final byte[] content;
    private final String fileSha256;
    private final int rowCount;
    private final List<DashboardProductExcelVO> rows;
}
