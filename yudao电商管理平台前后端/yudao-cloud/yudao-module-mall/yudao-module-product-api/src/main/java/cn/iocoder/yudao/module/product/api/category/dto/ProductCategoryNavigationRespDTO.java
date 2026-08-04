package cn.iocoder.yudao.module.product.api.category.dto;

import lombok.Data;

/**
 * Website navigation product-category summary.
 *
 * <p>The category name and status always come from the product center. Website
 * navigation only stores the category id, so category renames are reflected
 * automatically without asking an operator to edit the navigation twice.</p>
 */
@Data
public class ProductCategoryNavigationRespDTO {

    private Long id;
    private Long parentId;
    private String name;
    private Integer sort;
    private Integer status;
    private Long publishedProductCount;

}
