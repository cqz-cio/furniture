package cn.iocoder.yudao.module.product.controller.admin.category.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 官网导航快速创建商品分类 Request VO")
@Data
public class ProductNavigationCategoryCreateReqVO {

    @Schema(description = "父分类编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "父分类编号不能为空")
    @Positive(message = "请选择有效的上级分类")
    private Long parentId;

    @Schema(description = "稳定分类编码", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "contract-furniture")
    @NotBlank(message = "分类编码不能为空")
    @Size(max = 64, message = "分类编码不能超过 64 个字符")
    @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
            message = "分类编码只能包含小写字母、数字和连字符")
    private String code;

    @Schema(description = "分类名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "Contract Furniture")
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 64, message = "分类名称不能超过 64 个字符")
    private String name;

}
