package cn.iocoder.yudao.module.product.service.category;

import cn.iocoder.yudao.module.product.controller.admin.category.vo.ProductCategoryListReqVO;
import cn.iocoder.yudao.module.product.controller.admin.category.vo.ProductCategorySaveReqVO;
import cn.iocoder.yudao.module.product.controller.admin.category.vo.ProductNavigationCategoryCreateReqVO;
import cn.iocoder.yudao.module.product.api.category.dto.ProductCategoryNavigationRespDTO;
import cn.iocoder.yudao.module.product.dal.dataobject.category.ProductCategoryDO;
import jakarta.validation.Valid;

import java.util.Collection;
import java.util.List;

/**
 * 商品分类 Service 接口
 *
 * @author 芋道源码
 */
public interface ProductCategoryService {

    /**
     * 创建商品分类
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createCategory(@Valid ProductCategorySaveReqVO createReqVO);

    /**
     * 从官网导航快速创建商品分类
     *
     * @param createReqVO 导航快速创建信息
     * @return 编号
     */
    Long createNavigationCategory(@Valid ProductNavigationCategoryCreateReqVO createReqVO);

    /**
     * 更新商品分类
     *
     * @param updateReqVO 更新信息
     */
    void updateCategory(@Valid ProductCategorySaveReqVO updateReqVO);

    /**
     * 删除商品分类
     *
     * @param id 编号
     */
    void deleteCategory(Long id);

    /**
     * 获得商品分类
     *
     * @param id 编号
     * @return 商品分类
     */
    ProductCategoryDO getCategory(Long id);

    /**
     * 校验商品分类
     *
     * @param id 分类编号
     */
    void validateCategory(Long id);

    /**
     * Validate that a writable B2B Product type is an enabled P2 directly below the selected P1 Room.
     */
    void validateProductTypeSelection(Long categoryId, Long roomCategoryId);

    /**
     * 获得商品分类的层级
     *
     * @param id 编号
     * @return 商品分类的层级
     */
    Integer getCategoryLevel(Long id);

    /**
     * 获得商品分类列表
     *
     * @param listReqVO 查询条件
     * @return 商品分类列表
     */
    List<ProductCategoryDO> getCategoryList(ProductCategoryListReqVO listReqVO);

    /**
     * 获得开启状态的商品分类列表
     *
     * @return 商品分类列表
     */
    List<ProductCategoryDO> getEnableCategoryList();

    /**
     * 获得开启状态的商品分类列表，指定编号
     *
     * @param ids 商品分类编号数组
     * @return 商品分类列表
     */
    List<ProductCategoryDO> getEnableCategoryList(List<Long> ids);

    /**
     * 校验商品分类是否有效。如下情况，视为无效：
     * 1. 商品分类编号不存在
     * 2. 商品分类被禁用
     * 3. 商品分类层级校验，必须使用第二级的商品分类及以下
     *
     * @param ids 商品分类编号数组
     */
    void validateCategoryList(Collection<Long> ids);

    /**
     * Get enabled second-level categories for the website navigation picker.
     */
    List<ProductCategoryNavigationRespDTO> getNavigationCategoryList();

    /**
     * 从官网导航同步修改二级商品分类名称
     *
     * @param id 分类编号
     * @param name 新分类名称
     */
    void updateNavigationCategoryName(Long id, String name);

}
