package cn.iocoder.yudao.module.member.api.giftregistry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "RPC 服务 - Gift Registry 购买回写 Request DTO")
@Data
public class MemberGiftRegistryPurchaseRecordReqDTO {

    @Schema(description = "购买的 Gift Registry 商品列表")
    @Valid
    @NotEmpty(message = "Gift Registry 购买商品不能为空")
    private List<Item> items;

    @Schema(description = "Gift Registry 购买商品")
    @Data
    public static class Item {

        @Schema(description = "Gift Registry 编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
        @NotNull(message = "Gift Registry 编号不能为空")
        private Long registryId;

        @Schema(description = "Gift Registry 商品编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2001")
        @NotNull(message = "Gift Registry 商品编号不能为空")
        private Long registryItemId;

        @Schema(description = "购买数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        @NotNull(message = "购买数量不能为空")
        @Min(value = 1, message = "购买数量必须大于等于 1")
        private Integer count;

        @Schema(description = "订单编号", example = "3001")
        private Long orderId;

        @Schema(description = "订单商品编号", example = "4001")
        private Long orderItemId;

    }

}
