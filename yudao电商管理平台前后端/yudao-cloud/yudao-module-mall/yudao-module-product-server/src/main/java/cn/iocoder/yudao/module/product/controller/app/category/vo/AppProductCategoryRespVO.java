package cn.iocoder.yudao.module.product.controller.app.category.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "用户 App - 公开商品分类树 Response VO")
@Data
public class AppProductCategoryRespVO {

    private Long id;
    private Long parentId;
    private String code;
    private String name;
    private Integer sort;
    private Integer status;
    private List<AppProductCategoryRespVO> children;

}
