package cn.iocoder.yudao.module.crm.controller.admin.clue.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 询盘汇总 Response VO")
@Data
public class CrmInquirySummaryRespVO {

    private Long totalCount;
    private Long pendingCount;
    private Long processingCount;
    private Long processedCount;
    private Long invalidCount;
    private Long overdueCount;
    private Long testDataCount;

}
