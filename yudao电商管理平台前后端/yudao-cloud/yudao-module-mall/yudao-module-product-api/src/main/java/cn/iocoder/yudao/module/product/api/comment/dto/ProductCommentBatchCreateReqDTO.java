package cn.iocoder.yudao.module.product.api.comment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Schema(description = "RPC 服务 - 商品评论批量创建 Request DTO")
@Data
public class ProductCommentBatchCreateReqDTO {

    @Schema(description = "评论列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "评论列表不能为空")
    @Valid
    private List<ProductCommentCreateReqDTO> comments;

}
