package cn.iocoder.yudao.module.trade.controller.app.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "用户 App - 整单评价创建 Response VO")
@Data
public class AppTradeOrderCommentCreateRespVO {

    @Schema(description = "交易订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long orderId;

    @Schema(description = "本次成功创建的评论数", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    private Integer commentedItemCount;

    @Schema(description = "评论编号列表", requiredMode = Schema.RequiredMode.REQUIRED, example = "[9001,9002,9003]")
    private List<Long> commentIds;

}
