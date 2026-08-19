package cn.iocoder.yudao.module.product.controller.app.category;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.product.controller.app.category.vo.AppProductCategoryRespVO;
import cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO;
import cn.iocoder.yudao.module.product.service.category.ProductCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO.PARENT_ID_NULL;

@Tag(name = "用户 APP - 商品分类")
@RestController
@RequestMapping("/product/category")
public class AppProductCategoryController {

    private static final Comparator<ProductCategoryDO> CATEGORY_ORDER =
            Comparator.comparing(ProductCategoryDO::getSort,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(ProductCategoryDO::getId);

    @Resource
    private ProductCategoryService categoryService;

    @GetMapping("/tree")
    @Operation(summary = "获得带稳定编码的公开商品分类树")
    @PermitAll
    public CommonResult<List<AppProductCategoryRespVO>> getCategoryTree() {
        List<ProductCategoryDO> categories = categoryService.getEnableCategoryList().stream()
                .sorted(CATEGORY_ORDER)
                .toList();
        Map<Long, List<ProductCategoryDO>> childrenByParent = categories.stream()
                .filter(category -> !Objects.equals(category.getParentId(), PARENT_ID_NULL))
                .collect(Collectors.groupingBy(ProductCategoryDO::getParentId));
        List<AppProductCategoryRespVO> roots = categories.stream()
                .filter(category -> Objects.equals(category.getParentId(), PARENT_ID_NULL))
                .map(category -> toTreeNode(category, childrenByParent))
                .toList();
        return success(roots);
    }

    private AppProductCategoryRespVO toTreeNode(
            ProductCategoryDO category, Map<Long, List<ProductCategoryDO>> childrenByParent) {
        AppProductCategoryRespVO node = BeanUtils.toBean(category, AppProductCategoryRespVO.class);
        node.setChildren(childrenByParent.getOrDefault(category.getId(), List.of()).stream()
                .sorted(CATEGORY_ORDER)
                .map(child -> toTreeNode(child, childrenByParent))
                .toList());
        return node;
    }

}
