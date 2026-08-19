package cn.iocoder.yudao.module.product.controller.admin.category.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 商品分类新增/更新 Request VO")
@Data
public class ProductCategorySaveReqVO {

    @Schema(description = "分类编号", example = "2")
    private Long id;

    @Schema(description = "父分类编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "父分类编号不能为空")
    private Long parentId;

    @Schema(description = "稳定分类编码", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "dining-table")
    @NotBlank(message = "分类编码不能为空")
    @Size(max = 64, message = "分类编码不能超过 64 个字符")
    @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
            message = "分类编码只能包含小写字母、数字和连字符")
    private String code;

    @Schema(description = "分类名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "办公文具")
    @NotBlank(message = "分类名称不能为空")
    private String name;

    @Schema(description = "移动端分类图", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "移动端分类图不能为空")
    private String picUrl;

    @Schema(description = "分类排序", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer sort;

    @Schema(description = "开启状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "开启状态不能为空")
    private Integer status;

    @Schema(description = "分类描述", example = "描述")
    private String description;

}
