package cn.iocoder.yudao.module.product.api.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 官网导航同步商品分类名称 Request DTO
 */
@Data
public class ProductCategoryNavigationNameUpdateReqDTO {

    @NotNull(message = "商品分类编号不能为空")
    private Long id;

    @NotBlank(message = "商品分类名称不能为空")
    @Size(max = 64, message = "商品分类名称不能超过 64 个字符")
    private String name;

}
