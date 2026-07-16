package cn.iocoder.yudao.module.trade.controller.admin.fulfillment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 物流轨迹应用结果 Response VO")
@Data
public class TrackingApplyRespVO {

    private Boolean inserted;
    private Boolean stateChanged;
    private String previousStatus;
    private String currentStatus;
}
