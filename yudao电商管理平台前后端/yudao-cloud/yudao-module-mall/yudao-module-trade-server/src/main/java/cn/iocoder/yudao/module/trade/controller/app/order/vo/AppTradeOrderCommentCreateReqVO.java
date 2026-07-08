package cn.iocoder.yudao.module.trade.controller.app.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

@Schema(description = "用户 App - 整单评价创建 Request VO")
@Data
public class AppTradeOrderCommentCreateReqVO {

    @Schema(description = "订单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "订单编号不能为空")
    private Long orderId;

    @Schema(description = "是否匿名", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    @NotNull(message = "匿名标记不能为空")
    private Boolean anonymous;

    @Schema(description = "评价商品列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "评价商品列表不能为空")
    @Valid
    private List<Item> items;

    @Schema(description = "评价商品项")
    @Data
    public static class Item {

        @Schema(description = "订单项编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2312312")
        @NotNull(message = "订单项编号不能为空")
        private Long orderItemId;

        @Schema(description = "描述星级 1-5 分", requiredMode = Schema.RequiredMode.REQUIRED, example = "5")
        @NotNull(message = "描述星级不能为空")
        @Min(value = 1, message = "描述星级不能小于 1 分")
        @Max(value = 5, message = "描述星级不能大于 5 分")
        private Integer descriptionScores;

        @Schema(description = "服务星级 1-5 分", requiredMode = Schema.RequiredMode.REQUIRED, example = "5")
        @NotNull(message = "服务星级不能为空")
        @Min(value = 1, message = "服务星级不能小于 1 分")
        @Max(value = 5, message = "服务星级不能大于 5 分")
        private Integer benefitScores;

        @Schema(description = "评论内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "整体满意")
        @NotBlank(message = "评论内容不能为空白")
        private String content;

        @Schema(description = "评论图片地址数组，最多上传 9 张", example = "[\"https://www.iocoder.cn/xx.png\"]")
        @Size(max = 9, message = "评论图片地址数组长度不能超过 9 张")
        private List<String> picUrls;
    }

}
